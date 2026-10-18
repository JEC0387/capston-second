package com.jeiu.fashionproject.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.jeiu.fashionproject.ui.camera.GuidePoint
import com.jeiu.fashionproject.ui.camera.Joint
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Reuses the bundled selfie model. Returned geometry is in analysis-bitmap pixels, not mask pixels. */
class BodySegmenter(context: Context) {
    private var segmenter: ImageSegmenter? = null
    val isAvailable: Boolean get() = segmenter != null

    init {
        runCatching {
            segmenter = ImageSegmenter.createFromOptions(context,
                ImageSegmenter.ImageSegmenterOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
                    .setRunningMode(RunningMode.IMAGE)
                    .setOutputCategoryMask(false)
                    .setOutputConfidenceMasks(true)
                    .build())
        }
    }

    @Synchronized
    fun inspectWidths(bitmap: Bitmap, points: Map<Joint, GuidePoint>): SilhouetteMeasurement {
        val seg = segmenter ?: return SilhouetteMeasurement(issue = SilhouetteIssue.MODEL_UNAVAILABLE)
        return runCatching {
            val input = BitmapImageBuilder(bitmap).build()
            try {
                val result = seg.segment(input)
                val masks = result.confidenceMasks().orElse(null) ?: return SilhouetteMeasurement(issue = SilhouetteIssue.INVALID_MASK)
                try {
                    // Bundled model metadata labels.txt contains only "selfie": channel 0 is person probability.
                    // Do not interpret its category IDs as multiclass clothing/body labels.
                    val mask = masks.singleOrNull() ?: return SilhouetteMeasurement(issue = SilhouetteIssue.INVALID_MASK)
                    val buffer = ByteBufferExtractor.extract(mask).duplicate().order(ByteOrder.nativeOrder())
                    if (buffer.capacity().toLong() < mask.width.toLong() * mask.height * 4L) return SilhouetteMeasurement(issue = SilhouetteIssue.INVALID_MASK)
                    // Limit mask geometry work/memory. Separate X/Y scaling maps it exactly back to the bitmap.
                    val scale = minOf(1f, 512f / maxOf(mask.width, mask.height))
                    val w = (mask.width * scale).roundToInt().coerceAtLeast(1)
                    val h = (mask.height * scale).roundToInt().coerceAtLeast(1)
                    val confidence = FloatArray(w * h) { i ->
                        val x = (((i % w) + 0.5f) * mask.width / w).toInt().coerceAtMost(mask.width - 1)
                        val y = (((i / w) + 0.5f) * mask.height / h).toInt().coerceAtMost(mask.height - 1)
                        buffer.getFloat((y * mask.width + x) * 4)
                    }
                    SilhouetteMeasurer.inspect(confidence, w, h, bitmap.width, bitmap.height, points)
                } finally {
                    masks.forEach { it.close() }
                    result.categoryMask().orElse(null)?.close()
                }
            } finally { input.close() }
        }.getOrElse { SilhouetteMeasurement(issue = SilhouetteIssue.INVALID_MASK) }
    }

    @Synchronized
    fun close() { segmenter?.close(); segmenter = null }

    companion object { const val MODEL_ASSET = "selfie_segmenter.tflite" }
}
