package com.jeiu.fashionproject.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 옷 사진 누끼(배경 제거) — ML Kit 피사체 분리(Subject Segmentation), 폰 안에서 동작.
 *
 * 사진 한 장으로 세 가지 파일을 만든다.
 *  - 누끼 PNG (배경 투명, 옷 영역만 잘라 가운데 정렬) → 앱 전용 폴더
 *  - 원본 복사본 JPG ("원본으로 저장" 선택용)        → 앱 전용 폴더
 *  - 서버 전송용 JPG (투명 배경을 흰색으로 채움)      → 캐시 폴더 (전송 후 삭제)
 * 갤러리 주소 대신 앱 폴더에 사진을 보관하므로 갤러리 권한이 끊겨도 사진이 유지된다.
 */
object BackgroundRemover {

    data class Result(
        val cutoutPath: String?,     // 누끼 실패 시 null
        val originalPath: String,
        val uploadPath: String,      // 서버로 보낼 이미지 (누끼 실패 시 원본 사본과 같음)
        val cutoutBitmap: Bitmap?    // 색 추출용 (사용 후 recycle)
    )

    private const val MAX_SIDE = 1024

    /** 앱 전용 옷 사진 폴더. 옷을 지울 때 이 폴더 안의 파일만 함께 지운다. */
    fun wardrobeDir(context: Context): File = File(context.filesDir, "wardrobe").apply { mkdirs() }

    suspend fun process(context: Context, uri: Uri): Result = withContext(Dispatchers.Default) {
        val id = UUID.randomUUID().toString()
        val original = decode(context, uri)

        val originalFile = File(wardrobeDir(context), "${id}_orig.jpg")
        originalFile.outputStream().use { original.compress(Bitmap.CompressFormat.JPEG, 90, it) }

        val cutout = runCatching { segment(original) }.getOrNull()?.let { cropToSubject(it) }
        original.recycle()

        if (cutout == null) {
            return@withContext Result(null, originalFile.absolutePath, originalFile.absolutePath, null)
        }

        val cutoutFile = File(wardrobeDir(context), "${id}_cut.png")
        cutoutFile.outputStream().use { cutout.compress(Bitmap.CompressFormat.PNG, 100, it) }

        // 서버 모델은 투명 배경을 학습하지 않았으므로 흰 배경으로 채워서 보낸다.
        val uploadFile = File(context.cacheDir, "upload_$id.jpg")
        val white = Bitmap.createBitmap(cutout.width, cutout.height, Bitmap.Config.ARGB_8888)
        Canvas(white).apply {
            drawColor(android.graphics.Color.WHITE)
            drawBitmap(cutout, 0f, 0f, null)
        }
        uploadFile.outputStream().use { white.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        white.recycle()

        Result(cutoutFile.absolutePath, originalFile.absolutePath, uploadFile.absolutePath, cutout)
    }

    data class GarmentCut(
        val path: String,
        val bitmap: Bitmap,          // 색 추출용 (사용 후 recycle)
        val coverage: Float          // 옷 영역 / 원래 누끼 영역 — 작을수록 사람·다른 옷이 많이 섞여 있었다는 뜻
    )

    /**
     * 옷만 오려내기 — 서버(DeepFashion2)가 준 옷 윤곽 마스크로 사람 몸·다른 옷을 지운다.
     * [basePath]는 서버에 보낸 사진과 같은 비율의 이미지(누끼 PNG, 누끼 실패 시 원본 사본)이고,
     * 마스크는 서버에서 줄인 크기이므로 base 크기에 맞춰 늘려서 쓴다.
     */
    suspend fun cutGarment(context: Context, basePath: String, maskPngBase64: String): GarmentCut? =
        withContext(Dispatchers.Default) {
            val base = BitmapFactory.decodeFile(basePath)?.copy(Bitmap.Config.ARGB_8888, true)
                ?: return@withContext null
            val maskBytes = android.util.Base64.decode(maskPngBase64, android.util.Base64.DEFAULT)
            val maskRaw = BitmapFactory.decodeByteArray(maskBytes, 0, maskBytes.size)
                ?: return@withContext null.also { base.recycle() }
            val mask = Bitmap.createScaledBitmap(maskRaw, base.width, base.height, true)
                .also { if (it != maskRaw) maskRaw.recycle() }

            val w = base.width; val h = base.height
            val px = IntArray(w * h).also { base.getPixels(it, 0, w, 0, 0, w, h) }
            val mk = IntArray(w * h).also { mask.getPixels(it, 0, w, 0, 0, w, h) }
            mask.recycle()
            var before = 0; var after = 0
            for (i in px.indices) {
                val alpha = px[i] ushr 24
                if (alpha > 32) before++
                val keep = mk[i] and 0xFF   // 흑백 마스크: 흰색(255) = 옷
                val newAlpha = alpha * keep / 255
                if (newAlpha > 32) after++
                px[i] = (newAlpha shl 24) or (px[i] and 0x00FFFFFF)
            }
            base.setPixels(px, 0, w, 0, 0, w, h)

            val cropped = cropToSubject(base) ?: return@withContext null
            val file = File(wardrobeDir(context), "${UUID.randomUUID()}_garment.png")
            file.outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
            GarmentCut(file.absolutePath, cropped, if (before > 0) after.toFloat() / before else 1f)
        }

    // ── 사진 읽기 (긴 변 1024px로 축소) ──────────────────────────────────────
    private fun decode(context: Context, uri: Uri): Bitmap =
        if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val (w, h) = info.size.width to info.size.height
                val scale = MAX_SIDE.toFloat() / maxOf(w, h)
                if (scale < 1f) decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
            }.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            val raw = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
            val scale = MAX_SIDE.toFloat() / maxOf(raw.width, raw.height)
            if (scale < 1f) {
                Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt(), (raw.height * scale).toInt(), true)
                    .also { if (it != raw) raw.recycle() }
            } else raw
        }

    // ── ML Kit 피사체 분리 → 배경이 투명한 비트맵 ─────────────────────────────
    private suspend fun segment(bitmap: Bitmap): Bitmap = suspendCancellableCoroutine { cont ->
        val segmenter = SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
        )
        segmenter.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val fg = result.foregroundBitmap
                if (fg != null) cont.resume(fg)
                else cont.resumeWithException(IllegalStateException("피사체를 찾지 못했습니다"))
            }
            .addOnFailureListener { cont.resumeWithException(it) }
            .addOnCompleteListener { segmenter.close() }
    }

    /**
     * 옷(불투명 픽셀)이 있는 영역만 잘라내고 사방에 약간의 여백을 둔다.
     * 옷장 칸에서 모든 옷이 같은 크기·가운데 정렬로 보이게 하기 위함.
     * 남은 영역이 너무 작으면 누끼 실패로 본다.
     */
    private fun cropToSubject(fg: Bitmap): Bitmap? {
        val w = fg.width; val h = fg.height
        val pixels = IntArray(w * h).also { fg.getPixels(it, 0, w, 0, 0, w, h) }
        var left = w; var top = h; var right = -1; var bottom = -1
        for (y in 0 until h) for (x in 0 until w) {
            if ((pixels[y * w + x] ushr 24) > 32) {
                if (x < left) left = x; if (x > right) right = x
                if (y < top) top = y; if (y > bottom) bottom = y
            }
        }
        if (right < 0 || (right - left + 1) * (bottom - top + 1) < w * h * 0.02) {
            fg.recycle(); return null
        }
        val pad = (maxOf(right - left, bottom - top) * 0.05f).toInt()
        val x0 = (left - pad).coerceAtLeast(0); val y0 = (top - pad).coerceAtLeast(0)
        val x1 = (right + pad).coerceAtMost(w - 1); val y1 = (bottom + pad).coerceAtMost(h - 1)
        return Bitmap.createBitmap(fg, x0, y0, x1 - x0 + 1, y1 - y0 + 1).also { if (it != fg) fg.recycle() }
    }
}
