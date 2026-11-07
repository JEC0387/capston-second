package com.jeiu.fashionproject.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 가상 피팅용 몸 위치 찾기 — 전신 사진에서 어깨·골반·발목 위치를 찾는다. (ML Kit 포즈 인식, 폰 안에서 동작)
 * 좌표는 사진 픽셀 기준이며, 사진 크기(imageWidth/Height)와 함께 돌려준다.
 * 부위가 안 보이면 그 부위는 null.
 */
object FittingPoseDetector {

    /** Anatomical identity remains intact even when feet/arms cross in image space. */
    data class SidePoints(
        val shoulder: Offset? = null, val elbow: Offset? = null, val wrist: Offset? = null,
        val hip: Offset? = null, val knee: Offset? = null, val ankle: Offset? = null,
        val heel: Offset? = null, val toe: Offset? = null
    )

    data class BodyPoints(
        val imageWidth: Int,
        val imageHeight: Int,
        val shoulders: Pair<Offset, Offset>?,   // 화면에서 왼쪽 점, 오른쪽 점 순서
        val hips: Pair<Offset, Offset>?,
        val ankles: Pair<Offset, Offset>?,
        val left: SidePoints? = null,
        val right: SidePoints? = null,
        val faceCenter: Offset? = null,
        val faceWidth: Float? = null
    ) {
        val found: Boolean get() = shoulders != null || hips != null
    }

    private const val MAX_SIDE = 1280
    private const val MIN_LIKELIHOOD = 0.5f
    private val directExecutor = Executor { it.run() }

    internal data class LandmarkSample(val position: Offset, val likelihood: Float)

    /** Confidence is visibility evidence, not a guarantee of anatomical or measurement accuracy. */
    internal fun validPoint(sample: LandmarkSample?, width: Int, height: Int): Offset? =
        sample?.takeIf { width > 0 && height > 0 && it.likelihood.isFinite() &&
            it.likelihood in MIN_LIKELIHOOD..1f && it.position.x.isFinite() && it.position.y.isFinite() &&
            it.position.x >= 0 && it.position.x < width && it.position.y >= 0 && it.position.y < height }?.position

    internal fun fromSamples(width: Int, height: Int, samples: Map<Int, LandmarkSample>): BodyPoints? {
        if (width <= 0 || height <= 0) return null
        fun point(type: Int) = validPoint(samples[type], width, height)
        fun pair(a: Int, b: Int): Pair<Offset, Offset>? {
            val p = point(a) ?: return null; val q = point(b) ?: return null
            if ((p-q).getDistance() <= .001f) return null
            return if (p.x <= q.x) p to q else q to p
        }
        fun side(left: Boolean): SidePoints {
            fun choose(a: Int, b: Int) = point(if (left) a else b)
            return SidePoints(
                choose(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER),
                choose(PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW),
                choose(PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST),
                choose(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP),
                choose(PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE),
                choose(PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE),
                choose(PoseLandmark.LEFT_HEEL, PoseLandmark.RIGHT_HEEL),
                choose(PoseLandmark.LEFT_FOOT_INDEX, PoseLandmark.RIGHT_FOOT_INDEX))
        }
        val ears = pair(PoseLandmark.LEFT_EAR, PoseLandmark.RIGHT_EAR)
        val faceWidth = ears?.let { (it.second-it.first).getDistance() }?.takeIf { it >= 2f && it <= width * .5f }
        return BodyPoints(width, height,
            pair(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER),
            pair(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP),
            pair(PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE), side(true), side(false),
            point(PoseLandmark.NOSE), faceWidth).takeIf { it.found }
    }

    /** A cancelled coroutine must not recycle a bitmap still owned by an ML Kit task.
     * Wait for terminal native completion, suppress publication after cancellation, then release.
     * No retry or synthetic pose is substituted for failure.
     */
    internal suspend fun <T> awaitOwnedNative(start: () -> Task<T>, release: () -> Unit): T? {
        try {
            coroutineContext.ensureActive()
            val task = try { start() } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { return null }
            val value = suspendCoroutine<T?> { continuation ->
                task.addOnCompleteListener(directExecutor) { completed ->
                    continuation.resume(if (completed.isSuccessful) completed.result else null)
                }
            }
            coroutineContext.ensureActive()
            return value
        } finally { release() }
    }

    /** 찾지 못하거나 사진을 읽지 못하면 null */
    suspend fun detect(context: Context, uri: Uri): BodyPoints? = withContext(Dispatchers.Default) {
        val bitmap = try { decode(context, uri) } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { return@withContext null }
        val width = bitmap.width; val height = bitmap.height
        val detector = try { PoseDetection.getClient(
            AccuratePoseDetectorOptions.Builder()
                .setDetectorMode(AccuratePoseDetectorOptions.SINGLE_IMAGE_MODE)
                .build()
        ) } catch (exception: Exception) { bitmap.recycle(); throw exception }
        val pose = awaitOwnedNative({ detector.process(InputImage.fromBitmap(bitmap, 0)) },
            { try { detector.close() } finally { bitmap.recycle() } }) ?: return@withContext null
        fromSamples(width, height, pose.allPoseLandmarks.associate {
            it.landmarkType to LandmarkSample(Offset(it.position.x, it.position.y), it.inFrameLikelihood)
        })
    }

    /** 사진 회전(EXIF)을 반영해 읽고 긴 변을 1280px로 줄인다 — 화면에 보이는 사진과 비율이 같다. */
    internal fun decode(context: Context, uri: Uri): Bitmap {
        val raw = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val s = MAX_SIDE.toFloat() / maxOf(info.size.width, info.size.height)
                if (s < 1f) d.setTargetSize((info.size.width * s).toInt().coerceAtLeast(1), (info.size.height * s).toInt().coerceAtLeast(1))
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth > 0 && bounds.outHeight > 0)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
            val decoded = context.contentResolver.openInputStream(uri)!!.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: error("Cannot decode fitting image")
            try {
                val orientation = context.contentResolver.openInputStream(uri)!!.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                }
                val matrix = Matrix().apply { when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
                } }
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            } catch (exception: Exception) { decoded.recycle(); throw exception }
        }
        if (raw.config == Bitmap.Config.ARGB_8888) return raw
        return try { requireNotNull(raw.copy(Bitmap.Config.ARGB_8888, false)) } finally { raw.recycle() }
    }

    /** 옷 사진의 가로·세로 크기 (사진을 다 읽지 않고 크기만) */
    suspend fun imageSize(context: Context, uri: String): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        runCatching {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(Uri.parse(uri))!!.use { BitmapFactory.decodeStream(it, null, opts) }
            if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null
        }.getOrNull()
    }
}
