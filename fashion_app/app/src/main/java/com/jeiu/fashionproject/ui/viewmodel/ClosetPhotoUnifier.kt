package com.jeiu.fashionproject.ui.viewmodel

import android.content.Context
import com.jeiu.fashionproject.data.WardrobeItem

/**
 * 이미 옷장에 있는 옷 사진을 새 등록 사진과 같은 모양(통일 사진)으로 맞춘다 — 옷장 [사진 정리] 버튼.
 *  - 누끼 사진: 같은 크기 캔버스 · 같은 비율 · 같은 그림자로 다시 만든다
 *  - 배경이 남은 원본 사진: 배경을 지운 뒤 통일 사진으로 만든다 (원본은 "원본" 버전으로 남겨 둠)
 * 배경을 지우지 못한 옷은 그대로 둔다.
 */
object ClosetPhotoUnifier {

    /** 아직 통일 사진이 아닌 옷 */
    fun needs(item: WardrobeItem): Boolean = !item.imageUri.endsWith(BackgroundRemover.STD_SUFFIX)

    /** 바꾼 옷 정보 (못 바꾸면 null) */
    suspend fun unify(context: Context, item: WardrobeItem): WardrobeItem? {
        val img = item.imageUri
        return if (item.isCutout) {
            val std = BackgroundRemover.restandardize(context, img) ?: return null
            item.copy(
                imageUri = std,
                cutoutUri = if (item.cutoutUri == null || item.cutoutUri == img) std else item.cutoutUri,
                garmentUri = if (item.garmentUri == img) std else item.garmentUri
            )
        } else {
            val std = BackgroundRemover.cutoutFromOriginal(context, item.originalUri ?: img) ?: return null
            item.copy(imageUri = std, isCutout = true, cutoutUri = std, originalUri = item.originalUri ?: img)
        }
    }
}
