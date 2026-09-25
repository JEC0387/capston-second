package com.jeiu.fashionproject.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter

/**
 * MediaPipe Image Segmentation으로 사람 실루엣 마스크를 만들고,
 * 특정 높이(어깨/허리/힙)에서의 실제 가로폭(픽셀 수)을 측정한다.
 *
 * 모델: assets/selfie_segmenter.tflite (사용자가 직접 배치)
 * 모델이 없거나 초기화에 실패하면 isAvailable=false → 호출부가 포즈 추정으로 폴백.
 */
class BodySegmenter(context: Context) {

    private var segmenter: ImageSegmenter? = null
    val isAvailable: Boolean get() = segmenter != null

    init {
        runCatching {
            val base = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET)
                .build()
            val options = ImageSegmenter.ImageSegmenterOptions.builder()
                .setBaseOptions(base)
                .setRunningMode(RunningMode.IMAGE)
                .setOutputCategoryMask(true)
                .setOutputConfidenceMasks(false)
                .build()
            segmenter = ImageSegmenter.createFromOptions(context, options)
        }
    }

    /**
     * 실루엣 마스크에서 어깨·허리·힙 높이의 실제 가로폭을 측정한다.
     * @param shoulderYRatio 0~1, 이미지 세로 기준 어깨 높이 위치
     * @return 측정 실패 시 null
     */
    fun measureWidths(
        bitmap: Bitmap,
        shoulderYRatio: Float,
        waistYRatio: Float,
        hipYRatio: Float
    ): BodyWidths? {
        val seg = segmenter ?: return null
        return runCatching {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = seg.segment(mpImage)
            val mask = result.categoryMask().orElse(null) ?: return null

            val w = mask.width
            val h = mask.height
            val byteBuffer = com.google.mediapipe.framework.image.ByteBufferExtractor
                .extract(mask)

            // category 0 = 배경, 그 외(1~5: 머리카락/피부/얼굴/옷/기타) = 사람으로 간주
            fun rowWidth(yRatio: Float): Int {
                val y = (yRatio * h).toInt().coerceIn(0, h - 1)
                var minX = -1
                var maxX = -1
                val rowStart = y * w
                for (x in 0 until w) {
                    val idx = rowStart + x
                    if (idx >= byteBuffer.capacity()) break
                    val v = byteBuffer.get(idx).toInt() and 0xFF
                    if (v != 0) {
                        if (minX < 0) minX = x
                        maxX = x
                    }
                }
                return if (minX < 0) 0 else (maxX - minX + 1)
            }

            // 단일 행은 노이즈에 약하므로 ±2% 범위 평균
            fun bandWidth(yRatio: Float): Float {
                val offsets = listOf(-0.02f, -0.01f, 0f, 0.01f, 0.02f)
                val widths = offsets.map { rowWidth((yRatio + it).coerceIn(0f, 1f)) }.filter { it > 0 }
                return if (widths.isEmpty()) 0f else widths.average().toFloat()
            }

            val shoulder = bandWidth(shoulderYRatio)
            val waist = bandWidth(waistYRatio)
            val hip = bandWidth(hipYRatio)
            if (shoulder <= 0f || hip <= 0f) return null

            BodyWidths(shoulder = shoulder, waist = waist, hip = hip)
        }.getOrNull()
    }

    fun close() {
        runCatching { segmenter?.close() }
        segmenter = null
    }

    companion object {
        const val MODEL_ASSET = "selfie_segmenter.tflite"
    }
}

/** 세그멘테이션 마스크에서 실측한 신체 가로폭 (픽셀 단위) */
data class BodyWidths(val shoulder: Float, val waist: Float, val hip: Float)
