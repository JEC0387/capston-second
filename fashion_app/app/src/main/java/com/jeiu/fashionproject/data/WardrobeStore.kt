package com.jeiu.fashionproject.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 가상 옷장 전역 저장소 (5주차 보고서: 원터치 가상 옷장 구축).
 *
 * 샘플 옷·AI 등록 옷·직접 추가한 옷을 모두 이 저장소 하나에서 관리한다.
 * 앱 어디서든 보여줄 수 있도록 StateFlow로 공유하고,
 * SharedPreferences에 JSON으로 영속화해 앱을 재시작해도 유지한다.
 */
data class WardrobeItem(
    val id: String = UUID.randomUUID().toString(),  // 옷마다 고유 번호 (착용 기록에서 옷을 가리킬 때 사용)
    val name: String,
    val category: String,      // 대분류: 상의 / 하의 / 아우터 / 원피스 / 신발 / 액세서리
    val colorName: String,     // 자동 추출된 대표 색상명
    val colorValue: Long,      // 0xFFRRGGBB
    val imageUri: String,      // 옷장에 보여줄 사진 — 앱 폴더(file://) 또는 샘플(android.resource://)
    val aiTagged: Boolean = false,      // AI 자동 태깅 여부
    val subCategory: String? = null,    // 소분류: 반팔 상의 / 긴팔 상의 / 긴바지 / 치마 …
    val detailType: String? = null,     // 세부 종류: 셔츠 / 니트 / 청바지 …
    val season: String? = null,         // 여름 / 겨울 / 봄·가을 / 사계절
    val addedAt: Long = System.currentTimeMillis(),  // 옷장 등록 시각
    val favoritedAt: Long? = null,      // 하트를 누른 시각 (null이면 즐겨찾기 아님)
    val isCutout: Boolean = false,      // 배경을 지운(누끼) 투명 PNG인지
    val keptAt: Long? = null,           // "안 입은 옷" 알림에서 [계속 보관]을 누른 시각 — 이때부터 90일을 다시 센다
    // 옷 정보 수정에서 사진 종류를 바꿀 수 있도록 세 가지 버전을 모두 보관한다 (없으면 null)
    val originalUri: String? = null,    // 원본
    val cutoutUri: String? = null,      // 배경 제거 (ML Kit 누끼)
    val garmentUri: String? = null,     // 옷만 (DeepFashion2 윤곽으로 오려냄)
    val style: String? = null           // 사용자가 고른 스타일 (null이면 StyleTagger가 자동으로 붙인 스타일 사용)
) {
    /** 이 옷이 쓰는 사진 주소 전부 (보여주는 사진 + 보관한 버전들) */
    fun photoUris(): Set<String> = setOfNotNull(imageUri, originalUri, cutoutUri, garmentUri)
}

object WardrobeStore {
    private const val PREFS = "wardrobe_store"
    private const val KEY = "items"
    private const val KEY_SAMPLES_SEEDED = "samples_seeded"

    private val _items = MutableStateFlow<List<WardrobeItem>>(emptyList())
    val items: StateFlow<List<WardrobeItem>> = _items

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null)
        var needsSave = false

        val saved = raw?.let {
            runCatching {
                val arr = JSONArray(it)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    // 예전 버전에서 저장한 옷에는 id가 없으므로 새로 붙여서 다시 저장한다.
                    if (!o.has("id")) needsSave = true
                    fromJson(o)
                }
            }.getOrDefault(emptyList())
        } ?: emptyList()

        // 샘플 옷은 첫 실행 때 한 번만 넣는다. (사용자가 지운 샘플이 다시 생기지 않도록)
        var items = if (!prefs.getBoolean(KEY_SAMPLES_SEEDED, false)) {
            prefs.edit().putBoolean(KEY_SAMPLES_SEEDED, true).apply()
            needsSave = true
            SampleWardrobe.items() + saved
        } else saved

        // 옛날 버전 샘플(외부 사이트 사진)은 앱 안 사진·새 이름으로 바꾼다.
        // 하트·등록일·[계속 보관] 기록은 사용자 것이므로 그대로 둔다.
        val latestSamples = SampleWardrobe.items().associateBy { it.id }
        items = items.map { item ->
            val latest = latestSamples[item.id]
            if (latest == null || !SampleWardrobe.isOutdated(item)) item
            else {
                needsSave = true
                latest.copy(addedAt = item.addedAt, favoritedAt = item.favoritedAt, keptAt = item.keptAt)
            }
        }
        // 사진 버전 칸이 생기기 전에 들어간 샘플은, 사진이 그대로면 원본·옷만 버전을 채워 준다.
        items = items.map { item ->
            val latest = latestSamples[item.id]
            val noVersions = item.originalUri == null && item.cutoutUri == null && item.garmentUri == null
            if (latest == null || !noVersions || item.imageUri != latest.imageUri) item
            else {
                needsSave = true
                item.copy(originalUri = latest.originalUri, cutoutUri = latest.cutoutUri, garmentUri = latest.garmentUri)
            }
        }

        // 신발 세부 종류가 생기기 전에 들어간 샘플 신발은 세부 종류(스니커즈·로퍼)를 채워 준다.
        items = items.map { item ->
            val latest = latestSamples[item.id]
            if (latest == null || item.detailType != null || latest.detailType == null || item.subCategory != latest.subCategory) item
            else {
                needsSave = true
                item.copy(detailType = latest.detailType)
            }
        }

        _items.value = items
        if (needsSave) persist(context)
        cleanUpOrphanPhotos(context)
        // 처음 켤 때 한 번만 샘플 옷을 약 6개월 전 등록으로 옮기고 지난 착용 기록을 넣는다
        SampleWearHistory.applyOnce(context)
    }

    /**
     * 옷 등록 도중 앱이 강제 종료되면 저장하지 않은 사진 파일이 남는다.
     * 어느 옷에도 연결되지 않은 파일 중 1시간 넘게 지난 것만 지운다. (지금 등록 중인 파일 보호)
     */
    private fun cleanUpOrphanPhotos(context: Context) {
        val dir = File(context.filesDir, "wardrobe")
        val used = _items.value.flatMap { it.photoUris() }
            .mapNotNull { Uri.parse(it).takeIf { u -> u.scheme == "file" }?.path }.toSet()
        val cutoff = System.currentTimeMillis() - 60 * 60 * 1000L
        dir.listFiles()?.forEach { f ->
            if (f.absolutePath !in used && f.lastModified() < cutoff) f.delete()
        }
    }

    fun add(context: Context, item: WardrobeItem) {
        _items.value = _items.value + item
        persist(context)
    }

    fun remove(context: Context, id: String) {
        _items.value.find { it.id == id }?.photoUris()?.forEach { deletePhoto(context, it) }
        _items.value = _items.value.filterNot { it.id == id }
        persist(context)
    }

    /** 앱 전용 옷 사진 폴더에 보관한 사진이면 파일을 지운다. (샘플·갤러리 사진은 건드리지 않음) */
    fun deletePhoto(context: Context, uri: String) {
        val path = Uri.parse(uri).takeIf { it.scheme == "file" }?.path ?: return
        val file = File(path)
        if (file.parentFile == File(context.filesDir, "wardrobe")) file.delete()
    }

    /** 옷 정보를 고친다. 고친 뒤 더 이상 쓰지 않는 사진 파일은 지운다. */
    fun update(context: Context, item: WardrobeItem) {
        val old = _items.value.find { it.id == item.id }
        _items.value = _items.value.map { if (it.id == item.id) item else it }
        persist(context)
        old?.photoUris()?.minus(item.photoUris())?.forEach { deletePhoto(context, it) }
    }

    /** [계속 보관] — 지금부터 90일 동안 "안 입은 옷" 알림에서 뺀다. */
    fun keep(context: Context, id: String) {
        _items.value = _items.value.map { if (it.id == id) it.copy(keptAt = System.currentTimeMillis()) else it }
        persist(context)
    }

    /** 하트 켜기/끄기. 켤 때 시각을 기록해, 누른 순서대로 위쪽에 보여줄 수 있게 한다. */
    fun toggleFavorite(context: Context, id: String) {
        _items.value = _items.value.map {
            if (it.id != id) it
            else it.copy(favoritedAt = if (it.favoritedAt == null) System.currentTimeMillis() else null)
        }
        persist(context)
    }

    private fun fromJson(o: JSONObject) = WardrobeItem(
        id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
        name = o.getString("name"),
        category = o.getString("category"),
        colorName = o.getString("colorName"),
        colorValue = o.getLong("colorValue"),
        imageUri = o.getString("imageUri"),
        aiTagged = o.optBoolean("aiTagged", false),
        subCategory = o.optStringOrNull("subCategory"),
        detailType = o.optStringOrNull("detailType"),
        season = o.optStringOrNull("season"),
        addedAt = o.optLong("addedAt", System.currentTimeMillis()),
        favoritedAt = if (o.has("favoritedAt")) o.getLong("favoritedAt") else null,
        isCutout = o.optBoolean("isCutout", false),
        keptAt = if (o.has("keptAt")) o.getLong("keptAt") else null,
        originalUri = o.optStringOrNull("originalUri"),
        cutoutUri = o.optStringOrNull("cutoutUri"),
        garmentUri = o.optStringOrNull("garmentUri"),
        style = o.optStringOrNull("style")
    )

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private fun persist(context: Context) {
        val arr = JSONArray()
        _items.value.forEach { item ->
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
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
