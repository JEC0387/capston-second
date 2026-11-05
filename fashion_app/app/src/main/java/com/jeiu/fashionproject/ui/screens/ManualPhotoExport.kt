package com.jeiu.fashionproject.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.compose.ui.geometry.Size
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import com.jeiu.fashionproject.ui.viewmodel.FittingPoseDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/** A frozen local composition, not a model inference request or a saved body measurement. */
internal data class ManualPhotoSnapshot(val photo: String, val generation: Long, val canvas: Size,
    val layers: List<WornGarment>, val alpha: Float,
    val body: FittingPoseDetector.BodyPoints? = null, val automatic: Boolean = false)
internal data class ManualPhotoResult(val input: ManualPhotoSnapshot, val original: ByteArray, val png: ByteArray,
    val reports: List<String> = emptyList())

internal object ManualPhotoExport {
    fun localInput(source: String): Boolean = Uri.parse(source).scheme in setOf("content", "file", "android.resource")

    /** Same Fit/letterbox and back-to-front placement contract as the visible manual canvas. */
    fun render(photo: Bitmap, layers: List<Pair<WornGarment, Bitmap>>, size: Size, alpha: Float): ByteArray {
        require(size.isUsableFittingSize() && size.width >= 1 && size.height >= 1)
        require(alpha.isFinite() && alpha in 0f..1f && layers.size <= FitSlot.entries.size)
        require(!photo.isRecycled)
        layers.forEach { (g, image) ->
            require(!image.isRecycled && listOf(g.center.x, g.center.y, g.baseWidth, g.scale, g.aspect, g.rotation).all { it.isFinite() })
            require(g.baseWidth > 0 && g.scale > 0 && g.aspect > 0)
        }
        val outputScale = minOf(1f, 1600f / maxOf(size.width, size.height))
        val output = Bitmap.createBitmap((size.width * outputScale).roundToInt().coerceAtLeast(1),
            (size.height * outputScale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(output)
            canvas.drawColor(android.graphics.Color.rgb(245, 245, 245))
            canvas.scale(outputScale, outputScale)
            val fit = minOf(size.width / photo.width, size.height / photo.height)
            val width = photo.width * fit; val height = photo.height * fit
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(photo, null, RectF((size.width-width)/2, (size.height-height)/2,
                (size.width+width)/2, (size.height+height)/2), paint)
            paint.alpha = (alpha * 255).roundToInt()
            layers.forEach { (g, image) ->
                val w = g.baseWidth * g.scale; val h = w * g.aspect
                require(w.isFinite() && h.isFinite())
                canvas.save()
                canvas.translate(g.center.x, g.center.y)
                canvas.rotate(g.rotation)
                canvas.drawBitmap(image, null, RectF(-w/2, -h/2, w/2, h/2), paint)
                canvas.restore()
            }
            return ByteArrayOutputStream().use { out ->
                check(output.compress(Bitmap.CompressFormat.PNG, 100, out))
                check(out.size() <= 8 * 1024 * 1024)
                out.toByteArray()
            }
        } finally { output.recycle() }
    }

    suspend fun create(context: Context, input: ManualPhotoSnapshot): ManualPhotoResult = withContext(Dispatchers.Default) {
        require(input.layers.isNotEmpty())
        require((listOf(input.photo) + input.layers.map { it.uri }).all(::localInput))
        suspend fun load(source: String, maxSide: Int): Bitmap {
            coroutineContext.ensureActive()
            val request = ImageRequest.Builder(context).data(Uri.parse(source)).size(maxSide, maxSide)
                // This non-View request would otherwise upscale small inputs to the requested
                // maximum. Keep native pixels and prevent a larger shared cache hit defeating it.
                .precision(Precision.INEXACT).allowHardware(false).memoryCachePolicy(CachePolicy.DISABLED)
                .networkCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.DISABLED).build()
            val result = context.imageLoader.execute(request) as? SuccessResult ?: error("local_image_unavailable")
            // Coil's drawable applies EXIF; do not recycle its shared cached bitmap.
            return result.drawable.toBitmap()
        }
        val photo = load(input.photo, if (input.automatic) 2048 else 1600)
        val layers = input.layers.map { it to load(it.uri, if (input.automatic) 1024 else 1600) }
        coroutineContext.ensureActive()
        val original = render(photo, emptyList(), input.canvas, input.alpha)
        coroutineContext.ensureActive()
        val enhanced = if (input.automatic) LocalDrapeCompositor.render(photo, layers, input.canvas,
            input.alpha, input.body ?: throw LocalDrapeFailure("신체 위치가 확인되지 않았어요. 수동 배치를 사용해주세요.")) else null
        val result = enhanced?.png ?: render(photo, layers, input.canvas, input.alpha)
        coroutineContext.ensureActive()
        ManualPhotoResult(input, original, result, enhanced?.reports.orEmpty())
    }
}
