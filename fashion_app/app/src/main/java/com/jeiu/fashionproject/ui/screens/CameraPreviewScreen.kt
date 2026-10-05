package com.jeiu.fashionproject.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import com.jeiu.fashionproject.ui.camera.*
import kotlinx.coroutines.delay
import java.io.File

@androidx.annotation.OptIn(ExperimentalGetImage::class, androidx.camera.view.TransformExperimental::class)
@Composable
fun CameraPreviewScreen(onPhotoCaptured: (Uri) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val onPhoto by rememberUpdatedState(onPhotoCaptured)
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val gate = remember { AutoCaptureGate() }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var captureAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var dismissAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var guide by remember { mutableStateOf(GuideStatus.NO_PERSON) }
    var progress by remember { mutableStateOf(0f) }
    var capturing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastResult by remember { mutableLongStateOf(0L) }
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    // A frozen/very slow stream cannot leave a ready indicator on screen.
    LaunchedEffect(Unit) {
        while (true) {
            delay(200)
            if (!capturing && SystemClock.elapsedRealtime() - lastResult > 700) {
                gate.reset()
                progress = 0f
                guide = GuideStatus.NO_PERSON
            }
        }
    }
    // Also observe resume while permission is denied (e.g. returning from app settings).
    DisposableEffect(lifecycleOwner, context) {
        val permissionObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(permissionObserver)
        onDispose { lifecycleOwner.lifecycle.removeObserver(permissionObserver) }
    }
    DisposableEffect(hasPermission, lifecycleOwner, previewSize) {
        if (!hasPermission || previewSize.width == 0 || previewSize.height == 0) {
            onDispose { }
        } else {
            var disposed = false
            var processing = false
            var epoch = 0
            var provider: ProcessCameraProvider? = null
            var pendingPhoto: Uri? = null
            var resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            val rotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0
            val preview = Preview.Builder().setTargetRotation(rotation).build()
            val capture = ImageCapture.Builder().setTargetRotation(rotation)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
            val analysis = ImageAnalysis.Builder().setTargetRotation(rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            val detector = PoseDetection.getClient(AccuratePoseDetectorOptions.Builder()
                .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE).build())
            val transformFactory = ImageProxyTransformFactory().apply {
                isUsingRotationDegrees = true
                isUsingCropRect = false // ML Kit returns coordinates in the full rotated image.
            }
            gate.retry()
            capturing = false
            error = null
            fun resetGuide() { gate.reset(); progress = 0f; guide = GuideStatus.NO_PERSON }
            fun failedCapture(file: File?) {
                file?.delete()
                if (!disposed) {
                    capturing = false
                    gate.retry()
                    error = "촬영하지 못했어요. 수동 촬영 버튼으로 다시 시도해주세요."
                }
            }
            fun takePhoto() {
                if (disposed || !resumed || !gate.tryLock()) return
                capturing = true
                progress = 0f
                var file: File? = null
                try {
                    val outputFile = File.createTempFile("body_", ".jpg", context.cacheDir)
                    file = outputFile
                    capture.takePicture(ImageCapture.OutputFileOptions.Builder(outputFile).build(), mainExecutor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                if (disposed) { outputFile.delete(); return }
                                val uri = Uri.fromFile(outputFile)
                                if (resumed) onPhoto(uri) else pendingPhoto = uri
                                // Keep gate locked until this screen is disposed.
                            }
                            override fun onError(exception: ImageCaptureException) { failedCapture(outputFile) }
                        })
                } catch (_: Exception) { failedCapture(file) }
            }
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    resumed = true
                    hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    pendingPhoto?.let { pendingPhoto = null; onPhoto(it) }
                } else if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                    resumed = false
                    epoch++
                    resetGuide()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            dismissAction = { disposed = true; resetGuide(); onDismiss() }
            analysis.setAnalyzer(mainExecutor) { proxy ->
                if (disposed || !resumed || gate.locked) {
                    proxy.close()
                } else {
                    val mediaImage = proxy.image
                    if (mediaImage == null) {
                        resetGuide()
                        proxy.close()
                    } else {
                        val frameEpoch = epoch
                        val startedAt = SystemClock.elapsedRealtime()
                        processing = true
                        try {
                            val source = transformFactory.getOutputTransform(proxy)
                            detector.process(InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees))
                                .addOnSuccessListener(mainExecutor) { pose ->
                                    if (!disposed && resumed && !gate.locked && frameEpoch == epoch) {
                                        val now = SystemClock.elapsedRealtime()
                                        val target = previewView.outputTransform
                                        if (target == null || now - startedAt > 700) {
                                            resetGuide()
                                        } else {
                                            val result = runCatching {
                                                val transform = CoordinateTransform(source, target)
                                                val points = jointTypes.mapNotNull { (joint, type) ->
                                                    pose.getPoseLandmark(type)?.let { landmark ->
                                                        val xy = floatArrayOf(landmark.position.x, landmark.position.y)
                                                        transform.mapPoints(xy)
                                                        joint to GuidePoint(xy[0], xy[1], landmark.inFrameLikelihood)
                                                    }
                                                }.toMap()
                                                CaptureGuide.evaluate(points, previewView.width.toFloat(), previewView.height.toFloat())
                                            }.getOrElse { GuideStatus.FULL_BODY }
                                            guide = result
                                            lastResult = now
                                            val shouldCapture = gate.update(result == GuideStatus.READY && error == null, now)
                                            progress = gate.progress
                                            if (shouldCapture) takePhoto()
                                        }
                                    }
                                }
                                .addOnFailureListener(mainExecutor) {
                                    if (!disposed && frameEpoch == epoch) {
                                        resetGuide()
                                        error = "자세 인식에 실패했어요. 수동 촬영하거나 카메라를 다시 열어주세요."
                                    }
                                }
                                .addOnCompleteListener(mainExecutor) {
                                    proxy.close()
                                    processing = false
                                    if (disposed) detector.close()
                                }
                        } catch (_: Exception) {
                            proxy.close()
                            processing = false
                            if (!disposed) {
                                resetGuide()
                                error = "자세 인식을 시작하지 못했어요. 수동 촬영을 이용해주세요."
                            }
                        }
                    }
                }
            }
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                previewView.doOnLayout {
                    if (!disposed) {
                        try {
                            val cameraProvider = future.get()
                            provider = cameraProvider
                            preview.setSurfaceProvider(previewView.surfaceProvider)
                            val viewport = previewView.viewPort ?: throw IllegalStateException("Preview is not laid out")
                            val group = UseCaseGroup.Builder().setViewPort(viewport)
                                .addUseCase(preview).addUseCase(capture).addUseCase(analysis).build()
                            try {
                                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, group)
                            } catch (_: Exception) {
                                // Some devices cannot run three use cases together; retain manual capture.
                                cameraProvider.unbind(preview, capture, analysis)
                                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                                    UseCaseGroup.Builder().setViewPort(viewport).addUseCase(preview).addUseCase(capture).build())
                                error = "이 기기에서는 실시간 가이드를 사용할 수 없어요. 수동 촬영을 이용해주세요."
                            }
                            captureAction = { error = null; takePhoto() }
                        } catch (_: Exception) {
                            error = "카메라를 열지 못했어요. 권한을 확인한 뒤 다시 열어주세요."
                        }
                    }
                }
            }, mainExecutor)
            onDispose {
                disposed = true
                epoch++
                captureAction = null
                dismissAction = null
                lifecycleOwner.lifecycle.removeObserver(observer)
                analysis.clearAnalyzer()
                provider?.unbind(preview, capture, analysis)
                if (!processing) detector.close()
                pendingPhoto?.path?.let { File(it).delete() }
                gate.reset()
            }
        }
    }
    val dismiss = { dismissAction?.invoke() ?: onDismiss() }
    BackHandler(onBack = dismiss)
    Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize().onSizeChanged { previewSize = it })
                Canvas(Modifier.fillMaxSize()) {
                    val color = if (guide == GuideStatus.READY && error == null) Color.Green else Color.White
                    drawRoundRect(color, Offset(size.width * CaptureGuide.LEFT, size.height * CaptureGuide.TOP),
                        Size(size.width * (CaptureGuide.RIGHT - CaptureGuide.LEFT), size.height * (CaptureGuide.BOTTOM - CaptureGuide.TOP)),
                        CornerRadius(24.dp.toPx()), style = Stroke(3.dp.toPx()))
                    drawLine(color.copy(alpha = 0.35f), Offset(size.width / 2, size.height * 0.1f),
                        Offset(size.width / 2, size.height * 0.9f), strokeWidth = 1.dp.toPx())
                }
                IconButton(onClick = dismiss, modifier = Modifier.align(Alignment.TopStart)) {
                    Icon(Icons.Default.Close, contentDescription = "닫기", tint = Color.White)
                }
            }
            Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(when {
                    !hasPermission -> "카메라 권한이 필요해요. 설정에서 권한을 허용해주세요."
                    capturing -> "촬영 중이에요…"
                    error != null -> error!!
                    captureAction == null -> "카메라 준비 중이에요…"
                    else -> guide.message
                }, modifier = Modifier.height(48.dp), color = Color.White, textAlign = TextAlign.Center)
                Text("머리와 발을 테두리 안에 맞추고 1.5초간 유지해주세요", color = Color.LightGray,
                    modifier = Modifier.height(36.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                Button(onClick = { captureAction?.invoke() }, enabled = hasPermission && captureAction != null && !capturing) {
                    Text("수동 촬영")
                }
                if (!hasPermission) TextButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("카메라 권한 허용")
                }
            }
        }
    }
}

private val jointTypes = mapOf(
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



