package com.jeiu.fashionproject.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.jeiu.fashionproject.data.DbSync
import com.jeiu.fashionproject.data.PrivacyConsent
import com.jeiu.fashionproject.ui.camera.GuidePoint
import com.jeiu.fashionproject.ui.camera.Joint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
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
    val shoulderToHip: Float = 1f,        // 기존 어깨:힙 비율 유지
    val waistToHip: Float = 1f,
    val measurements: BodyMeasurements? = null,
    val measurementIssue: String? = null
)

sealed class BodyAnalysisUiState {
    object Idle : BodyAnalysisUiState()
    object Analyzing : BodyAnalysisUiState()
    data class Success(val result: BodyAnalysisResult, val imageUri: Uri) : BodyAnalysisUiState()
    data class Error(val message: String) : BodyAnalysisUiState()
}

class BodyAnalysisViewModel(application: Application, private val savedStateHandle: SavedStateHandle) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<BodyAnalysisUiState>(BodyAnalysisUiState.Idle)
    val state: StateFlow<BodyAnalysisUiState> = _state

    // SavedStateHandle survives camera navigation/configuration changes and system state restoration.
    // Not written to the profile: the user can edit this on each new analysis.
    // (Each successful analysis stores its own numeric record, including this height, via DbSync.)
    val heightInput: StateFlow<String> = savedStateHandle.getStateFlow("body_height_cm", "")
    fun updateHeightInput(text: String) {
        if (BodyMeasurementCalculator.acceptsHeightInput(text)) savedStateHandle["body_height_cm"] = text
    }
    private val segmenterDelegate = lazy { BodySegmenter(getApplication()) }
    private val segmenter by segmenterDelegate
    private var analysisJob: Job? = null
    @Volatile private var cleared = false

    fun analyzeImage(context: Context, uri: Uri) {
        analysisJob?.cancel()
        val heightCm = BodyMeasurementCalculator.parseHeight(heightInput.value)
        if (heightCm == null) {
            _state.value = BodyAnalysisUiState.Error("키를 100~230 cm 범위의 숫자로 입력한 뒤 다시 시도해주세요.")
            return
        }
        _state.value = BodyAnalysisUiState.Analyzing
        analysisJob = viewModelScope.launch {
            var bitmap: Bitmap? = null
            try {
                val analysisBitmap = withContext(Dispatchers.IO) {
                    cleanOldBodyPhotos(context, uri)
                    loadBitmap(context, uri)
                }
                bitmap = analysisBitmap
                // Exactly the same EXIF-normalized bitmap for BOTH models; no PreviewView coordinates.
                val pose = detectPose(analysisBitmap)
                ensureActive()
                when (validatePose(pose)) {
                    PoseQuality.NO_PERSON -> _state.value = BodyAnalysisUiState.Error("사람을 감지하지 못했어요.\n정면 전신 사진을 업로드해 주세요.")
                    PoseQuality.NOT_FULL_BODY -> _state.value = BodyAnalysisUiState.Error("전신이 모두 보이지 않아요.\n머리부터 발끝까지 나오게 촬영해 주세요.")
                    PoseQuality.NOT_FRONT -> _state.value = BodyAnalysisUiState.Error("정면 자세가 아니에요.\n카메라를 정면으로 보고 똑바로 서서 촬영해 주세요.")
                    PoseQuality.OK -> {
                        val widths = withContext(Dispatchers.Default) {
                            if (segmenter.isAvailable) measureRealWidths(pose, analysisBitmap) else null
                        }
                        val cm = BodyMeasurementCalculator.calculate(heightCm, widths, System.currentTimeMillis())
                        // Keep the existing classification/fallback even when cm evidence is insufficient.
                        val result = calculateBodyType(pose, widths).copy(
                            measurements = cm,
                            measurementIssue = if (cm == null)
                                "cm 측정에 필요한 전신 경계를 확인하지 못했어요. 머리와 발끝에 여유를 두고, 정면에서 팔을 몸과 떨어뜨려 다시 촬영해주세요."
                                else null
                        )
                        _state.value = BodyAnalysisUiState.Success(result, uri)
                        // 분석 결과 숫자(체형·비율·cm 추정값)만 서버 DB에 기록 — 사진은 보내지 않음
                        // 동의 화면에서 '측정 기록 저장'을 켠 경우에만 (마이페이지에서 끌 수 있음)
                        if (PrivacyConsent.canSaveRecords(context)) DbSync.saveBodyMeasurement(result)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = BodyAnalysisUiState.Error("이미지 분석에 실패했어요. 다른 전신 사진으로 다시 시도해주세요.")
            } finally {
                bitmap?.recycle()
                // Also closes an older cancelled job that was still initializing the native segmenter.
                if (cleared && segmenterDelegate.isInitialized()) segmenter.close()
            }
        }
    }

    /** 예전에 찍은 체형 사진(앱 임시 폴더의 body_*.jpg)을 지운다. 지금 분석할 사진만 남긴다. */
    private fun cleanOldBodyPhotos(context: Context, keep: Uri) {
        val keepPath = keep.takeIf { it.scheme == "file" }?.path
        context.cacheDir.listFiles { f -> f.name.startsWith("body_") && f.name.endsWith(".jpg") }
            ?.forEach { if (it.absolutePath != keepPath) it.delete() }
    }

    // Wait for the native task to release its input before recycling, even after job cancellation.
    private suspend fun detectPose(bitmap: Bitmap): Pose = suspendCoroutine { continuation ->
        val detector = PoseDetection.getClient(AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.SINGLE_IMAGE_MODE).build())
        try {
            detector.process(InputImage.fromBitmap(bitmap, 0)).addOnCompleteListener { task ->
                detector.close()
                if (task.isSuccessful) continuation.resume(task.result)
                else continuation.resumeWithException(task.exception ?: IllegalStateException("Pose detection failed"))
            }
        } catch (exception: Exception) {
            detector.close()
            continuation.resumeWithException(exception)
        }
    }

    fun reset() { analysisJob?.cancel(); _state.value = BodyAnalysisUiState.Idle }

    override fun onCleared() {
        cleared = true
        super.onCleared()
        analysisJob?.cancel()
        val job = analysisJob
        if (job != null) job.invokeOnCompletion { if (segmenterDelegate.isInitialized()) segmenter.close() }
        else if (segmenterDelegate.isInitialized()) segmenter.close()
    }

    private fun loadBitmap(context: Context, uri: Uri): Bitmap {
        val raw = if (Build.VERSION.SDK_INT >= 28) {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                decoder.setTargetSampleSize(((longest + 2047) / 2048).coerceAtLeast(1))
            } // ImageDecoder applies EXIF rotation and reflection.
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth > 0 && bounds.outHeight > 0)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
            val decoded = context.contentResolver.openInputStream(uri)!!.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: error("Cannot decode image")
            try {
                val orientation = context.contentResolver.openInputStream(uri)!!.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                }
                val matrix = Matrix().apply {
                    when (orientation) {
                        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                        ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                        ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                        ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                        ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                        ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                        ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
                    }
                }
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            } catch (exception: Exception) { decoded.recycle(); throw exception }
        }
        return if (raw.config == Bitmap.Config.ARGB_8888) raw else {
            val converted = raw.copy(Bitmap.Config.ARGB_8888, false)
            raw.recycle()
            converted
        }
    }

    private fun measureRealWidths(pose: Pose, bitmap: Bitmap): BodyWidths? {
        val types = mapOf(
            Joint.NOSE to PoseLandmark.NOSE,
            Joint.LEFT_EYE to PoseLandmark.LEFT_EYE, Joint.RIGHT_EYE to PoseLandmark.RIGHT_EYE,
            Joint.LEFT_SHOULDER to PoseLandmark.LEFT_SHOULDER, Joint.RIGHT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
            Joint.LEFT_ELBOW to PoseLandmark.LEFT_ELBOW, Joint.RIGHT_ELBOW to PoseLandmark.RIGHT_ELBOW,
            Joint.LEFT_WRIST to PoseLandmark.LEFT_WRIST, Joint.RIGHT_WRIST to PoseLandmark.RIGHT_WRIST,
            Joint.LEFT_HIP to PoseLandmark.LEFT_HIP, Joint.RIGHT_HIP to PoseLandmark.RIGHT_HIP,
            Joint.LEFT_KNEE to PoseLandmark.LEFT_KNEE, Joint.RIGHT_KNEE to PoseLandmark.RIGHT_KNEE,
            Joint.LEFT_ANKLE to PoseLandmark.LEFT_ANKLE, Joint.RIGHT_ANKLE to PoseLandmark.RIGHT_ANKLE,
            Joint.LEFT_FOOT to PoseLandmark.LEFT_FOOT_INDEX, Joint.RIGHT_FOOT to PoseLandmark.RIGHT_FOOT_INDEX
        )
        val points = types.mapNotNull { (joint, type) -> pose.getPoseLandmark(type)?.let {
            joint to GuidePoint(it.position.x, it.position.y, it.inFrameLikelihood)
        } }.toMap()
        return segmenter.measureWidths(bitmap, points)
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
        if (pose.allPoseLandmarks.any { !it.position.x.isFinite() || !it.position.y.isFinite() || !it.inFrameLikelihood.isFinite() }) return PoseQuality.NOT_FULL_BODY

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
            shoulderToHip = shToHip,
            waistToHip = waistWidth / hipWidth
        )
    }
}



