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
import androidx.lifecycle.viewModelScope
import com.jeiu.fashionproject.data.DbSync
import com.jeiu.fashionproject.data.BodyAnalysisCompletion
import com.jeiu.fashionproject.data.BodyAnalysisSessionStore
import com.jeiu.fashionproject.data.PrivacyConsent
import com.jeiu.fashionproject.ui.camera.GuidePoint
import com.jeiu.fashionproject.ui.camera.Joint
import com.jeiu.fashionproject.ui.camera.PoseQualityRules
import com.jeiu.fashionproject.ui.camera.GuideStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import kotlinx.coroutines.flow.drop

data class BodyAnalysisResult(
    val bodyTypeKo: String,
    val shoulderRatio: Float,
    val waistRatio: Float,
    val hipRatio: Float,
    val legRatio: Float?,
    val characteristics: List<String>,
    val styleAdvice: List<String>,
    val confidence: Int = 0,              // 입력 관절 검출 참고값 (0~100), 판정 정확도 아님
    val shoulderToHip: Float = 1f,        // 기존 어깨:힙 비율 유지
    val waistToHip: Float = 1f,
    val measurements: BodyMeasurements? = null,
    val measurementIssue: String? = null
)

sealed class BodyAnalysisUiState {
    object Idle : BodyAnalysisUiState()
    data class Analyzing(val requestId: Long) : BodyAnalysisUiState()
    data class Success(val result: BodyAnalysisResult, val imageUri: Uri) : BodyAnalysisUiState()
    data class Partial(val message: String, val imageUri: Uri, val measurements: BodyMeasurements? = null) : BodyAnalysisUiState()
    data class Error(val message: String) : BodyAnalysisUiState()
}

class BodyAnalysisViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<BodyAnalysisUiState>(BodyAnalysisUiState.Idle)
    val state: StateFlow<BodyAnalysisUiState> = _state

    // 입력 키도 분석 중에만 메모리에 둔다. 프로세스가 종료되면 다시 입력한다.
    private val _heightInput = MutableStateFlow("")
    val heightInput: StateFlow<String> = _heightInput
    fun updateHeightInput(text: String) {
        if (BodyMeasurementCalculator.acceptsHeightInput(text)) _heightInput.value = text
    }
    private val segmenterDelegate = lazy { BodySegmenter(getApplication()) }
    private val segmenter by segmenterDelegate
    private var analysisJob: Job? = null
    private var timeoutJob: Job? = null
    private val requestGate = AnalysisRequestGate()
    private val pipelineMutex = Mutex()
    private var currentPhotoUri: Uri? = null
    @Volatile private var cleared = false
    init {
        viewModelScope.launch {
            BodyAnalysisSessionStore.clearGeneration.drop(1).collect { reset() }
        }
    }

    fun analyzeImage(context: Context, uri: Uri) {
        if (cleared) return
        val request = requestGate.begin()
        analysisJob?.cancel()
        timeoutJob?.cancel()
        currentPhotoUri?.takeIf { it != uri }?.let(::deleteTemporaryPhoto)
        currentPhotoUri = uri
        val heightCm = BodyMeasurementCalculator.parseHeight(heightInput.value)
        if (!PrivacyConsent.hasAgreed(context) || heightCm == null) {
            requestGate.invalidate()
            deleteTemporaryPhoto(uri)
            currentPhotoUri = null
            _state.value = BodyAnalysisUiState.Error(if (heightCm == null)
                "키를 100~230 cm 범위의 숫자로 입력한 뒤 다시 시도해주세요."
                else "체형 사진 이용 안내에 동의한 뒤 사진을 선택해주세요.")
            return
        }
        _state.value = BodyAnalysisUiState.Analyzing(request)
        analysisJob = viewModelScope.launch {
            // Cancelled queued requests never allocate bitmaps; native ownership is serialized.
            pipelineMutex.withLock {
                var bitmap: Bitmap? = null
                try {
                    ensureActive()
                    if (!requestGate.isCurrent(request)) return@withLock
                    withContext(NonCancellable + Dispatchers.IO) {
                        cleanOldBodyPhotos(context, uri)
                        // Store ownership before dispatch back: prompt cancellation cannot leak the bitmap.
                        bitmap = loadBitmap(context, uri)
                    }
                    ensureActive()
                    val image = checkNotNull(bitmap)
                    val imageIssue = withContext(NonCancellable + Dispatchers.Default) {
                        val luma = IntArray(64 * 64) { index ->
                            val x = ((index % 64) * image.width / 64).coerceAtMost(image.width - 1)
                            val y = ((index / 64) * image.height / 64).coerceAtMost(image.height - 1)
                            val color = image.getPixel(x,y)
                            (((color shr 16 and 255)*299 + (color shr 8 and 255)*587 + (color and 255)*114)/1000)
                        }
                        ImageInputQuality.evaluate(image.width,image.height,luma)
                    }
                    ensureActive()
                    if (imageIssue != null) {
                        requestGate.publish(request) { _state.value = BodyAnalysisUiState.Error(imageIssue.message) }
                        return@withLock
                    }
                    val pose = detectPose(image)
                    ensureActive()
                    if (!requestGate.isCurrent(request)) return@withLock
                    val quality = PoseQualityRules.evaluate(posePoints(pose),image.width.toFloat(),image.height.toFloat())
                    if (quality != GuideStatus.READY) {
                        requestGate.publish(request) { _state.value = BodyAnalysisUiState.Error(quality.message) }
                        return@withLock
                    }
                    val silhouette = withContext(NonCancellable + Dispatchers.Default) {
                        if (segmenter.isAvailable) segmenter.inspectWidths(image,posePoints(pose))
                        else SilhouetteMeasurement(issue=SilhouetteIssue.MODEL_UNAVAILABLE)
                    }
                    ensureActive()
                    val widths = silhouette.widths
                    val cm = BodyMeasurementCalculator.calculate(heightCm,widths,System.currentTimeMillis())
                    val classification = BodyTypeClassifier.classify(widths)
                    requestGate.publish(request) {
                        if (classification.type == null || cm == null) {
                            _state.value = BodyAnalysisUiState.Partial(silhouette.issue?.message ?: classification.issue?.message
                                ?: "cm 환산이 불확실해요. 머리와 발끝에 여유를 두고 다시 촬영해주세요.",uri,cm)
                        } else {
                            val result = calculateBodyType(pose,widths!!,cm,classification.type)
                            _state.value = BodyAnalysisUiState.Success(result,uri)
                            BodyAnalysisCompletion.complete(cm,PrivacyConsent.canSaveRecords(context)) {
                                DbSync.saveBodyMeasurement(result)
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (isActive) requestGate.publish(request) {
                        _state.value = BodyAnalysisUiState.Error("이미지 분석에 실패했어요. 다른 정면 전신 사진을 선택해주세요.")
                    }
                } finally {
                    bitmap?.recycle()
                    if (requestGate.owns(request)) {
                        timeoutJob?.cancel()
                        if (_state.value is BodyAnalysisUiState.Error) {
                            deleteTemporaryPhoto(uri)
                            if (currentPhotoUri == uri) currentPhotoUri = null
                        }
                    } else if (uri != currentPhotoUri) deleteTemporaryPhoto(uri)
                    if (cleared && segmenterDelegate.isInitialized()) segmenter.close()
                }
            }
        }
        timeoutJob = viewModelScope.launch {
            delay(45_000)
            if (requestGate.isCurrent(request) && _state.value is BodyAnalysisUiState.Analyzing) {
                cancelRequest(request)
                _state.value = BodyAnalysisUiState.Error("분석 시간이 길어졌어요. 다른 사진으로 다시 시도해주세요.")
            }
        }
    }

    /** 예전에 찍은 체형 사진(앱 임시 폴더의 body_*.jpg)을 지운다. 지금 분석할 사진만 남긴다. */
    private fun cleanOldBodyPhotos(context: Context, keep: Uri) {
        val keepPath = keep.takeIf { it.scheme == "file" }?.path
        context.cacheDir.listFiles { f -> f.name.startsWith("body_") && f.name.endsWith(".jpg") }
            ?.forEach { if (it.absolutePath != keepPath && it.lastModified() < System.currentTimeMillis()-60*60*1000L) it.delete() }
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

    fun cancelRequest(requestId: Long) {
        if (requestGate.isCurrent(requestId) && _state.value is BodyAnalysisUiState.Analyzing) reset()
    }

    fun reset() {
        requestGate.invalidate()
        timeoutJob?.cancel()
        analysisJob?.cancel()
        currentPhotoUri?.let(::deleteTemporaryPhoto)
        currentPhotoUri = null
        _state.value = BodyAnalysisUiState.Idle
    }

    override fun onCleared() {
        cleared = true
        requestGate.invalidate()
        timeoutJob?.cancel()
        super.onCleared()
        analysisJob?.cancel()
        currentPhotoUri?.let(::deleteTemporaryPhoto)
        currentPhotoUri = null
        val job = analysisJob
        if (job != null) job.invokeOnCompletion { if (segmenterDelegate.isInitialized()) segmenter.close() }
        else if (segmenterDelegate.isInitialized()) segmenter.close()
    }

    /** 앱이 만든 캐시 사진만 삭제한다. 갤러리에서 선택한 URI에는 손대지 않는다. */
    private fun deleteTemporaryPhoto(uri: Uri) {
        val file = uri.takeIf { it.scheme == "file" }?.path?.let { java.io.File(it) } ?: return
        val cache = getApplication<Application>().cacheDir
        runCatching {
            if (file.parentFile?.canonicalPath == cache.canonicalPath &&
                file.name.startsWith("body_") && file.name.endsWith(".jpg")) file.delete()
        }
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

    private fun posePoints(pose: Pose): Map<Joint, GuidePoint> {
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
        return points
    }
    private fun calculateBodyType(pose: Pose, realWidths: BodyWidths, cm: BodyMeasurements, type: BodyType): BodyAnalysisResult {
        val shoulderWidth = realWidths.shoulder
        val waistWidth = realWidths.waist
        val hipWidth = realWidths.hip
        val maxWidth = maxOf(shoulderWidth, waistWidth, hipWidth)
        val shoulderRatio = shoulderWidth / maxWidth
        val waistRatio = waistWidth / maxWidth
        val hipRatio = hipWidth / maxWidth
        val shToHip = shoulderWidth / hipWidth
        // Both UI and cm use mask crown-to-soles height and the same hip-to-ankle segment.
        val legRatio = cm.legLengthCm?.div(cm.heightCm)
        val confidence = (pose.allPoseLandmarks.map { it.inFrameLikelihood }.average() * 100).toInt()
        val bodyTypeKo: String
        val characteristics: List<String>
        val styleAdvice: List<String>

        when {
            // 1) 어깨가 힙보다 뚜렷하게 넓다 → 역삼각형
            type == BodyType.INVERTED_TRIANGLE -> {
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
            type == BodyType.TRIANGLE -> {
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
            type == BodyType.HOURGLASS -> {
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
        val finalCharacteristics = if (legRatio != null && legRatio >= 0.52f)
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
            waistToHip = waistWidth / hipWidth,
            measurements = cm
        )
    }
}



