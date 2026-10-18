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
 * 서버 DB 로 보내는 것: 옷장(+ 옷 사진 백업 · 가격 · 브랜드) · 착용 기록 · 선호 스타일 · 성별 · 관심 상품 ·
 * 취향 평가 기록 · 추천 기록 · 저장한 코디 · 코디 일정 · (동의 시) 체형 숫자 기록.
 * 각 항목은 "마지막 상태 한 개"만 대기열에 남아, 서버가 꺼져 있다 켜지면 최신 상태로 맞춰진다.
 *
 * 마이페이지 > 데이터 백업 · 복원 에서 서버 백업을 끄면, 서버의 내 기록을 지우고 이후로는 아무것도 보내지 않는다.
 * (끄는 동안 기기 번호를 요청에 붙이지 않으므로 서버 DB 에 새 사용자도 생기지 않는다)
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

    /** 서버 백업이 켜져 있는지 (기본 켜짐) */
    val isEnabled: Boolean get() = !::prefs.isInitialized || prefs.getBoolean(KEY_ENABLED, true)

    /** 이 폰의 백업 코드 (= 기기 번호). 다른 폰에서 이 코드를 넣으면 기록을 가져올 수 있다. */
    val backupCode: String? get() = if (::prefs.isInitialized) prefs.getString("device_id", null) else null

    private const val KEY_ENABLED = "sync_enabled"
    private const val KEY_PHOTO_SIGS = "photo_sigs"

    fun init(appContext: Context) {
        if (::prefs.isInitialized) return
        context = appContext.applicationContext
        prefs = context.getSharedPreferences("db_sync", Context.MODE_PRIVATE)
        val id = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("device_id", it).apply()
        }
        BackendApi.deviceId = if (isEnabled) id else null
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
        if (!::prefs.isInitialized || !isEnabled) return
        synchronized(queueLock) { pending[key] = path to body; persistLocked() }
        retry()
    }

    fun retry() { scope.launch { delay(500); flush() } }

    private suspend fun flush() = networkLock.withLock {
        if (!isEnabled) return@withLock
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
            if (key.startsWith("photo:")) { uploadPhoto(key, request); continue }
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

    fun pushWardrobe(items: List<WardrobeItem>) {
        enqueue("wardrobe", "/api/wardrobe/sync", wardrobeJson(items))
        queuePhotos(items)
    }

    // ── 옷 사진 백업 (복원용) ──────────────────────────────
    // 앱 폴더에 있는 옷 사진(file://)만 올린다. 샘플(android.resource://) · 인터넷 사진은 올리지 않는다.
    // 대기열에는 사진 자체가 아니라 파일 위치만 두고, 보낼 때 720px 로 줄여서 보낸다.
    // 한 번 올린 사진은 (주소 · 크기 · 수정 시각)이 바뀔 때만 다시 올린다.

    private fun photoSigs(): JSONObject =
        runCatching { JSONObject(prefs.getString(KEY_PHOTO_SIGS, "{}") ?: "{}") }.getOrDefault(JSONObject())

    private fun signature(file: java.io.File, uri: String) = "$uri|${file.length()}|${file.lastModified()}"

    private fun queuePhotos(items: List<WardrobeItem>) {
        if (!::prefs.isInitialized || !isEnabled) return
        val owned = items.filter { it.isOwned }
        val ids = owned.map { it.id }.toSet()
        synchronized(queueLock) {
            // 지운 옷의 사진은 보내지 않는다 (서버 사진은 옷장 동기화 때 같이 지워짐)
            val stale = pending.keys.filter { it.startsWith("photo:") && it.removePrefix("photo:") !in ids }
            if (stale.isNotEmpty()) { stale.forEach { pending.remove(it) }; persistLocked() }
            val sigs = photoSigs()
            val gone = sigs.keys().asSequence().filter { it !in ids }.toList()
            if (gone.isNotEmpty()) { gone.forEach { sigs.remove(it) }; prefs.edit().putString(KEY_PHOTO_SIGS, sigs.toString()).apply() }
        }
        val sigs = photoSigs()
        owned.forEach { item ->
            val path = android.net.Uri.parse(item.imageUri).takeIf { it.scheme == "file" }?.path ?: return@forEach
            val file = java.io.File(path)
            if (!file.isFile) return@forEach
            if (sigs.optString(item.id) == signature(file, item.imageUri)) return@forEach
            val key = "photo:${item.id}"
            val body = JSONObject().put("clientId", item.id).put("file", path).put("uri", item.imageUri)
                .put("cutout", item.isCutout).toString()
            if (synchronized(queueLock) { pending[key]?.second } != body) enqueue(key, "/api/wardrobe/photos", body)
        }
    }

    private val photoFailures = HashMap<String, Int>()

    private suspend fun uploadPhoto(key: String, request: Pair<String, String>) {
        if ((photoFailures[key] ?: 0) >= 5) return   // 계속 실패하면 이번 실행 동안은 쉬고, 앱을 다시 켜면 다시 시도
        val marker = runCatching { JSONObject(request.second) }.getOrNull()
        val file = marker?.optString("file")?.let { java.io.File(it) }
        val done = { synchronized(queueLock) { if (pending[key] == request) { pending.remove(key); persistLocked() } } }
        if (marker == null || file == null || !file.isFile) { done(); return }   // 사진이 없어졌으면 건너뜀
        val encoded = withContext(Dispatchers.IO) { PhotoBackup.encode(file, marker.optBoolean("cutout")) }
        if (encoded == null) { done(); return }
        val body = JSONObject().put("clientId", marker.getString("clientId")).put("mime", encoded.first)
            .put("data", android.util.Base64.encodeToString(encoded.second, android.util.Base64.NO_WRAP)).toString()
        if (BackendApi.postJson(request.first, body, readTimeoutMs = 30_000) == null) {   // 옷장 동기화 전이면 404 → 다음에 다시
            photoFailures[key] = (photoFailures[key] ?: 0) + 1
            return
        }
        photoFailures.remove(key)
        synchronized(queueLock) {
            val sigs = photoSigs().put(marker.getString("clientId"), signature(file, marker.optString("uri")))
            prefs.edit().putString(KEY_PHOTO_SIGS, sigs.toString()).apply()
        }
        done()
    }

    /** 추천 코디 기록 (추천 성능 측정) — 서버는 새 기록만 더한다 */
    fun pushRecommendations(entries: List<RecommendationEntry>) {
        val body = runCatching {
            val arr = JSONArray()
            entries.takeLast(2000).forEach { e ->
                arr.put(JSONObject().put("date", e.date).put("context", e.context).put("scope", e.scope)
                    .put("rank", e.rank).put("itemIds", JSONArray(e.itemIds)).put("shownAt", e.shownAt))
            }
            JSONObject().put("logs", arr).toString()
        }.getOrNull() ?: return
        enqueue("recs", "/api/recommendations/sync", body)
    }

    /** 저장한 코디 (룩북) — 폰 목록과 똑같이 맞춘다 */
    fun pushSavedOutfits(list: List<SavedOutfit>) {
        val body = runCatching {
            val arr = JSONArray()
            list.take(SavedOutfitStore.MAX).filter { it.itemIds.size in 1..8 && it.itemIds.all { id -> id.length <= 64 } }.forEach {
                arr.put(JSONObject().put("id", it.id).put("itemIds", JSONArray(it.itemIds)).put("style", it.style)
                    .put("pattern", it.pattern ?: JSONObject.NULL).put("note", it.note).put("savedAt", it.savedAt))
            }
            JSONObject().put("outfits", arr).toString()
        }.getOrNull() ?: return
        enqueue("saved_outfits", "/api/saved-outfits/sync", body)
    }

    /** 코디 일정 — 폰 목록과 똑같이 맞춘다 (날짜마다 하나) */
    fun pushOutfitPlans(list: List<OutfitPlan>) {
        val body = runCatching {
            val arr = JSONArray()
            list.filter { it.itemIds.size in 1..8 && it.itemIds.all { id -> id.length <= 64 } }.forEach {
                arr.put(JSONObject().put("date", it.date).put("itemIds", JSONArray(it.itemIds)).put("note", it.note)
                    .put("createdAt", it.createdAt))
            }
            JSONObject().put("plans", arr).toString()
        }.getOrNull() ?: return
        enqueue("plans", "/api/outfit-plans/sync", body)
    }

    // ── 백업 끄기 · 켜기 · 복원 ──────────────────────────────

    /**
     * 서버에 저장된 내 기록을 모두 지우고 서버 백업을 끈다 (폰 안의 기록은 그대로).
     * 성공하면 true. 서버에 연결되지 않으면 false (아무것도 바뀌지 않음).
     */
    suspend fun disableAndDeleteServerData(): Boolean = networkLock.withLock {
        if (!::prefs.isInitialized) return@withLock false
        if (!BackendApi.delete("/api/me", readTimeoutMs = 20_000)) return@withLock false
        synchronized(queueLock) { pending.clear(); persistLocked() }
        prefs.edit().putBoolean(KEY_ENABLED, false).remove(KEY_PHOTO_SIGS).apply()
        BackendApi.deviceId = null
        true
    }

    /** 서버 백업을 다시 켠다 — 폰의 지금 기록을 전부 다시 올린다 */
    fun enable(appContext: Context) {
        if (!::prefs.isInitialized) init(appContext)
        prefs.edit().putBoolean(KEY_ENABLED, true).apply()
        BackendApi.deviceId = backupCode
        WardrobeStore.load(appContext); WearLogStore.load(appContext)
        TastePreferenceStore.load(appContext); ProductInterestStore.load(appContext); RecommendationLog.load(appContext)
        SavedOutfitStore.load(appContext); OutfitPlanStore.load(appContext)
        pushWardrobe(WardrobeStore.items.value)
        pushWearLogs(WearLogStore.logs.value)
        if (ProfileStore.hasSelection(appContext)) pushPreferredStyles(ProfileStore.preferredStyles(appContext))
        appContext.getSharedPreferences("user_prefs", Context.MODE_PRIVATE).getString("gender", null)?.let { pushGender(it) }
        pushProductInterests(ProductInterestStore.ids.value)
        pushTasteEvents(TastePreferenceStore.events.value)
        pushRecommendations(RecommendationLog.entries.value)
        pushSavedOutfits(SavedOutfitStore.items.value)
        pushOutfitPlans(OutfitPlanStore.plans.value)
    }

    /**
     * 백업 코드로 복원할 때 — 이 폰이 그 코드(기기 번호)를 쓰도록 바꾸고, 보내지 않은 대기열은 버린다.
     * @param photos 복원하면서 받은 사진 파일 (옷 id → 파일, 다시 올리지 않도록)
     */
    suspend fun adoptForRestore(code: String, photos: Map<String, java.io.File>, uris: Map<String, String>) =
        networkLock.withLock {
            synchronized(queueLock) { pending.clear(); persistLocked() }
            val sigs = JSONObject()
            photos.forEach { (id, file) -> uris[id]?.let { sigs.put(id, signature(file, it)) } }
            prefs.edit().putString("device_id", code).putBoolean(KEY_ENABLED, true)
                .putString(KEY_PHOTO_SIGS, sigs.toString()).commit()
            BackendApi.deviceId = code
        }

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
                    .put("brand", item.brand)
                    .put("price", item.price)
            )
        }
        return JSONObject().put("items", arr).toString()
    }

}
