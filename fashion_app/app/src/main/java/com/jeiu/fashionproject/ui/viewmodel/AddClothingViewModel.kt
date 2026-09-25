package com.jeiu.fashionproject.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeiu.fashionproject.data.BackendApi
import com.jeiu.fashionproject.data.DetectionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AI 옷 등록 자동 태깅 ViewModel.
 *
 * 1) FastAPI 백엔드의 YOLOv8 객체 탐지 API 호출 → 라벨/개수/결과 이미지
 * 2) 이미지 중앙 영역의 대표 색상 추출 → 색상 태그 자동 생성
 * 3) 서버에 연결할 수 없으면 색상 추출만으로 로컬 태깅 (오프라인 폴백)
 */
data class AutoTag(
    val suggestedName: String,
    val suggestedCategory: String,
    val colorName: String,
    val colorValue: Long,
    val detectedLabels: List<String>,      // 탐지된 객체 라벨(한글)
    val resultImageUrl: String?,           // 바운딩 박스 결과 이미지 (서버 연결 시)
    val fromServer: Boolean
)

sealed class AddClothingUiState {
    object Idle : AddClothingUiState()
    object Tagging : AddClothingUiState()
    data class Tagged(val tag: AutoTag, val imageUri: Uri) : AddClothingUiState()
    data class Error(val message: String) : AddClothingUiState()
}

class AddClothingViewModel : ViewModel() {

    private val _state = MutableStateFlow<AddClothingUiState>(AddClothingUiState.Idle)
    val state: StateFlow<AddClothingUiState> = _state

    fun tagImage(context: Context, uri: Uri) {
        _state.value = AddClothingUiState.Tagging
        viewModelScope.launch {
            val (colorName, colorValue) = runCatching { dominantColor(context, uri) }
                .getOrDefault("베이지" to 0xFFEDE3D5)

            val serverResult = runCatching { BackendApi.detectObjects(context, uri) }.getOrNull()

            val tag = if (serverResult != null) {
                buildTagFromServer(serverResult, colorName, colorValue)
            } else {
                // 서버 미연결 시 색상 기반 로컬 태깅
                AutoTag(
                    suggestedName = "$colorName 아이템",
                    suggestedCategory = "상의",
                    colorName = colorName,
                    colorValue = colorValue,
                    detectedLabels = emptyList(),
                    resultImageUrl = null,
                    fromServer = false
                )
            }
            _state.value = AddClothingUiState.Tagged(tag, uri)
        }
    }

    fun reset() { _state.value = AddClothingUiState.Idle }

    // ── 서버 탐지 결과 → 태그 ───────────────────────────────────────────────
    private fun buildTagFromServer(r: DetectionResult, colorName: String, colorValue: Long): AutoTag {
        val labelsKo = r.detections.map { labelKo(it.label) }.distinct()
        val best = r.detections.maxByOrNull { it.confidence }
        val category = best?.let { categoryFor(it.label) } ?: "상의"
        val name = when {
            best == null -> "$colorName 아이템"
            best.label == "person" -> "$colorName 착용 코디"
            else -> "$colorName ${labelKo(best.label)}"
        }
        return AutoTag(
            suggestedName = name,
            suggestedCategory = category,
            colorName = colorName,
            colorValue = colorValue,
            detectedLabels = labelsKo,
            resultImageUrl = r.resultImageUrl,
            fromServer = true
        )
    }

    private fun labelKo(label: String) = when (label) {
        "person" -> "인물"
        "handbag" -> "핸드백"
        "backpack" -> "백팩"
        "suitcase" -> "캐리어"
        "tie" -> "넥타이"
        "umbrella" -> "우산"
        "hat" -> "모자"
        else -> label
    }

    private fun categoryFor(label: String) = when (label) {
        "handbag", "backpack", "suitcase", "tie", "umbrella", "hat" -> "액세서리"
        else -> "상의"
    }

    // ── 대표 색상 추출 ──────────────────────────────────────────────────────
    private suspend fun dominantColor(context: Context, uri: Uri): Pair<String, Long> =
        withContext(Dispatchers.Default) {
            val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.setTargetSize(64, 64)
                }
            } else {
                val raw = context.contentResolver.openInputStream(uri)!!.use {
                    BitmapFactory.decodeStream(it)
                }
                Bitmap.createScaledBitmap(raw, 64, 64, true).also { if (it != raw) raw.recycle() }
            }
            // 배경 영향을 줄이기 위해 중앙 50% 영역만 평균
            var r = 0L; var g = 0L; var b = 0L; var n = 0L
            val x0 = bitmap.width / 4; val x1 = bitmap.width * 3 / 4
            val y0 = bitmap.height / 4; val y1 = bitmap.height * 3 / 4
            for (y in y0 until y1) for (x in x0 until x1) {
                val px = bitmap.getPixel(x, y)
                r += (px shr 16) and 0xFF; g += (px shr 8) and 0xFF; b += px and 0xFF; n++
            }
            bitmap.recycle()
            val rr = (r / n).toInt(); val gg = (g / n).toInt(); val bb = (b / n).toInt()
            nearestColorName(rr, gg, bb) to (0xFF000000L or (rr.toLong() shl 16) or (gg.toLong() shl 8) or bb.toLong())
        }

    private fun nearestColorName(r: Int, g: Int, b: Int): String {
        val palette = listOf(
            "블랙" to Triple(30, 30, 30),
            "화이트" to Triple(240, 240, 240),
            "그레이" to Triple(140, 140, 140),
            "베이지" to Triple(222, 205, 180),
            "브라운" to Triple(120, 90, 60),
            "네이비" to Triple(40, 55, 95),
            "블루" to Triple(70, 110, 190),
            "그린" to Triple(90, 140, 95),
            "레드" to Triple(180, 60, 60),
            "핑크" to Triple(225, 160, 175),
            "옐로우" to Triple(220, 195, 90),
        )
        return palette.minBy { (_, c) ->
            val (cr, cg, cb) = c
            (r - cr) * (r - cr) + (g - cg) * (g - cg) + (b - cb) * (b - cb)
        }.first
    }
}
