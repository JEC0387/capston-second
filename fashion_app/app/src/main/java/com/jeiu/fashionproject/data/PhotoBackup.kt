package com.jeiu.fashionproject.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 옷장 옷 사진을 서버 백업용으로 줄인다 (긴 변 720px).
 * 배경을 지운 사진(투명)은 WebP, 나머지는 JPEG 로 바꿔 2MB 아래로 맞춘다.
 * 옷장 옷 사진만 다룬다 — 체형 분석 사진은 이 경로로 들어오지 않는다.
 */
object PhotoBackup {
    const val MAX_SIDE = 720
    const val MAX_BYTES = 1_900_000

    /** (mime, 바이트). 사진을 읽지 못하면 null */
    fun encode(file: File, transparent: Boolean): Pair<String, ByteArray>? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1), true).also { if (it !== decoded) decoded.recycle() }
        } else decoded
        try {
            val alpha = transparent || bitmap.hasAlpha()
            val mime = if (alpha) "image/webp" else "image/jpeg"
            var quality = 85
            var result: Pair<String, ByteArray>? = null
            while (result == null && quality >= 40) {
                val out = ByteArrayOutputStream()
                if (alpha) bitmap.compress(webp(), quality, out) else bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                val bytes = out.toByteArray()
                if (bytes.size <= MAX_BYTES) result = mime to bytes
                quality -= 15
            }
            result
        } finally {
            bitmap.recycle()
        }
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun webp(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

    /** 서버에서 받은 사진 형식 → 파일 확장자 */
    fun extension(mime: String): String = when (mime) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }
}
