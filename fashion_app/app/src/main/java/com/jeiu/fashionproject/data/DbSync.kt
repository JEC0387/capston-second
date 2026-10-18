package com.jeiu.fashionproject.data

import android.content.Context
import android.content.SharedPreferences
import com.jeiu.fashionproject.ui.viewmodel.BodyAnalysisResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Local stores remain authoritative. Durable, serialized retries; body photos never leave this queue.
 * 서버 DB 로 보내는 것: 옷장 · 착용 기록 · 선호 스타일 · 성별 · 관심 상품 · 취향 평가 기록 · (동의 시) 체형 숫자 기록.
 * 각 항목은 "마지막 상태 한 개"만 대기열에 남아, 서버가 꺼져 있다 켜지면 최신 상태로 맞춰진다.
 */
object DbSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val networkLock = Mutex()
    private val queueLock = Any()
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private val pending = linkedMapOf<String, Pair<String, String>>()
    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount

    fun init(appContext: Context) {
        if (::prefs.isInitialized) return
        context = appContext.applicationContext
        prefs = context.getSharedPreferences("db_sync", Context.MODE_PRIVATE)
        BackendApi.deviceId = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("device_id", it).apply()
        }
        synchronized(queueLock) {
            runCatching {
                val entries = JSONObject(prefs.getString("pending", "{}") ?: "{}")
                entries.keys().forEach { key ->
                    val o = entries.getJSONObject(key)
                    pending[key] = o.getString("path") to o.getString("body")
                }
            }
            _pendingCount.value = pending.size
        }
        if (ProfileStore.hasSelection(context)) pushPreferredStyles(ProfileStore.preferredStyles(context))
        scope.launch {
            while (isActive) { flush(); delay(30_000) }
        }
    }

    private fun persistLocked() {
        val json = JSONObject()
        pending.forEach { (key, request) ->
            json.put(key, JSONObject().put("path", request.first).put("body", request.second))
        }
        prefs.edit().putString("pending", json.toString()).apply()
        _pendingCount.value = pending.size
    }

    private fun enqueue(key: String, path: String, body: String) {
        if (!::prefs.isInitialized) return
        synchronized(queueLock) { pending[key] = path to body; persistLocked() }
        retry()
    }

    fun retry() { scope.launch { delay(500); flush() } }

    private suspend fun flush() = networkLock.withLock {
        val batch = synchronized(queueLock) { pending.toMap() }
        if (batch.isEmpty()) return@withLock
        val health = BackendApi.getJson("/api/health/database") ?: return@withLock
        if (!runCatching { JSONObject(health).optBoolean("ready") }.getOrDefault(false)) return@withLock
        for ((key, request) in batch) {
            if (key.startsWith("body:") && !PrivacyConsent.canSaveRecords(context)) {
                clearPendingBodyRecords(); continue
            }
            // A newer local snapshot may have replaced this entry while another request was running.
            if (synchronized(queueLock) { pending[key] } != request) continue
            val result = BackendApi.postJson(request.first, request.second) ?: continue
            if (result.isNotEmpty()) synchronized(queueLock) {
                if (pending[key] == request) { pending.remove(key); persistLocked() }
            }
        }
    }

    fun clearPendingBodyRecords() {
        if (!::prefs.isInitialized) return
        synchronized(queueLock) { pending.keys.removeAll { it.startsWith("body:") }; persistLocked() }
    }

    /** Serializes deletion against uploads, so successful deletion cannot be undone by old queued records. */
    suspend fun deleteBodyRecords(): Boolean = networkLock.withLock {
        val ok = BackendApi.delete("/api/body-measurements")
        if (ok) clearPendingBodyRecords()
        ok
    }

    fun pushPreferredStyles(styles: List<String>) = enqueue("styles", "/api/profile/styles",
        JSONObject().put("styles", JSONArray(styles)).toString())

    fun pushWardrobe(items: List<WardrobeItem>) = enqueue("wardrobe", "/api/wardrobe/sync", wardrobeJson(items))

    /** 관심 상품(쇼핑 · 상품 상세 · 옷장 '관심 옷' 하트) 목록을 누른 순서대로 서버 DB(product_interests)에 맞춘다 */
    fun pushProductInterests(ids: List<String>) {
        val body = runCatching { JSONObject().put("productIds", JSONArray(ids)).toString() }.getOrNull() ?: return
        enqueue("interests", "/api/product-interests/sync", body)
    }

    /** 마이페이지 성별 설정 (FEMALE / MALE, null = 해제) → 서버 users.gender */
    fun pushGender(gender: String?) {
        val body = runCatching { JSONObject().put("gender", gender ?: JSONObject.NULL).toString() }.getOrNull() ?: return
        enqueue("gender", "/api/profile/gender", body)
    }

    /**
     * 취향 평가 기록 전체(코디 👍👎 · 취향 카드 · 착용)를 서버 DB(taste_events)에 맞춘다.
     * 점수 합계가 아니라 기록 하나하나를 보내서, 서버에서도 같은 규칙으로 다시 계산할 수 있다.
     */
    fun pushTasteEvents(events: List<TasteEvent>) {
        val body = runCatching {
            val arr = JSONArray()
            // 서버 한 번 요청 한도(5000개) 안에서 최근 기록 위주로 보낸다
            events.takeLast(5000).forEach { e ->
                arr.put(
                    JSONObject()
                        .put("at", e.at)
                        .put("source", e.source)
                        .put("liked", e.liked)
                        .put("colors", JSONArray(e.colors.take(20)))
                        .put("kinds", JSONArray(e.kinds.take(20)))
                        .put("pattern", e.pattern ?: JSONObject.NULL)
                        .put("style", e.style ?: JSONObject.NULL)
                        .put("itemIds", JSONArray(e.itemIds.take(20)))
                        .put("date", e.date ?: JSONObject.NULL)
                )
            }
            JSONObject().put("events", arr).toString()
        }.getOrNull() ?: return
        enqueue("taste", "/api/taste-events/sync", body)
    }

    fun pushWearLogs(logs: List<WearLog>) {
        val entries = JSONArray()
        logs.forEach { log -> entries.put(JSONObject().put("date", log.date)
            .put("itemIds", JSONArray(log.itemIds)).put("memo", log.memo)) }
        enqueue("wear", "/api/wear-logs/sync", JSONObject().put("logs", entries).toString())
    }

    fun saveBodyMeasurement(result: BodyAnalysisResult) {
        if (!::prefs.isInitialized || !PrivacyConsent.canSaveRecords(context) || !BodyAnalysisPersistence.mayStore(result)) return
        val body = runCatching {
            val m = result.measurements
            JSONObject()
                .put("clientId", UUID.randomUUID().toString())
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
        }.getOrNull() ?: return

        val key = "body:" + JSONObject(body).getString("clientId")
        enqueue(key, "/api/body-measurements", body)
    }

    private fun JSONObject.putNum(key: String, value: Float?): JSONObject =
        if (value != null && value.isFinite() && value > 0f) put(key, value.toDouble()) else this

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

}
