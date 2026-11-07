package com.jeiu.fashionproject.ui.viewmodel

import android.content.Context
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.data.WardrobeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

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
                originalUri = item.originalUri ?: img,
                cutoutUri = std
            )
        } else {
            val std = BackgroundRemover.cutoutFromOriginal(context, item.originalUri ?: img) ?: return null
            item.copy(imageUri = std, isCutout = true, cutoutUri = std, originalUri = item.originalUri ?: img)
        }
    }

    /** Prepare first; cancellation/processing failure cannot partially overwrite records.
     * Concurrently edited/deleted items are skipped at the main-thread commit point.
     */
    suspend fun unifyAll(
        context: Context,
        targets: List<WardrobeItem>,
        progress: (Int, Int) -> Unit = { _, _ -> },
        transform: suspend (Context, WardrobeItem) -> WardrobeItem? = ::unify
    ): Int {
        val prepared = mutableListOf<Pair<WardrobeItem, WardrobeItem>>()
        try {
            for ((index, item) in targets.withIndex()) {
                currentCoroutineContext().ensureActive()
                transform(context, item)?.let { prepared += item to it }
                progress(index + 1, targets.size)
            }
            currentCoroutineContext().ensureActive()
            return withContext(Dispatchers.Main.immediate) {
                currentCoroutineContext().ensureActive()
                prepared.count { (before, after) -> WardrobeStore.updatePhotoIfUnchanged(context, before, after) }
            }
        } finally {
            // Keep all originals. Only unreferenced derivatives created by this action are discarded.
            val used = WardrobeStore.items.value.flatMap { it.photoUris() }.toSet()
            prepared.forEach { (before, after) ->
                (after.photoUris() - before.photoUris() - used).forEach { WardrobeStore.deletePhoto(context, it) }
            }
        }
    }
}
