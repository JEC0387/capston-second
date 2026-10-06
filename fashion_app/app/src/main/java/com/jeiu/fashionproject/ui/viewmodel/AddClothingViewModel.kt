package com.jeiu.fashionproject.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeiu.fashionproject.data.BackendApi
import com.jeiu.fashionproject.data.ClothingCandidate
import com.jeiu.fashionproject.data.ClothingCategories
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.data.WardrobeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * AI 옷 등록 / 옷 정보 수정 ViewModel.
 *
 * 등록: 1) 누끼(배경 제거) — ML Kit 피사체 분리, 폰 안에서 동작 (BackgroundRemover)
 *       2) 옷 종류 분류 — 서버 DeepFashion2 + COCO 가방류 (누끼 결과를 흰 배경으로 채워 전송)
 *       3) 옷만 오려내기 — 고른 후보의 윤곽 마스크로 사람 몸·다른 옷을 지움
 *       4) 옷 픽셀(투명하지 않은 부분)만으로 대표 색상 추출
 *       사진 세 가지(원본·배경 제거·옷만)를 모두 보관해 나중에 수정에서 바꿀 수 있게 한다.
 * 수정: 저장된 옷을 불러와 같은 화면에서 고친다. 사진 종류는 저장해 둔 버전 중에서만 고른다.
 * 서버에 연결할 수 없으면 사용자가 종류를 직접 고르고, 누끼에 실패하면 원본 사진으로 계속 진행한다.
 */
enum class ImageMode { GARMENT, CUTOUT, ORIGINAL }

data class ColorTag(val name: String, val value: Long)

sealed class AddClothingUiState {
    object Idle : AddClothingUiState()
    object Tagging : AddClothingUiState()
    data class Tagged(
        val candidates: List<ClothingCandidate>,   // 크게 잡힌 순서 (서버 미연결·분석 전이면 빈 목록)
        val selected: Int,                         // 고른 후보 번호
        val fromServer: Boolean,
        val originalUri: String?,                  // 원본
        val cutoutUri: String?,                    // 배경 제거 (실패 시 null)
        val garmentUri: String?,                   // 고른 후보만 오려낸 사진 (마스크 없으면 null)
        val colors: Map<ImageMode, ColorTag>,      // 사진 종류별 대표 색
        val defaultMode: ImageMode,                // 처음 보여줄 사진 종류
        val garmentLoading: Boolean = false,
        val analyzed: Boolean = true               // 수정 화면처럼 분석 없이 저장된 정보를 불러왔으면 false
    ) : AddClothingUiState() {
        val selectedCandidate: ClothingCandidate? get() = candidates.getOrNull(selected)
        fun uriFor(mode: ImageMode): String? = when (mode) {
            ImageMode.GARMENT -> garmentUri
            ImageMode.CUTOUT -> cutoutUri
            ImageMode.ORIGINAL -> originalUri
        }
        fun availableModes(): List<ImageMode> = ImageMode.entries.filter { uriFor(it) != null }
    }
    data class Error(val message: String) : AddClothingUiState()
}

class AddClothingViewModel : ViewModel() {

    private val _state = MutableStateFlow<AddClothingUiState>(AddClothingUiState.Idle)
    val state: StateFlow<AddClothingUiState> = _state

    /** 수정 중인 옷 (등록 모드면 null) */
    var editingItem: WardrobeItem? = null
        private set

    private var garmentJob: Job? = null

    // ── 등록 ────────────────────────────────────────────────────────────

    fun tagImage(context: Context, uri: Uri) {
        _state.value = AddClothingUiState.Tagging
        viewModelScope.launch { analyze(context, uri) }
    }

    /** 누끼 → 분류 → 색 → 첫 후보 오려내기 */
    private suspend fun analyze(context: Context, uri: Uri) {
        // ① 누끼
        val cut = runCatching { BackgroundRemover.process(context, uri) }.getOrElse {
            _state.value = AddClothingUiState.Error("사진을 읽을 수 없어요. 다른 사진을 선택해 주세요.")
            return
        }

        // ② 옷 종류 분류 (누끼가 있으면 흰 배경 이미지로)
        val uploadFile = File(cut.uploadPath)
        val candidates = runCatching {
            BackendApi.classifyClothing(context, Uri.fromFile(uploadFile))
        }.getOrNull()
        if (cut.uploadPath != cut.originalPath) uploadFile.delete()

        // ③ 대표 색 — 누끼가 있으면 옷 픽셀만, 없으면 원본 가운데 영역
        val colors = mutableMapOf<ImageMode, ColorTag>()
        colors[ImageMode.ORIGINAL] = colorOf(null, cut.originalPath)
        cut.cutoutBitmap?.let { colors[ImageMode.CUTOUT] = colorOf(it, cut.originalPath); it.recycle() }

        val tagged = AddClothingUiState.Tagged(
            candidates = candidates.orEmpty(),
            selected = 0,
            fromServer = candidates != null,
            originalUri = fileUri(cut.originalPath),
            cutoutUri = cut.cutoutPath?.let(::fileUri),
            garmentUri = null,
            colors = colors,
            defaultMode = if (cut.cutoutPath != null) ImageMode.CUTOUT else ImageMode.ORIGINAL
        )
        _state.value = tagged
        // ④ 첫 번째 후보만 오려내기
        if (tagged.candidates.isNotEmpty()) selectCandidate(context, 0)
    }

    /** 후보를 바꾸면 그 옷의 윤곽으로 다시 오려낸다. */
    fun selectCandidate(context: Context, index: Int) {
        val s = _state.value as? AddClothingUiState.Tagged ?: return
        val candidate = s.candidates.getOrNull(index) ?: return
        garmentJob?.cancel()
        s.garmentUri?.let { deleteIfTemp(context, it) }
        val baseMode = if (s.cutoutUri != null) ImageMode.CUTOUT else ImageMode.ORIGINAL
        _state.value = s.copy(
            selected = index, garmentUri = null, colors = s.colors - ImageMode.GARMENT,
            defaultMode = baseMode, garmentLoading = candidate.maskPngBase64 != null
        )
        val mask = candidate.maskPngBase64 ?: return
        // 서버에 보낸 사진과 같은 비율의 이미지(누끼, 없으면 원본) 위에 마스크를 씌운다
        val basePath = pathOf(s.cutoutUri ?: s.originalUri) ?: return

        garmentJob = viewModelScope.launch {
            val garment = runCatching { BackgroundRemover.cutGarment(context, basePath, mask) }.getOrNull()
            // 그사이 다른 후보를 골랐으면 이 결과는 버린다
            if (!isActive) { garment?.let { File(it.path).delete(); it.bitmap.recycle() }; return@launch }
            val cur = _state.value as? AddClothingUiState.Tagged ?: return@launch
            if (garment == null) {
                _state.value = cur.copy(garmentLoading = false)
                return@launch
            }
            val color = colorOf(garment.bitmap, basePath)
            garment.bitmap.recycle()
            // 오려낸 옷이 원래 누끼의 85% 미만이면 사람 몸·다른 옷이 섞여 있었던 것 → "옷만"을 기본으로
            val mode = if (garment.coverage < 0.85f) ImageMode.GARMENT else baseMode
            _state.value = cur.copy(
                garmentUri = fileUri(garment.path),
                colors = cur.colors + (ImageMode.GARMENT to color),
                defaultMode = mode,
                garmentLoading = false
            )
        }
    }

    /** 옷장에 새로 저장. 사진 세 가지를 모두 보관한다. */
    fun save(
        context: Context, name: String, category: String, subCategory: String?,
        detailType: String?, season: String, style: String?, mode: ImageMode
    ) {
        val s = _state.value as? AddClothingUiState.Tagged ?: return
        val shown = s.uriFor(mode) ?: s.originalUri ?: return
        val color = colorFor(s, mode)
        WardrobeStore.add(
            context,
            WardrobeItem(
                name = name.ifBlank { suggestedName(color.name, category, subCategory, detailType) },
                category = category,
                colorName = color.name,
                colorValue = color.value,
                imageUri = shown,
                aiTagged = s.fromServer && s.candidates.isNotEmpty(),
                subCategory = subCategory,
                detailType = detailType,
                season = season,
                style = style,
                isCutout = shown != s.originalUri,
                originalUri = s.originalUri,
                cutoutUri = s.cutoutUri,
                garmentUri = s.garmentUri
            )
        )
        _state.value = AddClothingUiState.Idle
    }

    // ── 수정 ────────────────────────────────────────────────────────────

    /** 저장된 옷을 불러와 수정 화면을 연다. */
    fun startEdit(context: Context, item: WardrobeItem) {
        if (editingItem?.id == item.id) return   // 화면이 다시 그려져도 한 번만
        editingItem = item
        // 보여주던 사진이 버전 칸에 없으면(버전 칸이 생기기 전에 등록한 옷) 누끼 여부에 따라 알맞은 자리에 넣는다
        var original = item.originalUri
        var cutout = item.cutoutUri
        val garment = item.garmentUri
        if (item.imageUri !in setOfNotNull(original, cutout, garment)) {
            if (item.isCutout) cutout = cutout ?: item.imageUri else original = original ?: item.imageUri
        }
        val currentMode = when (item.imageUri) {
            garment -> ImageMode.GARMENT
            cutout -> ImageMode.CUTOUT
            else -> ImageMode.ORIGINAL
        }
        val tagged = AddClothingUiState.Tagged(
            candidates = emptyList(), selected = 0, fromServer = false,
            originalUri = original, cutoutUri = cutout, garmentUri = garment,
            colors = mapOf(currentMode to ColorTag(item.colorName, item.colorValue)),
            defaultMode = currentMode, analyzed = false
        )
        _state.value = tagged
        // 나머지 버전의 대표 색은 사진에서 계산해 둔다
        viewModelScope.launch {
            val extra = tagged.availableModes().filter { it != currentMode }.mapNotNull { mode ->
                colorOfUri(context, tagged.uriFor(mode)!!, cut = mode != ImageMode.ORIGINAL)?.let { mode to it }
            }
            val cur = _state.value as? AddClothingUiState.Tagged ?: return@launch
            if (cur.analyzed) return@launch
            _state.value = cur.copy(colors = cur.colors + extra)
        }
    }

    /** 수정 완료 — 같은 번호로 덮어써서 착용 기록·하트·등록일·보관 기록이 그대로 이어진다. */
    fun saveEdit(
        context: Context, name: String, category: String, subCategory: String?,
        detailType: String?, season: String, style: String?, mode: ImageMode
    ) {
        val s = _state.value as? AddClothingUiState.Tagged ?: return
        val item = editingItem ?: return
        val shown = s.uriFor(mode) ?: s.originalUri ?: s.cutoutUri ?: s.garmentUri ?: return
        val color = colorFor(s, mode)
        WardrobeStore.update(
            context,
            item.copy(
                name = name.ifBlank { suggestedName(color.name, category, subCategory, detailType) },
                category = category,
                subCategory = subCategory,
                detailType = detailType,
                season = season,
                style = style,
                colorName = color.name,
                colorValue = color.value,
                imageUri = shown,
                isCutout = shown != s.originalUri,
                aiTagged = item.aiTagged || (s.fromServer && s.candidates.isNotEmpty()),
                originalUri = s.originalUri,
                cutoutUri = s.cutoutUri,
                garmentUri = s.garmentUri
            )
        )
        editingItem = null
        _state.value = AddClothingUiState.Idle
    }

    // ── 공통 ────────────────────────────────────────────────────────────

    /** 저장하지 않고 다시 시작 — 만들어 둔 사진 파일을 지운다. */
    fun reset(context: Context) {
        garmentJob?.cancel()
        discardFiles(context)
        _state.value = AddClothingUiState.Idle
    }

    private var appContext: Context? = null

    /** 화면이 사라질 때 임시 파일 정리를 위해 앱 Context를 기억해 둔다. */
    fun attach(context: Context) { appContext = context.applicationContext }

    override fun onCleared() {
        appContext?.let { discardFiles(it) }
    }

    private fun discardFiles(context: Context) {
        val s = _state.value as? AddClothingUiState.Tagged ?: return
        listOfNotNull(s.originalUri, s.cutoutUri, s.garmentUri).forEach { deleteIfTemp(context, it) }
    }

    /** 이번 등록/수정 중에 만든 파일만 지운다 (수정 중인 옷에 저장된 사진은 지키기) */
    private fun deleteIfTemp(context: Context, uri: String) {
        if (editingItem?.photoUris()?.contains(uri) == true) return
        WardrobeStore.deletePhoto(context, uri)
    }

    private fun colorFor(s: AddClothingUiState.Tagged, mode: ImageMode): ColorTag =
        s.colors[mode] ?: s.colors.values.firstOrNull() ?: ColorTag("베이지", 0xFFEDE3D5)

    private fun fileUri(path: String) = Uri.fromFile(File(path)).toString()

    private fun pathOf(uri: String?): String? =
        uri?.let { Uri.parse(it) }?.takeIf { it.scheme == "file" }?.path

    companion object {
        /** 예: "블루 셔츠", "블랙 긴바지" */
        fun suggestedName(colorName: String, category: String, subCategory: String?, detailType: String?) =
            "$colorName ${detailType ?: subCategory ?: category}"

        /** AI 후보 → 앱 분류 (모르는 종류면 null) */
        fun mappingOf(candidate: ClothingCandidate?) =
            candidate?.let { ClothingCategories.fromModelLabel(it.label) }
    }

    // ── 대표 색상 추출 ──────────────────────────────────────────────────────
    private suspend fun colorOf(cutout: Bitmap?, originalPath: String): ColorTag =
        runCatching { dominantColor(cutout, BitmapFactory.decodeFile(originalPath)) }
            .getOrDefault(ColorTag("베이지", 0xFFEDE3D5))

    /** 저장된 사진 주소(file:// 또는 android.resource://)에서 대표 색 */
    private suspend fun colorOfUri(context: Context, uri: String, cut: Boolean): ColorTag? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = context.contentResolver.openInputStream(Uri.parse(uri))!!.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 4 })
                }!!
                if (cut) dominantColor(bitmap, null).also { bitmap.recycle() }
                else dominantColor(null, bitmap)
            }.getOrNull()
        }

    /** [cutout]이 있으면 불투명 픽셀(= 옷)만, 없으면 [original] 가운데 50% 영역 평균. 받은 원본 비트맵은 여기서 recycle 한다. */
    private suspend fun dominantColor(cutout: Bitmap?, original: Bitmap?): ColorTag =
        withContext(Dispatchers.Default) {
            var r = 0L; var g = 0L; var b = 0L; var n = 0L
            if (cutout != null) {
                // 누끼: 투명하지 않은 픽셀(= 옷)만 평균 → 배경색이 섞이지 않는다
                val small = Bitmap.createScaledBitmap(cutout, 64, 64, true)
                for (y in 0 until 64) for (x in 0 until 64) {
                    val px = small.getPixel(x, y)
                    if ((px ushr 24) < 200) continue
                    r += (px shr 16) and 0xFF; g += (px shr 8) and 0xFF; b += px and 0xFF; n++
                }
                if (small != cutout) small.recycle()
            }
            if (n == 0L && original != null) {
                // 누끼가 없으면 배경 영향을 줄이기 위해 원본 중앙 50% 영역만 평균
                val bitmap = Bitmap.createScaledBitmap(original, 64, 64, true)
                for (y in 16 until 48) for (x in 16 until 48) {
                    val px = bitmap.getPixel(x, y)
                    r += (px shr 16) and 0xFF; g += (px shr 8) and 0xFF; b += px and 0xFF; n++
                }
                if (bitmap != original) bitmap.recycle()
            }
            original?.recycle()
            if (n == 0L) return@withContext ColorTag("베이지", 0xFFEDE3D5)
            val rr = (r / n).toInt(); val gg = (g / n).toInt(); val bb = (b / n).toInt()
            ColorTag(
                nearestColorName(rr, gg, bb),
                0xFF000000L or (rr.toLong() shl 16) or (gg.toLong() shl 8) or bb.toLong()
            )
        }

    /**
     * 색 이름 — RGB 거리 대신 색상(H)·채도(S)·밝기(V)로 판단한다.
     * RGB 거리는 데님처럼 탁한 색을 엉뚱한 색(예: 그린)으로 붙이는 경우가 많았다.
     */
    private fun nearestColorName(r: Int, g: Int, b: Int): String {
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(r, g, b, hsv)
        val (h, s, v) = Triple(hsv[0], hsv[1], hsv[2])
        return when {
            v < 0.2f -> "블랙"
            s < 0.15f -> when {
                v > 0.75f -> "화이트"   // 실내 사진은 그늘 때문에 흰 옷도 실제보다 어둡게 찍힌다
                v < 0.3f -> "블랙"
                else -> "그레이"
            }
            h < 15f || h >= 340f -> if (v > 0.75f && s < 0.5f) "핑크" else "레드"
            h < 45f -> when {
                v < 0.6f -> "브라운"
                s < 0.45f -> "베이지"
                else -> "오렌지"
            }
            h < 70f -> if (s < 0.4f) "베이지" else "옐로우"
            h < 170f -> "그린"
            h < 260f -> if (v < 0.4f) "네이비" else "블루"
            else -> if (v > 0.6f) "핑크" else "퍼플"
        }
    }
}
