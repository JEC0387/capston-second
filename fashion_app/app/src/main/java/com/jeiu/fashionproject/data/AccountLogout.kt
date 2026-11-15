package com.jeiu.fashionproject.data

import android.content.Context
import com.jeiu.fashionproject.ui.viewmodel.UserPreferencesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 구글 · 카카오 로그아웃 — 계정마다 기록을 따로 둔다.
 *
 *  1) 폰의 기록을 전부 서버(내 계정)에 올리고, 서버 스냅샷에 옷장 · 착용 기록 · 취향 기록이 다 들어갔는지 확인한다.
 *     하나라도 확인이 안 되면 아무것도 지우지 않고 멈춘다 (인터넷 연결 후 다시 시도).
 *  2) 이 기기를 계정에서 떼어 내고(DataAuth.detachForLogout), 이 기기의 계정 기록(옷장 · 착용 · 선호 스타일 · 성별 ·
 *     관심 상품 · 취향 · 스타일 점수 · 저장 코디 · 일정 · 추천 기록)을 비운다. 원래 값은 RestoreJournal 안전 사본에 남는다.
 *  3) 앱을 다시 시작하면 로그인 화면. 같은 계정으로 로그인하면 서버에서 그 계정 기록을 받아 온다(BackupRestore).
 * 체형 치수 · 3D 체형 · 체형 분류는 원래 계정별로 이 기기에 따로 저장돼 있어(BodyMeasurementScope) 지우지 않고,
 * 같은 계정으로 다시 로그인하면 그대로 보인다. 다른 계정으로 로그인하면 보이지 않는다.
 */
object AccountLogout {
    sealed class Result {
        object Done : Result()
        data class Blocked(val message: String) : Result()
    }

    private const val PREFS = "account_logout"
    private const val KEY_CLEARED = "cleared_after_logout"

    /** 로그아웃으로 이 기기 기록을 비운 뒤 아직 다시 로그인하지 않았는지 — 기록 없는 계정 로그인도 그대로 받아들인다 */
    fun wasClearedAfterLogout(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_CLEARED, false)

    fun clearMark(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_CLEARED).commit()
    }

    suspend fun logout(context: Context): Result {
        val app = context.applicationContext
        if (AuthStore.mode.value != AuthStore.Mode.SIGNED_IN) return Result.Blocked("로그인한 계정이 없어요.")
        if (!DbSync.isEnabled) return Result.Blocked("서버 백업이 꺼져 있어 로그아웃하면 기록을 다시 불러올 수 없어요. 백업을 켠 뒤 다시 시도해 주세요.")
        if (DataAuth.isRestorePending || RestoreJournal.hasPending(app))
            return Result.Blocked("계정 기록을 받는 중이에요. 잠시 후 다시 시도해 주세요.")

        // 1) 전부 올리고 확인
        DbSync.pushAll(app)
        val synced = DbSync.flushNow() && DbSync.flushNow()
        if (!synced) return Result.Blocked("아직 서버에 올리지 못한 기록이 있어요. 인터넷 연결을 확인하고 다시 눌러 주세요.")
        if (!serverHasLocalRecords(app)) return Result.Blocked("서버에 기록이 모두 저장됐는지 확인하지 못했어요. 잠시 후 다시 시도해 주세요.")

        // 2) 계정에서 떼어 내고 이 기기의 계정 기록 비우기
        return DbSync.exclusiveAccountChange {
            if (DbSync.pendingCount.value > 0) return@exclusiveAccountChange Result.Blocked("새로 생긴 기록이 있어요. 다시 눌러 주세요.")
            val rollback = DataAuth.detachForLogout()
                ?: return@exclusiveAccountChange Result.Blocked("로그아웃하지 못했어요. 다시 시도해 주세요.")
            val cleared = runCatching {
                withContext(Dispatchers.Main.immediate) {
                    RestoreJournal.stage(app) { staging -> clearAccountStores(staging) }
                    RestoreJournal.finish(app)
                }
            }.isSuccess
            if (!cleared) {
                rollback()
                return@exclusiveAccountChange Result.Blocked("이 기기 기록을 정리하지 못해 로그아웃을 취소했어요.")
            }
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_CLEARED, true).commit()
            AuthStore.signOut(app)
            Result.Done
        }
    }

    /** 새로 설치한 것처럼 계정 기록을 비운다 (기록 복원과 같은 저장 함수 사용) */
    internal fun clearAccountStores(staging: Context) {
        WardrobeStore.writeForRestore(staging, emptyList())
        SampleWearHistory.markApplied(staging)
        WearLogStore.writeForRestore(staging, emptyList())
        ProfileStore.writeForRestore(staging, emptyList())
        UserPreferencesViewModel.writeForRestore(staging, null)
        ProductInterestStore.writeForRestore(staging, emptyList())
        TastePreferenceStore.writeForRestore(staging, emptyList())
        StylePreferenceStore.writeForRestore(staging, emptyMap(), 0)
        SavedOutfitStore.writeForRestore(staging, emptyList())
        OutfitPlanStore.writeForRestore(staging, emptyList())
        RecommendationLog.clearForRestore(staging)
        DbSync.stageForRestore(staging, emptyMap(), emptyMap())
    }

    /** 서버 스냅샷에 이 기기의 옷 · 착용 기록 · 취향 기록이 모두 들어 있는지 */
    private suspend fun serverHasLocalRecords(app: Context): Boolean = withContext(Dispatchers.IO) {
        val body = BackendApi.getJson("/api/backup/snapshot") ?: return@withContext false
        val snapshot = runCatching { BackupRestore.parseSnapshot(JSONObject(body)) }.getOrNull() ?: return@withContext false
        WardrobeStore.load(app); WearLogStore.load(app); TastePreferenceStore.load(app)
        coversLocal(
            serverClothes = snapshot.clothes.map { it.item.id }.toSet(),
            localClothes = WardrobeStore.items.value.filter { it.isOwned }.map { it.id }.toSet(),
            serverWearLogs = snapshot.wearLogs.size, localWearLogs = WearLogStore.logs.value.size,
            serverTaste = snapshot.tasteEvents.size, localTaste = TastePreferenceStore.events.value.size
        )
    }

    internal fun coversLocal(serverClothes: Set<String>, localClothes: Set<String>,
                             serverWearLogs: Int, localWearLogs: Int, serverTaste: Int, localTaste: Int): Boolean =
        serverClothes.containsAll(localClothes) && serverWearLogs >= localWearLogs && serverTaste >= localTaste
}
