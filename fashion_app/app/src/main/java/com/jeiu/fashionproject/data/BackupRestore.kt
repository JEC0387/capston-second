package com.jeiu.fashionproject.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.jeiu.fashionproject.ui.viewmodel.UserPreferencesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 백업 코드로 다른 폰(또는 앱을 다시 설치한 폰)의 기록을 서버 DB 에서 가져온다.
 *
 * 백업 코드 = 예전 폰의 기기 번호. 서버는 기기 번호로 사용자를 구분하므로, 이 폰이 그 번호를 이어서 쓰게 된다.
 * 가져오는 것: 옷장(+ 사진 백업) · 착용 기록 · 선호 스타일 · 성별 · 관심 상품 · 취향 평가 기록 · 스타일 점수.
 * 가져오지 않는 것: 체형 사진(원래 서버에 없음), 체형 동의 상태(이 폰에서 다시 동의), 알림 설정.
 * 체형 숫자 기록은 서버에 그대로 있어서, 동의하면 '측정 기록 · 변화 그래프'에서 다시 볼 수 있다.
 *
 * 가져온 기록은 이 폰의 지금 기록을 바꿔 쓰고, 앱을 다시 시작해서 불러온다.
 */
object BackupRestore {

    sealed class Result {
        data class Done(val clothes: Int, val photos: Int, val wearLogs: Int, val interests: Int, val tasteEvents: Int) : Result()
        object InvalidCode : Result()
        object SameCode : Result()
        object Offline : Result()
        object NotFound : Result()
    }

    /** 사용자가 입력한 코드 다듬기 (공백 제거 · 소문자). 형식이 틀리면 null */
    fun normalize(input: String): String? {
        val code = input.filterNot { it.isWhitespace() }.lowercase()
        return code.takeIf { Regex("[a-z0-9-]{8,64}").matches(it) }
    }

    data class RemoteClothing(val item: WardrobeItem, val hasPhoto: Boolean)

    data class Snapshot(
        val clothes: List<RemoteClothing>,
        val wearLogs: List<WearLog>,
        val preferredStyles: List<String>,
        val gender: String?,
        val interests: List<String>,
        val tasteEvents: List<TasteEvent>,
        val styleScores: Map<String, Int>?,
        val styleRatedCount: Int
    ) {
        val isEmpty: Boolean
            get() = clothes.isEmpty() && wearLogs.isEmpty() && interests.isEmpty() && tasteEvents.isEmpty() &&
                preferredStyles.isEmpty() && styleRatedCount == 0
    }

    // ── 서버 응답 읽기 (PC 단위 테스트 가능) ──────────────────

    private fun JSONObject.str(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
    private fun JSONObject.long(key: String): Long? = if (has(key) && !isNull(key)) getLong(key) else null
    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else List(length()) { getString(it) }

    /** "#RRGGBB" → 0xFFRRGGBB (틀리면 기본 베이지) */
    fun colorValue(hex: String?): Long =
        hex?.removePrefix("#")?.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { 0xFF000000L or it } ?: 0xFFEDE3D5L

    fun parseWardrobe(body: String): List<RemoteClothing> {
        val arr = JSONObject(body).getJSONArray("items")
        val samples = SampleWardrobe.items().associateBy { it.id }
        return List(arr.length()) { arr.getJSONObject(it) }.mapNotNull { o ->
            val id = o.str("clientId") ?: return@mapNotNull null
            if (id.startsWith("server-")) return@mapNotNull null   // Swagger 로 넣은 시연용 옷 (폰 옷이 아님)
            val sample = samples[id]
            val base = WardrobeItem(
                id = id,
                name = o.str("name") ?: "이름 없음",
                category = o.str("category") ?: "상의",
                colorName = o.str("colorName") ?: "",
                colorValue = colorValue(o.str("color")),
                imageUri = o.str("imageUrl").orEmpty(),
                aiTagged = o.optBoolean("aiTagged", false),
                subCategory = o.str("subCategory"),
                detailType = o.str("detailType"),
                season = o.str("season"),
                addedAt = o.long("addedAt") ?: System.currentTimeMillis(),
                favoritedAt = o.long("favoritedAt"),
                isCutout = o.optBoolean("isCutout", false),
                keptAt = o.long("keptAt"),
                originalUri = o.str("originalUri"),
                cutoutUri = o.str("cutoutUri"),
                garmentUri = o.str("garmentUri"),
                style = o.str("style")
            )
            // 샘플 옷은 앱 안 사진을 그대로 쓴다
            val item = if (sample != null) base.copy(imageUri = sample.imageUri, originalUri = sample.originalUri,
                cutoutUri = sample.cutoutUri, garmentUri = sample.garmentUri) else base
            RemoteClothing(item, o.optBoolean("hasPhoto", false) && sample == null)
        }.sortedBy { it.item.addedAt }
    }

    /**
     * 사진 주소 정리 — 받은 사진이 있으면 그 파일로, 없으면 예전 폰의 파일 주소(이 폰에 없음)는 비운다.
     * 샘플(android.resource://) · 인터넷 사진(http)은 그대로 둔다.
     */
    fun withPhoto(item: WardrobeItem, restoredUri: String?): WardrobeItem {
        fun local(u: String?) = u != null && u.startsWith("file:")
        if (restoredUri != null) {
            return item.copy(imageUri = restoredUri,
                cutoutUri = if (item.isCutout) restoredUri else null,
                originalUri = if (item.isCutout) null else restoredUri,
                garmentUri = null)
        }
        return item.copy(
            imageUri = if (local(item.imageUri)) "" else item.imageUri,
            originalUri = item.originalUri.takeUnless { local(it) },
            cutoutUri = item.cutoutUri.takeUnless { local(it) },
            garmentUri = item.garmentUri.takeUnless { local(it) }
        )
    }

    fun parseWearLogs(body: String): List<WearLog> {
        val arr = JSONObject(body).getJSONArray("logs")
        return List(arr.length()) { arr.getJSONObject(it) }.mapNotNull { o ->
            val ids = o.optJSONArray("itemIds").strings()
            if (ids.isEmpty()) null else WearLog(o.getString("date"), ids, o.optString("memo", ""))
        }.sortedBy { it.date }
    }

    fun parseInterests(body: String): List<String> {
        val arr = JSONObject(body).getJSONArray("items")
        return List(arr.length()) { arr.getJSONObject(it) }
            .sortedBy { it.optInt("position") }.map { it.getString("productKey") }.distinct()
    }

    fun parseTasteEvents(body: String): List<TasteEvent> {
        val arr = JSONObject(body).getJSONArray("events")
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            TasteEvent(
                at = o.getLong("at"), source = o.getString("source"), liked = o.getBoolean("liked"),
                colors = o.optJSONArray("colors").strings(), kinds = o.optJSONArray("kinds").strings(),
                pattern = o.str("pattern"), style = o.str("style"),
                itemIds = o.optJSONArray("itemIds").strings(), date = o.str("date")
            )
        }.sortedBy { it.at }
    }

    /** /api/profile → (선호 스타일, 성별) */
    fun parseProfile(body: String): Pair<List<String>, String?> {
        val o = JSONObject(body)
        return o.optJSONArray("preferredStyles").strings() to o.str("gender")
    }

    /** /api/style-preference → (스타일 점수, 평가 수) */
    fun parseStylePreference(body: String): Pair<Map<String, Int>, Int> {
        val o = JSONObject(body)
        val arr = o.getJSONArray("scores")
        val scores = List(arr.length()) { arr.getJSONObject(it) }.associate { it.getString("style") to it.getInt("score") }
        return scores to o.optInt("ratedCount")
    }

    // ── 복원 ──────────────────────────────────────────

    suspend fun restore(context: Context, input: String): Result = withContext(Dispatchers.IO) {
        val code = normalize(input) ?: return@withContext Result.InvalidCode
        if (code == DbSync.backupCode?.lowercase()) return@withContext Result.SameCode
        val health = BackendApi.getJson("/api/health/database") ?: return@withContext Result.Offline
        if (!runCatching { JSONObject(health).optBoolean("ready") }.getOrDefault(false)) return@withContext Result.Offline

        val wardrobe = BackendApi.getJson("/api/wardrobe", code) ?: return@withContext Result.Offline
        val wear = BackendApi.getJson("/api/wear-logs", code) ?: return@withContext Result.Offline
        val profile = BackendApi.getJson("/api/profile", code)?.let { runCatching { parseProfile(it) }.getOrNull() }
        val interests = BackendApi.getJson("/api/product-interests", code)?.let { runCatching { parseInterests(it) }.getOrNull() }
        val taste = BackendApi.getJson("/api/taste-events", code)?.let { runCatching { parseTasteEvents(it) }.getOrNull() }
        val pref = BackendApi.getJson("/api/style-preference", code)?.let { runCatching { parseStylePreference(it) }.getOrNull() }

        val snapshot = runCatching {
            Snapshot(parseWardrobe(wardrobe), parseWearLogs(wear), profile?.first.orEmpty(), profile?.second,
                interests.orEmpty(), taste.orEmpty(), pref?.first, pref?.second ?: 0)
        }.getOrNull() ?: return@withContext Result.Offline
        if (snapshot.isEmpty) return@withContext Result.NotFound

        // 옷 사진 받기
        val dir = File(context.filesDir, "wardrobe").apply { mkdirs() }
        val files = mutableMapOf<String, File>()
        val uris = mutableMapOf<String, String>()
        val stamp = System.currentTimeMillis()
        val items = snapshot.clothes.map { rc ->
            if (!rc.hasPhoto) return@map withPhoto(rc.item, null)
            val got = BackendApi.getBytes("/api/wardrobe/photos/${Uri.encode(rc.item.id)}", code)
            val file = got?.let { (bytes, mime) ->
                runCatching {
                    val safeId = rc.item.id.replace(Regex("[^A-Za-z0-9_-]"), "_")
                    File(dir, "restored_${safeId}_$stamp.${PhotoBackup.extension(mime)}").apply { writeBytes(bytes) }
                }.getOrNull()
            }
            if (file == null) withPhoto(rc.item, null)
            else {
                val uri = Uri.fromFile(file).toString()
                files[rc.item.id] = file
                uris[rc.item.id] = uri
                withPhoto(rc.item, uri)
            }
        }

        // 이 폰 저장소를 바꿔 쓰기 (앱을 다시 시작하면 불러옴)
        WardrobeStore.writeForRestore(context, items)
        SampleWearHistory.markApplied(context)
        WearLogStore.writeForRestore(context, snapshot.wearLogs)
        ProfileStore.writeForRestore(context, snapshot.preferredStyles)
        UserPreferencesViewModel.writeForRestore(context, snapshot.gender)
        ProductInterestStore.writeForRestore(context, snapshot.interests)
        TastePreferenceStore.writeForRestore(context, snapshot.tasteEvents)
        snapshot.styleScores?.let { StylePreferenceStore.writeForRestore(context, it, snapshot.styleRatedCount) }
        RecommendationLog.clearForRestore(context)
        DbSync.adoptForRestore(code, files, uris)

        Result.Done(items.size, files.size, snapshot.wearLogs.size, snapshot.interests.size, snapshot.tasteEvents.size)
    }

    /** 복원한 기록을 불러오도록 앱을 다시 시작한다 */
    fun restartApp(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val restart = Intent.makeRestartActivityTask(launch.component)
        context.startActivity(restart)
        Runtime.getRuntime().exit(0)
    }
}
