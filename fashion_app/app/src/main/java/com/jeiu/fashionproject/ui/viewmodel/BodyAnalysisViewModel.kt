package com.jeiu.fashionproject.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class BodyAnalysisResult(
    val bodyTypeKo: String,
    val shoulderRatio: Float,
    val waistRatio: Float,
    val hipRatio: Float,
    val legRatio: Float,
    val characteristics: List<String>,
    val styleAdvice: List<String>,
    val confidence: Int = 0,              // 분석 신뢰도 (0~100%)
    val shoulderToHip: Float = 1f         // 어깨:힙 비율 (디버그/표시용)
)

sealed class BodyAnalysisUiState {
    object Idle : BodyAnalysisUiState()
    object Analyzing : BodyAnalysisUiState()
    data class Success(val result: BodyAnalysisResult, val imageUri: Uri) : BodyAnalysisUiState()
    data class Error(val message: String) : BodyAnalysisUiState()
}

class BodyAnalysisViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<BodyAnalysisUiState>(BodyAnalysisUiState.Idle)
    val state: StateFlow<BodyAnalysisUiState> = _state

    // 세그멘테이션 측정기 (모델 없으면 isAvailable=false → 포즈 추정 폴백)
    private val segmenter: BodySegmenter by lazy { BodySegmenter(getApplication()) }

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
                    when (validatePose(pose)) {
                        PoseQuality.NO_PERSON ->
                            _state.value = BodyAnalysisUiState.Error("사람을 감지하지 못했어요.\n정면 전신 사진을 업로드해 주세요.")
                        PoseQuality.NOT_FULL_BODY ->
                            _state.value = BodyAnalysisUiState.Error("전신이 모두 보이지 않아요.\n머리부터 발목까지 나오게 촬영해 주세요.")
                        PoseQuality.NOT_FRONT ->
                            _state.value = BodyAnalysisUiState.Error("정면 자세가 아니에요.\n카메라를 정면으로 보고 똑바로 서서 촬영해 주세요.")
                        PoseQuality.OK -> {
                            // 세그멘테이션용 비트맵 로딩 후 실제 폭 측정 시도
                            val widths = runCatching {
                                if (segmenter.isAvailable) {
                                    val bmp = loadBitmap(context, uri)
                                    measureRealWidths(pose, bmp)
                                } else null
                            }.getOrNull()
                            _state.value = BodyAnalysisUiState.Success(calculateBodyType(pose, widths), uri)
                        }
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

    override fun onCleared() {
        super.onCleared()
        runCatching { segmenter.close() }
    }

    private fun loadBitmap(context: Context, uri: Uri): Bitmap {
        val raw = if (Build.VERSION.SDK_INT >= 28) {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { d, _, _ -> d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
        } else {
            context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
        }
        // ARGB_8888 보장 (MediaPipe 입력 요건)
        return if (raw.config != Bitmap.Config.ARGB_8888)
            raw.copy(Bitmap.Config.ARGB_8888, false) else raw
    }

    /** 포즈로 어깨/허리/힙의 '높이'를 잡고, 세그멘테이션 마스크에서 그 높이의 실제 폭을 측정 */
    private fun measureRealWidths(pose: Pose, bitmap: Bitmap): BodyWidths? {
        val ls = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER) ?: return null
        val rs = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER) ?: return null
        val lh = pose.getPoseLandmark(PoseLandmark.LEFT_HIP) ?: return null
        val rh = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP) ?: return null

        val imgH = bitmap.height.toFloat().coerceAtLeast(1f)
        val shoulderY = ((ls.position.y + rs.position.y) / 2f) / imgH
        val hipY = ((lh.position.y + rh.position.y) / 2f) / imgH
        // 허리는 어깨~힙 사이 약 60% 지점(배꼽 근처)으로 잡음
        val waistY = shoulderY + (hipY - shoulderY) * 0.6f

        return segmenter.measureWidths(bitmap, shoulderY, waistY, hipY)
    }

    private enum class PoseQuality { NO_PERSON, NOT_FULL_BODY, NOT_FRONT, OK }

    // 체형 판별에 꼭 필요한 핵심 랜드마크
    private val requiredLandmarks = listOf(
        PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
        PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP,
        PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE
    )

    /** 사진이 체형 분석에 적합한지(전신·정면·신뢰도) 검증한다. */
    private fun validatePose(pose: Pose): PoseQuality {
        if (pose.allPoseLandmarks.isEmpty()) return PoseQuality.NO_PERSON

        // 1) 핵심 랜드마크가 모두 충분한 신뢰도로 잡혔는지 (전신 여부)
        for (type in requiredLandmarks) {
            val lm = pose.getPoseLandmark(type) ?: return PoseQuality.NOT_FULL_BODY
            if (lm.inFrameLikelihood < 0.5f) return PoseQuality.NOT_FULL_BODY
        }

        val ls = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)!!
        val rs = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)!!
        val lh = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)!!
        val rh = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)!!

        // 2) 정면 자세 검증: 옆으로 서면 좌우 어깨 사이 가로폭이 매우 좁아진다.
        //    어깨 가로폭이 (어깨~힙 세로길이) 대비 일정 비율 이상이어야 정면으로 본다.
        val shoulderSpan = kotlin.math.abs(ls.position.x - rs.position.x)
        val hipSpan = kotlin.math.abs(lh.position.x - rh.position.x)
        val torsoHeight = kotlin.math.abs(
            ((lh.position.y + rh.position.y) / 2f) - ((ls.position.y + rs.position.y) / 2f)
        ).coerceAtLeast(1f)
        if (shoulderSpan / torsoHeight < 0.25f && hipSpan / torsoHeight < 0.25f) {
            return PoseQuality.NOT_FRONT
        }

        return PoseQuality.OK
    }

    /** 좌우 랜드마크 가로 거리 (어깨/힙 너비는 가로폭이 더 정확) */
    private fun horizontalWidth(a: PoseLandmark?, b: PoseLandmark?): Float {
        if (a == null || b == null) return 0f
        return kotlin.math.abs(a.position.x - b.position.x)
    }

    private fun calculateBodyType(pose: Pose, realWidths: BodyWidths? = null): BodyAnalysisResult {
        val ls = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rs = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val lh = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rh = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val lk = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rk = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val la = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val ra = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)
        val nose = pose.getPoseLandmark(PoseLandmark.NOSE)

        // ── 너비 측정 ──
        // 세그멘테이션 실측값이 있으면 그것을 사용(허리까지 실측 → 정확),
        // 없으면 포즈 관절 가로폭으로 추정(허리는 좁은 쪽 78%로 근사).
        val usedSegmentation = realWidths != null
        val shoulderWidth: Float
        val hipWidth: Float
        val waistWidth: Float
        if (realWidths != null) {
            shoulderWidth = realWidths.shoulder
            hipWidth = realWidths.hip.coerceAtLeast(1f)
            waistWidth = if (realWidths.waist > 0f) realWidths.waist
                         else minOf(realWidths.shoulder, realWidths.hip) * 0.78f
        } else {
            shoulderWidth = horizontalWidth(ls, rs)
            hipWidth = horizontalWidth(lh, rh).coerceAtLeast(1f)
            waistWidth = minOf(shoulderWidth, hipWidth) * 0.78f
        }

        // ── 세로 비율 (키 대비 다리 길이): 엉덩이~발목 사용, 무릎으로 보정 ──
        val headY = nose?.position?.y ?: ((ls?.position?.y ?: 0f))
        val ankleY = when {
            la != null && ra != null -> (la.position.y + ra.position.y) / 2f
            lk != null && rk != null -> {
                // 발목이 안 잡히면 무릎 위치로 다리 길이를 보수적으로 추정
                val kneeY = (lk.position.y + rk.position.y) / 2f
                val hY = if (lh != null && rh != null) (lh.position.y + rh.position.y) / 2f else 0f
                hY + (kneeY - hY) * 2f   // 엉덩이~무릎 거리를 2배로 발목 추정
            }
            else -> 1f
        }
        val hipY = if (lh != null && rh != null) (lh.position.y + rh.position.y) / 2f else 0f
        val bodyHeight = (ankleY - headY).coerceAtLeast(1f)
        val legLength = (ankleY - hipY).coerceAtLeast(0f)
        val legRatio = (legLength / bodyHeight).coerceIn(0.3f, 0.95f)

        val maxWidth = maxOf(shoulderWidth, hipWidth, waistWidth).coerceAtLeast(1f)
        val shoulderRatio = shoulderWidth / maxWidth
        val waistRatio = waistWidth / maxWidth
        val hipRatio = hipWidth / maxWidth

        val shToHip = shoulderWidth / hipWidth
        val shoulderHipAvg = (shoulderWidth + hipWidth) / 2f
        val waistDefinition = waistWidth / shoulderHipAvg.coerceAtLeast(1f)

        // ── 신뢰도 계산: 랜드마크 평균 likelihood + 좌우 대칭(자세 반듯함) ──
        val keyLandmarks = listOfNotNull(ls, rs, lh, rh, lk, rk, la, ra)
        val avgLikelihood = if (keyLandmarks.isNotEmpty())
            keyLandmarks.map { it.inFrameLikelihood }.average().toFloat() else 0f
        // 어깨선·힙선이 수평에 가까울수록(좌우 y 차이 작을수록) 자세가 반듯함
        val shoulderTilt = if (ls != null && rs != null)
            kotlin.math.abs(ls.position.y - rs.position.y) / shoulderWidth.coerceAtLeast(1f) else 1f
        val hipTilt = if (lh != null && rh != null)
            kotlin.math.abs(lh.position.y - rh.position.y) / hipWidth else 1f
        val symmetryScore = (1f - ((shoulderTilt + hipTilt) / 2f)).coerceIn(0f, 1f)
        // 세그멘테이션 실측을 사용하면 허리까지 실제로 쟀으므로 신뢰도 가산
        val segBonus = if (usedSegmentation) 1.12f else 1.0f
        val confidence = ((avgLikelihood * 0.6f + symmetryScore * 0.4f) * 100 * segBonus)
            .toInt().coerceIn(40, 99)

        val bodyTypeKo: String
        val characteristics: List<String>
        val styleAdvice: List<String>

        when {
            // 1) 어깨가 힙보다 뚜렷하게 넓다 → 역삼각형
            shToHip >= 1.08f -> {
                bodyTypeKo = "역삼각형"
                characteristics = listOf(
                    "어깨가 힙보다 ${((shToHip - 1f) * 100).toInt()}%가량 넓어요",
                    "상체가 발달된 스포티한 체형이에요",
                    "어깨 라인이 또렷해요"
                )
                styleAdvice = listOf(
                    "와이드 팬츠로 하체 볼륨 추가",
                    "V넥 상의로 어깨선 분산",
                    "플레어 스커트로 하체 강조"
                )
            }
            // 2) 힙이 어깨보다 뚜렷하게 넓다 → 삼각형(서양배형)
            shToHip <= 0.92f -> {
                bodyTypeKo = "삼각형"
                characteristics = listOf(
                    "힙이 어깨보다 ${(((1f / shToHip) - 1f) * 100).toInt()}%가량 넓어요",
                    "하체가 발달되어 있어요",
                    "상체가 비교적 날씬해요"
                )
                styleAdvice = listOf(
                    "어깨 볼륨 있는 상의(퍼프/패드) 추천",
                    "A라인 스커트로 하체 커버",
                    "밝은 색 상의 + 어두운 하의"
                )
            }
            // 3) 어깨·힙이 비슷하고 허리가 잘록 → 모래시계형
            waistDefinition <= 0.80f -> {
                bodyTypeKo = "모래시계형"
                characteristics = listOf(
                    "어깨와 힙의 너비가 비슷해요",
                    "허리 라인이 뚜렷하게 들어가요",
                    "상하체 균형이 잘 맞는 체형이에요"
                )
                styleAdvice = listOf(
                    "허리 강조 원피스 추천",
                    "벨트로 허리 라인 살리기",
                    "바디컨·하이웨이스트 잘 어울림"
                )
            }
            // 4) 어깨·힙 비슷하지만 허리 굴곡이 적다 → 직사각형
            else -> {
                bodyTypeKo = "직사각형"
                characteristics = listOf(
                    "어깨, 허리, 힙이 비슷한 너비예요",
                    "균형 잡힌 일자형 실루엣이에요",
                    "다양한 스타일이 잘 어울려요"
                )
                styleAdvice = listOf(
                    "허리 라인 강조 스타일로 곡선 연출",
                    "레이어드 코디로 입체감 추가",
                    "크롭 상의·하이웨이스트로 비율 강조"
                )
            }
        }

        // 다리 비율이 길면 특징에 한 줄 추가
        val finalCharacteristics = if (legRatio >= 0.52f)
            characteristics + "다리 비율이 길어 롱 팬츠가 잘 어울려요"
        else characteristics

        return BodyAnalysisResult(
            bodyTypeKo = bodyTypeKo,
            shoulderRatio = shoulderRatio,
            waistRatio = waistRatio,
            hipRatio = hipRatio,
            legRatio = legRatio,
            characteristics = finalCharacteristics,
            styleAdvice = styleAdvice,
            confidence = confidence,
            shoulderToHip = shToHip
        )
    }
}
