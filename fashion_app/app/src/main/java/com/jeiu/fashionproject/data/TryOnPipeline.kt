package com.jeiu.fashionproject.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/**
 * 팀의 이전 전처리 연구 코드 보존. 직접 실행은 차단하며 아래 단계의 품질을 보증하지 않는다.
 *
 *  ① Pose Estimation (폰, ML Kit 포즈 33점) — 전신 사진에서 어깨 · 골반 · 발목 · 얼굴 위치를 찾는다.
 *     사람이 없으면 유료 요청을 보내지 않고, 사람이 작게 나온 사진은 전신이 2:3 세로로 꽉 차게 자른다 (TryOnFraming).
 *  ② Human Parsing  (FASHN 모델 안) — 몸을 얼굴 · 머리 · 팔 · 상의 · 하의 영역으로 나눠 바꿀 옷 영역을 정한다.
 *  ③ Garment Warping (FASHN 모델 안) — 옷 사진을 체형 · 자세에 맞게 휘고 늘린다.
 *     폰에서는 옷장 누끼 사진의 그림자를 지우고 흰 바탕에 옷만 크게 놓아 "flat-lay" 사진으로 보낸다.
 *  ④ Diffusion       (FASHN tryon-v1.6, quality 모드) — 주름 · 그림자 · 질감을 그려 실제로 입은 사진을 만든다.
 *  ⑤ Image Blending  (폰) — 결과에 원본 얼굴을 부드럽게 다시 합쳐, 얼굴이 바뀌어 보이는 이질감을 없앤다.
 */
@Deprecated("Use the authenticated, consent-bound TryOnViewModel or ServerOutfitVtonViewModel")
object TryOnPipeline {

    data class Person(
        val dataUri: String, val file: File, val width: Int, val height: Int,
        val face: TryOnFraming.Oval?, val fullBody: Boolean
    )

    /** @param image 결과 사진 (합성했으면 폰 캐시 file://, 아니면 FASHN 결과 https 주소) */
    data class Outcome(val image: String, val blended: Boolean, val note: String?)

    private const val PERSON_MAX = 1296      // FASHN 권장 최대 높이
    private const val GARMENT_MAX = 1024

    suspend fun run(
        context: Context, personUri: Uri, plan: TryOnPlan, seed: Int, progress: (String) -> Unit
    ): Outcome {
        // The legacy entry has no consent, durable request ID or usage reservation.
        // Screens use AiTryOnScreen/TryOnViewModel and ServerOutfitVtonViewModel instead.
        // Fail before opening the photograph; never reintroduce a direct paid API call.
        throw TryOnFailure("인증·전송 동의·요청 보호가 있는 AI 피팅 화면에서 시작해주세요.")
    }
    // ── ① 사람 사진 ─────────────────────────────────────────────────────────

    private suspend fun preparePerson(context: Context, uri: Uri): Person = withContext(Dispatchers.Default) {
        val src = decodeOriented(context, uri, 2048)
        val pose = detectPose(src)
        fun lm(type: Int) = pose?.getPoseLandmark(type)?.takeIf { it.inFrameLikelihood >= 0.5f }
            ?.position?.let { TryOnFraming.P(it.x, it.y) }
        val points = pose?.allPoseLandmarks.orEmpty().filter { it.inFrameLikelihood >= 0.5f }
            .map { TryOnFraming.P(it.position.x, it.position.y) }
        if (points.size < 6) {
            src.recycle()
            throw TryOnFailure("사진에서 사람을 찾지 못했어요. 머리부터 발끝까지 나온 정면 사진을 골라 주세요.")
        }
        val nose = lm(PoseLandmark.NOSE)
        val ls = lm(PoseLandmark.LEFT_SHOULDER); val rs = lm(PoseLandmark.RIGHT_SHOULDER)
        val feet = listOfNotNull(lm(PoseLandmark.LEFT_ANKLE), lm(PoseLandmark.RIGHT_ANKLE),
            lm(PoseLandmark.LEFT_HEEL), lm(PoseLandmark.RIGHT_HEEL))
        val shoulderMidY = if (ls != null && rs != null) (ls.y + rs.y) / 2 else null
        val shoulderW = if (ls != null && rs != null) kotlin.math.hypot(ls.x - rs.x, ls.y - rs.y) else null
        val fullBody = nose != null && ls != null && rs != null && feet.isNotEmpty()

        // 전신이 2:3 세로로 꽉 차게 자르기
        val crop = TryOnFraming.bodyBox(points, nose, shoulderMidY)?.let { TryOnFraming.crop(src.width, src.height, it) }
        val framed = crop?.let {
            Bitmap.createBitmap(src, it.left.toInt(), it.top.toInt(),
                it.width.toInt().coerceAtMost(src.width - it.left.toInt()),
                it.height.toInt().coerceAtMost(src.height - it.top.toInt()))
        } ?: src
        val scale = minOf(1f, PERSON_MAX.toFloat() / maxOf(framed.width, framed.height))
        val sized = if (scale < 1f) Bitmap.createScaledBitmap(framed, (framed.width * scale).toInt(), (framed.height * scale).toInt(), true) else framed

        val face = TryOnFraming.faceOval(nose, lm(PoseLandmark.LEFT_EAR), lm(PoseLandmark.RIGHT_EAR),
            lm(PoseLandmark.LEFT_EYE), lm(PoseLandmark.RIGHT_EYE), shoulderW)
            ?.shifted(crop?.left ?: 0f, crop?.top ?: 0f)?.scaled(scale, scale)

        val bytes = ByteArrayOutputStream().also { sized.compress(Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
        val file = File(File(context.cacheDir, "tryon").apply { mkdirs() }, "person_${System.currentTimeMillis()}.jpg")
        file.writeBytes(bytes)
        val person = Person("data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP),
            file, sized.width, sized.height, face, fullBody)
        if (sized !== framed) sized.recycle()
        if (framed !== src) framed.recycle()
        src.recycle()
        person
    }

    private suspend fun detectPose(bitmap: Bitmap): Pose? {
        val detector = PoseDetection.getClient(
            AccuratePoseDetectorOptions.Builder().setDetectorMode(AccuratePoseDetectorOptions.SINGLE_IMAGE_MODE).build()
        )
        return try {
            suspendCancellableCoroutine { cont ->
                detector.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            }
        } finally { detector.close() }
    }

    // ── ③ 옷 사진 ───────────────────────────────────────────────────────────

    /**
     * FASHN에 보낼 옷 사진. 누끼(투명 PNG)면 그림자 · 반투명 가장자리를 지우고 흰 바탕 가운데에 옷만 크게,
     * 원본 사진이면 그대로. 이미 인터넷 주소(https)면 그대로 보낸다.
     */
    private suspend fun garmentImage(context: Context, g: TryOnGarment): String = withContext(Dispatchers.Default) {
        if (TryOnBatch.isResultUrl(g.image)) return@withContext g.image
        val src = decodeOriented(context, Uri.parse(g.image), GARMENT_MAX)
        val out = if (src.hasAlpha()) flatLay(src) ?: src else src
        val bytes = ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
        if (out !== src) out.recycle()
        src.recycle()
        "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /** 투명 누끼 → 흰 바탕 flat-lay (불투명한 옷 부분만, 사방 6% 여백) */
    private fun flatLay(src: Bitmap): Bitmap? {
        val cut = solidCrop(src) ?: return null
        val pad = (maxOf(cut.width, cut.height) * 0.06f).toInt()
        val out = Bitmap.createBitmap(cut.width + pad * 2, cut.height + pad * 2, Bitmap.Config.ARGB_8888)
        out.eraseColor(android.graphics.Color.WHITE)
        android.graphics.Canvas(out).drawBitmap(cut, pad.toFloat(), pad.toFloat(), null)
        cut.recycle()
        return out
    }

    /**
     * 옷장 통일 사진의 그림자(옅은 반투명)를 지우고 옷이 있는 부분만 자른 투명 PNG.
     * 수동 배치 화면에서도 옷 크기를 관절에 맞추려면 여백 없는 사진이 필요해서 같이 쓴다.
     */
    fun solidCrop(src: Bitmap): Bitmap? {
        val w = src.width; val h = src.height
        val px = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
        var l = w; var t = h; var r = -1; var b = -1
        for (i in px.indices) {
            val a = px[i] ushr 24
            if (a < 64) { px[i] = 0; continue }
            val x = i % w; val y = i / w
            if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y
        }
        if (r < 0) return null
        val pad = (maxOf(r - l, b - t) * 0.03f).toInt()
        val x0 = (l - pad).coerceAtLeast(0); val y0 = (t - pad).coerceAtLeast(0)
        val x1 = (r + pad).coerceAtMost(w - 1); val y1 = (b + pad).coerceAtMost(h - 1)
        val cleaned = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        return Bitmap.createBitmap(cleaned, x0, y0, x1 - x0 + 1, y1 - y0 + 1).also { if (it !== cleaned) cleaned.recycle() }
    }

    /** 수동 배치용 — 통일 사진이면 그림자 · 여백을 뺀 사진을 캐시에 만들어 그 주소를, 아니면 그대로 */
    suspend fun tightGarmentUri(context: Context, uri: String): String = withContext(Dispatchers.Default) {
        if (!uri.endsWith("_std.png")) return@withContext uri
        runCatching {
            val file = File(File(context.cacheDir, "tryon").apply { mkdirs() }, "fit_${uri.hashCode().toUInt()}.png")
            if (!file.exists()) {
                val src = decodeOriented(context, Uri.parse(uri), GARMENT_MAX)
                val cut = solidCrop(src) ?: return@runCatching uri
                src.recycle()
                file.outputStream().use { cut.compress(Bitmap.CompressFormat.PNG, 100, it) }
                cut.recycle()
            }
            Uri.fromFile(file).toString()
        }.getOrDefault(uri)
    }

    // ── ⑤ 합성 ──────────────────────────────────────────────────────────────

    /** AI 결과에 원본 얼굴을 부드럽게 합친다. 결과 비율이 보낸 사진과 다르거나 얼굴을 못 찾았으면 null */
    private suspend fun blendFace(context: Context, resultUrl: String, person: Person): String? = withContext(Dispatchers.IO) {
        val face = person.face ?: return@withContext null
        val bytes = download(resultUrl) ?: return@withContext null
        val result = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.copy(Bitmap.Config.ARGB_8888, true)
            ?: return@withContext null
        val rw = result.width; val rh = result.height
        if (kotlin.math.abs(rw.toFloat() / rh - person.width.toFloat() / person.height) > 0.02f) {
            result.recycle(); return@withContext null
        }
        val orig = BitmapFactory.decodeFile(person.file.absolutePath)?.let {
            Bitmap.createScaledBitmap(it, rw, rh, true).also { s -> if (s !== it) it.recycle() }
        } ?: run { result.recycle(); return@withContext null }
        val o = face.scaled(rw.toFloat() / person.width, rh.toFloat() / person.height)
        val x0 = (o.cx - o.rx).toInt().coerceIn(0, rw - 1); val x1 = (o.cx + o.rx).toInt().coerceIn(0, rw - 1)
        val y0 = (o.cy - o.ry).toInt().coerceIn(0, rh - 1); val y1 = (o.cy + o.ry).toInt().coerceIn(0, rh - 1)
        if (x1 <= x0 || y1 <= y0) { orig.recycle(); result.recycle(); return@withContext null }
        val bw = x1 - x0 + 1; val bh = y1 - y0 + 1
        val a = IntArray(bw * bh).also { orig.getPixels(it, 0, bw, x0, y0, bw, bh) }
        val r = IntArray(bw * bh).also { result.getPixels(it, 0, bw, x0, y0, bw, bh) }
        for (j in 0 until bh) for (i in 0 until bw) {
            val k = j * bw + i
            val wgt = TryOnFraming.faceWeight((x0 + i).toFloat(), (y0 + j).toFloat(), o)
            if (wgt <= 0f) continue
            fun mix(sh: Int) = (((a[k] shr sh) and 0xFF) * wgt + ((r[k] shr sh) and 0xFF) * (1 - wgt)).toInt()
            r[k] = (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
        }
        result.setPixels(r, 0, bw, x0, y0, bw, bh)
        orig.recycle()
        val out = File(File(context.cacheDir, "tryon").apply { mkdirs() }, "result_${System.currentTimeMillis()}.jpg")
        out.outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        result.recycle()
        Uri.fromFile(out).toString()
    }

    private fun download(url: String): ByteArray? {
        if (!TryOnBatch.isResultUrl(url)) return null
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 20_000; conn.readTimeout = 30_000; conn.instanceFollowRedirects = false
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.use { input ->
                val out = ByteArrayOutputStream(); val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    if (out.size() + n > 20 * 1024 * 1024) return null
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            }
        } catch (_: Exception) { null } finally { conn.disconnect() }
    }

    // ── 사진 읽기 (회전 정보 반영) ───────────────────────────────────────────

    private fun decodeOriented(context: Context, uri: Uri, maxSide: Int): Bitmap {
        if (Build.VERSION.SDK_INT >= 28) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val s = minOf(1f, maxSide.toFloat() / maxOf(info.size.width, info.size.height))
                d.setTargetSize(maxOf(1, (info.size.width * s).toInt()), maxOf(1, (info.size.height * s).toInt()))
            }.copy(Bitmap.Config.ARGB_8888, false)
        }
        val raw = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
            ?: throw TryOnFailure("사진을 열 수 없어요.")
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)!!.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val m = Matrix().apply {
            when (orientation) {
                3 -> setRotate(180f)
                6 -> setRotate(90f)
                8 -> setRotate(270f)
            }
            val s = minOf(1f, maxSide.toFloat() / maxOf(raw.width, raw.height))
            postScale(s, s)
        }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also { if (it !== raw) raw.recycle() }
    }
}
