package com.jeiu.fashionproject.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.sqrt

data class BodyAnalysisResult(
    val bodyTypeKo: String,
    val shoulderRatio: Float,
    val waistRatio: Float,
    val hipRatio: Float,
    val legRatio: Float,
    val characteristics: List<String>,
    val styleAdvice: List<String>
)

sealed class BodyAnalysisUiState {
    object Idle : BodyAnalysisUiState()
    object Analyzing : BodyAnalysisUiState()
    data class Success(val result: BodyAnalysisResult, val imageUri: Uri) : BodyAnalysisUiState()
    data class Error(val message: String) : BodyAnalysisUiState()
}

class BodyAnalysisViewModel : ViewModel() {

    private val _state = MutableStateFlow<BodyAnalysisUiState>(BodyAnalysisUiState.Idle)
    val state: StateFlow<BodyAnalysisUiState> = _state

    fun analyzeImage(context: Context, uri: Uri) {
        _state.value = BodyAnalysisUiState.Analyzing
        try {
            val image = InputImage.fromFilePath(context, uri)
            val options = AccuratePoseDetectorOptions.Builder()
                .setDetectorMode(AccuratePoseDetectorOptions.SINGLE_IMAGE_MODE)
                .build()
            val detector = PoseDetection.getClient(options)

            detector.process(image)
                .addOnSuccessListener { pose ->
                    if (pose.allPoseLandmarks.isEmpty()) {
                        _state.value = BodyAnalysisUiState.Error("사람을 감지하지 못했어요.\n정면 전신 사진을 업로드해 주세요.")
                    } else {
                        _state.value = BodyAnalysisUiState.Success(calculateBodyType(pose), uri)
                    }
                }
                .addOnFailureListener {
                    _state.value = BodyAnalysisUiState.Error("분석 중 오류가 발생했어요.")
                }
        } catch (e: Exception) {
            _state.value = BodyAnalysisUiState.Error("이미지를 불러오지 못했어요.")
        }
    }

    fun reset() {
        _state.value = BodyAnalysisUiState.Idle
    }

    private fun dist(a: PoseLandmark?, b: PoseLandmark?): Float {
        if (a == null || b == null) return 0f
        val dx = a.position.x - b.position.x
        val dy = a.position.y - b.position.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun calculateBodyType(pose: Pose): BodyAnalysisResult {
        val ls = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rs = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val lh = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rh = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val la = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val ra = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)
        val nose = pose.getPoseLandmark(PoseLandmark.NOSE)

        val shoulderWidth = dist(ls, rs)
        val hipWidth = dist(lh, rh).coerceAtLeast(1f)
        val waistWidth = (shoulderWidth + hipWidth) / 2f * 0.8f

        val headY = nose?.position?.y ?: 0f
        val ankleY = if (la != null && ra != null) (la.position.y + ra.position.y) / 2f else 1f
        val bodyHeight = (ankleY - headY).coerceAtLeast(1f)
        val hipY = if (lh != null && rh != null) (lh.position.y + rh.position.y) / 2f else 0f
        val legLength = (ankleY - hipY).coerceAtLeast(0f)

        val maxWidth = maxOf(shoulderWidth, hipWidth, waistWidth).coerceAtLeast(1f)
        val shoulderRatio = shoulderWidth / maxWidth
        val waistRatio = waistWidth / maxWidth
        val hipRatio = hipWidth / maxWidth
        val legRatio = (legLength / bodyHeight).coerceIn(0.3f, 0.95f)

        val shToHip = shoulderWidth / hipWidth
        val waistToShoulder = waistWidth / shoulderWidth.coerceAtLeast(1f)

        val bodyTypeKo: String
        val characteristics: List<String>
        val styleAdvice: List<String>

        when {
            shToHip > 1.1f -> {
                bodyTypeKo = "역삼각형"
                characteristics = listOf(
                    "어깨가 힙보다 넓어요",
                    "상체가 발달되어 있어요",
                    "허리 라인이 뚜렷해요"
                )
                styleAdvice = listOf(
                    "와이드 팬츠로 하체 볼륨 추가",
                    "V넥 상의로 어깨선 분산",
                    "플레어 스커트 추천"
                )
            }
            shToHip < 0.9f -> {
                bodyTypeKo = "삼각형"
                characteristics = listOf(
                    "힙이 어깨보다 넓어요",
                    "하체가 발달되어 있어요",
                    "상체가 날씬해요"
                )
                styleAdvice = listOf(
                    "어깨 볼륨 있는 상의 추천",
                    "A라인 스커트로 밸런스",
                    "밝은 색 상의 활용"
                )
            }
            // ML Kit Pose에는 허리 랜드마크가 없어 waistWidth는 (어깨+힙)/2*0.8로 추정한 값이다.
            // 이 식 때문에 shToHip이 0.9~1.1인 구간에서 waistToShoulder는 항상 0.76~0.84 범위로만
            // 나오므로, 기존 0.75f 기준은 수학적으로 영원히 거짓이 되는 죽은 분기였다(모래시계형이
            // 절대 안 나옴). 그 구간의 중간값(0.8f)으로 재조정해 두 분류가 실제로 나뉘어 나오게 한다.
            waistToShoulder < 0.80f -> {
                bodyTypeKo = "모래시계형"
                characteristics = listOf(
                    "어깨와 힙의 너비가 비슷해요",
                    "허리 라인이 뚜렷하게 들어가요",
                    "상하체 균형이 잘 맞아요"
                )
                styleAdvice = listOf(
                    "허리 강조 원피스 추천",
                    "벨트로 허리 라인 살리기",
                    "바디컨 스타일 잘 어울림"
                )
            }
            else -> {
                bodyTypeKo = "직사각형"
                characteristics = listOf(
                    "어깨, 허리, 힙이 비슷한 너비예요",
                    "균형 잡힌 체형이에요",
                    "다양한 스타일이 잘 어울려요"
                )
                styleAdvice = listOf(
                    "허리 라인 강조 스타일 추천",
                    "레이어드 코디로 입체감 연출",
                    "벨트 활용으로 곡선 만들기"
                )
            }
        }

        return BodyAnalysisResult(
            bodyTypeKo = bodyTypeKo,
            shoulderRatio = shoulderRatio,
            waistRatio = waistRatio,
            hipRatio = hipRatio,
            legRatio = legRatio,
            characteristics = characteristics,
            styleAdvice = styleAdvice
        )
    }
}
