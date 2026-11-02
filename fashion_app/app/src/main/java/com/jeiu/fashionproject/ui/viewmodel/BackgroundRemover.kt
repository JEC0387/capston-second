package com.jeiu.fashionproject.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 옷 사진 누끼(배경 제거) + 옷장 사진 통일 — 폰 안에서 동작.
 *
 * 배경 지우기 순서 (앞 방법이 실패하면 다음 방법):
 *  ① ML Kit 피사체 분리(Subject Segmentation). 모델이 아직 폰에 없으면 내려받기를 요청하고 잠깐 기다렸다가 다시 시도
 *  ② 단색 배경 지우기 (ClosetPhotoRules.plainBackgroundMask) — 바닥 · 벽 · 침대처럼 고른 배경
 *  ③ (등록 화면) 서버 DeepFashion2 옷 윤곽 마스크로 "옷만" 오려내기 — cutGarment
 *
 * 사진 한 장으로 만드는 파일:
 *  - 통일 누끼 PNG (900×900 투명 캔버스, 옷 긴 변 78% 가운데, 옅은 그림자) → 앱 전용 폴더 (옷장에 보이는 사진)
 *  - 원본 복사본 JPG ("원본으로 저장" 선택용)                           → 앱 전용 폴더
 *  - 마스크 기준 PNG (자르기만 한 누끼 — 서버에 보낸 사진과 같은 비율)       → 캐시 폴더
 *  - 서버 전송용 JPG (투명 배경을 흰색으로 채움)                          → 캐시 폴더 (전송 후 삭제)
 * 갤러리 주소 대신 앱 폴더에 사진을 보관하므로 갤러리 권한이 끊겨도 사진이 유지된다.
 */
object BackgroundRemover {

    data class Result(
        val cutoutPath: String?,     // 통일 누끼 (배경 제거 실패 시 null)
        val originalPath: String,
        val uploadPath: String,      // 서버로 보낼 이미지 (누끼 실패 시 원본 사본과 같음)
        val maskBasePath: String,    // 서버 마스크를 씌울 기준 이미지 (자른 누끼, 실패 시 원본 사본)
        val cutoutBitmap: Bitmap?    // 색 추출용 (사용 후 recycle)
    )

    private const val MAX_SIDE = 1024
    /** 통일 처리한 사진 파일 이름 끝 — 이미 통일한 사진은 다시 처리하지 않는다 */
    const val STD_SUFFIX = "_std.png"

    /** 앱 전용 옷 사진 폴더. 옷을 지울 때 이 폴더 안의 파일만 함께 지운다. */
    fun wardrobeDir(context: Context): File = File(context.filesDir, "wardrobe").apply { mkdirs() }

    private fun segmenterOptions() = SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()

    /**
     * ML Kit 누끼 모델을 미리 내려받아 둔다 (Google Play 서비스 모듈). 옷 등록 화면을 열 때 부른다.
     * 이미 있으면 아무것도 하지 않고, 실패해도 등록은 예비 방법으로 계속된다.
     */
    suspend fun prepare(context: Context): Boolean = runCatching {
        val client = ModuleInstall.getClient(context.applicationContext)
        val segmenter = SubjectSegmentation.getClient(segmenterOptions())
        try {
            if (client.areModulesAvailable(segmenter).awaitTask().areModulesAvailable()) return@runCatching true
            client.installModules(ModuleInstallRequest.newBuilder().addApi(segmenter).build()).awaitTask()
            // 내려받기는 Play 서비스가 따로 진행 — 최대 25초 동안 준비됐는지 확인
            withTimeoutOrNull(25_000L) {
                while (!client.areModulesAvailable(segmenter).awaitTask().areModulesAvailable()) delay(1_000)
                true
            } ?: false
        } finally { segmenter.close() }
    }.getOrDefault(false)

    suspend fun process(context: Context, uri: Uri): Result = withContext(Dispatchers.Default) {
        val id = UUID.randomUUID().toString()
        val original = decode(context, uri)

        val originalFile = File(wardrobeDir(context), "${id}_orig.jpg")
        originalFile.outputStream().use { original.compress(Bitmap.CompressFormat.JPEG, 90, it) }

        val cutout = removeBackground(context, original)
        original.recycle()

        if (cutout == null) {
            return@withContext Result(null, originalFile.absolutePath, originalFile.absolutePath, originalFile.absolutePath, null)
        }

        // 마스크 기준(자르기만 한 누끼) — 서버에 보내는 사진과 같은 크기라 옷 윤곽 마스크가 정확히 겹친다
        val baseFile = File(context.cacheDir, "maskbase_$id.png")
        baseFile.outputStream().use { cutout.compress(Bitmap.CompressFormat.PNG, 100, it) }

        // 옷장에 보일 통일 사진
        val stdFile = File(wardrobeDir(context), "${id}_cut$STD_SUFFIX")
        val std = standardize(cutout)
        stdFile.outputStream().use { std.compress(Bitmap.CompressFormat.PNG, 100, it) }
        std.recycle()

        // 서버 모델은 투명 배경을 학습하지 않았으므로 흰 배경으로 채워서 보낸다.
        val uploadFile = File(context.cacheDir, "upload_$id.jpg")
        val white = Bitmap.createBitmap(cutout.width, cutout.height, Bitmap.Config.ARGB_8888)
        Canvas(white).apply {
            drawColor(android.graphics.Color.WHITE)
            drawBitmap(cutout, 0f, 0f, null)
        }
        uploadFile.outputStream().use { white.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        white.recycle()

        Result(stdFile.absolutePath, originalFile.absolutePath, uploadFile.absolutePath, baseFile.absolutePath, cutout)
    }

    /** ① ML Kit → (모델 내려받기 후 한 번 더) → ② 단색 배경. 모두 실패하면 null. 결과는 옷 영역에 맞춰 자른 투명 PNG */
    private suspend fun removeBackground(context: Context, original: Bitmap): Bitmap? {
        runCatching { segment(original) }.getOrNull()?.let { fg -> cropToSubject(fg)?.let { return it } }
        if (prepare(context)) {
            runCatching { segment(original) }.getOrNull()?.let { fg -> cropToSubject(fg)?.let { return it } }
        }
        return runCatching { plainBackgroundCut(original) }.getOrNull()?.let { cropToSubject(it) }
    }

    /** ② 단색 배경 지우기 — 경계는 1px 부드럽게 */
    private fun plainBackgroundCut(src: Bitmap): Bitmap? {
        val w = src.width; val h = src.height
        val px = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
        val bg = ClosetPhotoRules.plainBackgroundMask(px, w, h) ?: return null
        val out = IntArray(w * h)
        for (i in px.indices) {
            if (bg[i]) { out[i] = 0; continue }
            val x = i % w; val y = i / w
            val edge = (x > 0 && bg[i - 1]) || (x < w - 1 && bg[i + 1]) || (y > 0 && bg[i - w]) || (y < h - 1 && bg[i + w])
            val alpha = if (edge) 0x99 else 0xFF
            out[i] = (alpha shl 24) or (px[i] and 0x00FFFFFF)
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * 통일 사진 — 900×900 투명 캔버스 가운데에 옷을 같은 비율로 놓고, 아래에 옅은 그림자를 깐다.
     * 모든 옷이 같은 크기 · 같은 위치 · 같은 그림자로 보여 옷장이 한 쇼핑몰 상품 사진처럼 정돈된다.
     */
    fun standardize(cutout: Bitmap): Bitmap {
        val size = ClosetPhotoRules.CANVAS
        val place = ClosetPhotoRules.layout(cutout.width, cutout.height)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val dst = RectF(place.left, place.top, place.left + place.width, place.top + place.height)
        // 그림자: 옷 모양을 흐리게 한 실루엣을 살짝 아래에
        runCatching {
            val blur = Paint().apply { maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL) }
            val offset = IntArray(2)
            val alpha = cutout.extractAlpha(blur, offset)
            val sx = place.width / cutout.width; val sy = place.height / cutout.height
            val shadowDst = RectF(
                dst.left + offset[0] * sx, dst.top + offset[1] * sy + size * 0.018f,
                dst.left + (offset[0] + alpha.width) * sx, dst.top + (offset[1] + alpha.height) * sy + size * 0.018f
            )
            canvas.drawBitmap(alpha, null, shadowDst, Paint(Paint.FILTER_BITMAP_FLAG).apply { color = 0x2E000000 })
            alpha.recycle()
        }
        canvas.drawBitmap(cutout, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    /** 이미 저장된 누끼 사진(파일)을 통일 사진으로 다시 만든다. 이미 통일했거나 읽지 못하면 null */
    suspend fun restandardize(context: Context, uri: String): String? = withContext(Dispatchers.Default) {
        if (uri.endsWith(STD_SUFFIX)) return@withContext null
        val bitmap = runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri))!!.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()?.copy(Bitmap.Config.ARGB_8888, false) ?: return@withContext null
        val cropped = cropToSubject(bitmap) ?: return@withContext null
        saveStd(context, cropped)
    }

    /** 배경이 남은 원본 사진에서 배경을 지워 통일 사진을 만든다. 실패하면 null */
    suspend fun cutoutFromOriginal(context: Context, uri: String): String? = withContext(Dispatchers.Default) {
        val original = runCatching { decode(context, Uri.parse(uri)) }.getOrNull() ?: return@withContext null
        val cut = removeBackground(context, original)
        original.recycle()
        cut?.let { saveStd(context, it) }
    }

    private fun saveStd(context: Context, cropped: Bitmap): String {
        val std = standardize(cropped)
        cropped.recycle()
        val file = File(wardrobeDir(context), "${UUID.randomUUID()}_cut$STD_SUFFIX")
        file.outputStream().use { std.compress(Bitmap.CompressFormat.PNG, 100, it) }
        std.recycle()
        return Uri.fromFile(file).toString()
    }

    data class GarmentCut(
        val path: String,
        val bitmap: Bitmap,          // 색 추출용 (사용 후 recycle)
        val coverage: Float          // 옷 영역 / 원래 누끼 영역 — 작을수록 사람·다른 옷이 많이 섞여 있었다는 뜻
    )

    /**
     * 옷만 오려내기 — 서버(DeepFashion2)가 준 옷 윤곽 마스크로 사람 몸·다른 옷을 지운다.
     * [basePath]는 서버에 보낸 사진과 같은 비율의 이미지(자른 누끼, 누끼 실패 시 원본 사본)이고,
     * 마스크는 서버에서 줄인 크기이므로 base 크기에 맞춰 늘려서 쓴다. 결과는 통일 사진으로 저장한다.
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
            val std = standardize(cropped)
            val file = File(wardrobeDir(context), "${UUID.randomUUID()}_garment$STD_SUFFIX")
            file.outputStream().use { std.compress(Bitmap.CompressFormat.PNG, 100, it) }
            std.recycle()
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
        val segmenter = SubjectSegmentation.getClient(segmenterOptions())
        segmenter.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val fg = result.foregroundBitmap
                if (fg != null) cont.resume(fg)
                else cont.resumeWithException(IllegalStateException("피사체를 찾지 못했습니다"))
            }
            .addOnFailureListener { cont.resumeWithException(it) }
            .addOnCompleteListener { segmenter.close() }
    }

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
    }

    /**
     * 옷(불투명 픽셀)이 있는 영역만 잘라내고 사방에 약간의 여백을 둔다.
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
