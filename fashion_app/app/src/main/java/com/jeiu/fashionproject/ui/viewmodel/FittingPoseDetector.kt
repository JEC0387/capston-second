package com.jeiu.fashionproject.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.ui.geometry.Offset
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * 가상 피팅용 몸 위치 찾기 — 전신 사진에서 어깨·골반·발목 위치를 찾는다. (ML Kit 포즈 인식, 폰 안에서 동작)
 * 좌표는 사진 픽셀 기준이며, 사진 크기(imageWidth/Height)와 함께 돌려준다.
 * 부위가 안 보이면 그 부위는 null.
 */
object FittingPoseDetector {

    data class BodyPoints(
        val imageWidth: Int,
        val imageHeight: Int,
        val shoulders: Pair<Offset, Offset>?,   // 화면에서 왼쪽 점, 오른쪽 점 순서
        val hips: Pair<Offset, Offset>?,
        val ankles: Pair<Offset, Offset>?
    ) {
        val found: Boolean get() = shoulders != null || hips != null
    }

    private const val MAX_SIDE = 1280
    private const val MIN_LIKELIHOOD = 0.5f

    /** 찾지 못하거나 사진을 읽지 못하면 null */
    suspend fun detect(context: Context, uri: Uri): BodyPoints? = withContext(Dispatchers.Default) {
        val bitmap = runCatching { decode(context, uri) }.getOrNull() ?: return@withContext null
        val detector = PoseDetection.getClient(
            AccuratePoseDetectorOptions.Builder()
                .setDetectorMode(AccuratePoseDetectorOptions.SINGLE_IMAGE_MODE)
                .build()
        )
        try {
            val pose = suspendCancellableCoroutine<Pose?> { cont ->
                detector.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            } ?: return@withContext null

            fun pair(a: Int, b: Int): Pair<Offset, Offset>? {
                val p = pose.getPoseLandmark(a) ?: return null
                val q = pose.getPoseLandmark(b) ?: return null
                if (p.inFrameLikelihood < MIN_LIKELIHOOD || q.inFrameLikelihood < MIN_LIKELIHOOD) return null
                val o1 = Offset(p.position.x, p.position.y)
                val o2 = Offset(q.position.x, q.position.y)
                return if (o1.x <= o2.x) o1 to o2 else o2 to o1
            }
            BodyPoints(
                imageWidth = bitmap.width,
                imageHeight = bitmap.height,
                shoulders = pair(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER),
                hips = pair(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP),
                ankles = pair(PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE)
            ).takeIf { it.found }
        } finally {
            detector.close()
            bitmap.recycle()
        }
    }

    /** 사진 회전(EXIF)을 반영해 읽고 긴 변을 1280px로 줄인다 — 화면에 보이는 사진과 비율이 같다. */
    private fun decode(context: Context, uri: Uri): Bitmap =
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val s = MAX_SIDE.toFloat() / maxOf(info.size.width, info.size.height)
                if (s < 1f) d.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
            }.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
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
