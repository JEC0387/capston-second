package com.jeiu.fashionproject.data

import android.content.Context
import com.jeiu.fashionproject.ui.viewmodel.BodyAnalysisResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 폰 저장소 → 서버 DB(Supabase) 동기화.
 *
 * 원칙
 * - 폰(WardrobeStore·WearLogStore)이 기준이다. 서버 DB는 폰 내용을 그대로 따라 저장한다.
 * - 서버가 꺼져 있거나 인터넷이 없어도 앱은 지금처럼 동작한다. (실패는 조용히 넘기고 다음 변경 때 다시 보냄)
 * - 옷을 여러 번 빠르게 바꾸면 0.5초 동안 모았다가 마지막 상태 한 번만 보낸다.
 * - 사진 파일은 보내지 않는다. 옷 사진은 주소만, 체형 분석은 숫자 결과만 저장한다.
 */
object DbSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val DEBOUNCE_MS = 500L

    private var wardrobeJob: Job? = null
    private var wearLogJob: Job? = null
    // 같은 종류 요청이 겹쳐서 서버에 동시에 들어가지 않도록 하나씩 보낸다
    private val wardrobeLock = Mutex()
    private val wearLogLock = Mutex()
    private val likeLock = Mutex()

    // ── 기기 번호 ────────────────────────────────────────────────

    /**
     * 처음 실행 때 이 폰만의 번호(UUID)를 만들어 저장해 두고, 모든 서버 요청에 붙인다.
     * → 팀원 여러 명이 같은 Supabase DB를 써도 폰마다 옷장 · 기록이 따로 저장된다.
     * (로그인 기능을 붙이면 이 번호 대신 로그인 토큰을 보내면 된다)
     */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences("db_sync", Context.MODE_PRIVATE)
        val id = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("device_id", it).apply()
        }
        BackendApi.deviceId = id
    }

    // ── 옷장 ─────────────────────────────────────────────────────

    /** 옷장 전체를 서버에 맞춘다 (WardrobeStore 가 바뀔 때마다 호출). */
    @Synchronized
    fun pushWardrobe(items: List<WardrobeItem>) {
        wardrobeJob?.cancel()
        wardrobeJob = scope.launch {
            delay(DEBOUNCE_MS)
            val body = runCatching { wardrobeJson(items) }.getOrNull() ?: return@launch
            wardrobeLock.withLock { BackendApi.postJson("/api/wardrobe/sync", body) }
        }
    }

    private fun wardrobeJson(items: List<WardrobeItem>): String {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("category", item.category)
                    .put("colorName", item.colorName)
                    .put("colorValue", item.colorValue)
                    .put("imageUri", item.imageUri)
                    .put("aiTagged", item.aiTagged)
                    .put("subCategory", item.subCategory)
                    .put("detailType", item.detailType)
                    .put("season", item.season)
                    .put("addedAt", item.addedAt)
                    .put("favoritedAt", item.favoritedAt)
                    .put("isCutout", item.isCutout)
                    .put("keptAt", item.keptAt)
                    .put("originalUri", item.originalUri)
                    .put("cutoutUri", item.cutoutUri)
                    .put("garmentUri", item.garmentUri)
                    .put("style", item.style)
            )
        }
        return JSONObject().put("items", arr).toString()
    }

    // ── 착용 기록 ────────────────────────────────────────────────

    /** 착용 기록 전체를 서버에 맞춘다 (WearLogStore 가 바뀔 때마다 호출). */
    @Synchronized
    fun pushWearLogs(logs: List<WearLog>) {
        wearLogJob?.cancel()
        wearLogJob = scope.launch {
            delay(DEBOUNCE_MS)
            val body = runCatching {
                val arr = JSONArray()
                logs.forEach { log ->
                    arr.put(
                        JSONObject()
                            .put("date", log.date)
                            .put("itemIds", JSONArray(log.itemIds))
                            .put("memo", log.memo)
                    )
                }
                JSONObject().put("logs", arr).toString()
            }.getOrNull() ?: return@launch
            wearLogLock.withLock { BackendApi.postJson("/api/wear-logs/sync", body) }
        }
    }

    // ── 체형 분석 기록 ───────────────────────────────────────────

    /** 체형 분석 결과(체형·비율·cm 추정값)를 기록으로 남긴다. 사진은 보내지 않는다. */
    fun saveBodyMeasurement(result: BodyAnalysisResult) {
        scope.launch {
            val body = runCatching {
                val m = result.measurements
                JSONObject()
                    .put("bodyType", result.bodyTypeKo)
                    .put("confidence", result.confidence)
                    .putNum("shoulderRatio", result.shoulderRatio)
                    .putNum("waistRatio", result.waistRatio)
                    .putNum("hipRatio", result.hipRatio)
                    .putNum("legRatio", result.legRatio)
                    .putNum("shoulderToHip", result.shoulderToHip)
                    .putNum("waistToHip", result.waistToHip)
                    .putNum("heightCm", m?.heightCm)
                    .putNum("shoulderCm", m?.shoulderWidthCm)
                    .putNum("waistCm", m?.waistWidthCm)
                    .putNum("hipCm", m?.hipWidthCm)
                    .putNum("legCm", m?.legLengthCm)
                    .putNum("torsoCm", m?.torsoLengthCm)
                    .put("measuredAt", m?.measuredAt ?: System.currentTimeMillis())
                    .toString()
            }.getOrNull() ?: return@launch
            BackendApi.postJson("/api/body-measurements", body)
        }
    }

    /** NaN·무한대는 JSON 에 넣을 수 없으므로 빼고 보낸다 (서버에는 빈 값으로 저장). */
    private fun JSONObject.putNum(key: String, value: Float?): JSONObject =
        if (value != null && value.isFinite()) put(key, value.toDouble()) else this

    // ── 쇼핑 하트 ────────────────────────────────────────────────

    /** 상품 이름 → 서버 상품 번호 (처음 한 번 받아 둔다) */
    @Volatile private var productIds: Map<String, Int> = emptyMap()

    private suspend fun productIdMap(): Map<String, Int> {
        if (productIds.isNotEmpty()) return productIds
        val body = BackendApi.getJson("/api/products") ?: return emptyMap()
        val map = runCatching {
            val arr = JSONObject(body).getJSONArray("products")
            (0 until arr.length()).associate { i ->
                val p = arr.getJSONObject(i)
                p.getString("name") to p.getInt("id")
            }
        }.getOrDefault(emptyMap())
        productIds = map
        return map
    }

    /** 서버에 저장된 하트 상품 이름 (누른 순서대로). 서버 연결이 안 되면 null. */
    suspend fun loadLikedProductNames(): List<String>? {
        val body = BackendApi.getJson("/api/products/likes") ?: return null
        return runCatching {
            val arr = JSONObject(body).getJSONArray("items")
            List(arr.length()) { arr.getJSONObject(it).getString("name") }
        }.getOrNull()
    }

    /** 하트 켜기/끄기를 서버에 저장 (누른 순서를 지키도록 하나씩 보냄). */
    fun setProductLike(productName: String, liked: Boolean) {
        scope.launch {
            likeLock.withLock {
                val id = productIdMap()[productName] ?: return@withLock
                val body = runCatching { JSONObject().put("liked", liked).toString() }.getOrNull() ?: return@withLock
                BackendApi.postJson("/api/products/$id/like", body)
            }
        }
    }
}
