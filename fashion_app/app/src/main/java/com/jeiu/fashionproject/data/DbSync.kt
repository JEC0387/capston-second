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

/** Local stores remain authoritative. Durable, serialized retries; body photos never leave this queue. */
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

    fun pushWearLogs(logs: List<WearLog>) {
        val entries = JSONArray()
        logs.forEach { log -> entries.put(JSONObject().put("date", log.date)
            .put("itemIds", JSONArray(log.itemIds)).put("memo", log.memo)) }
        enqueue("wear", "/api/wear-logs/sync", JSONObject().put("logs", entries).toString())
    }

    fun saveBodyMeasurement(result: BodyAnalysisResult) {
        if (!::prefs.isInitialized || !PrivacyConsent.canSaveRecords(context)) return
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
