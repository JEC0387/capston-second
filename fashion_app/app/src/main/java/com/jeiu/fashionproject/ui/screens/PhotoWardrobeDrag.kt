package com.jeiu.fashionproject.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.ui.viewmodel.FittingPoseDetector

/** Initial affine reference only; separate per-foot warp and visual acceptance happen at export. */
internal fun photoDropPlacement(slot: FitSlot, item: WardrobeItem, aspect: Float, canvas: Size,
    body: FittingPoseDetector.BodyPoints?): Placement? {
    if (slot == FitSlot.SHOES) {
        body ?: return null
        fun valid(point: Offset?) = point != null && point.x.isFinite() && point.y.isFinite() &&
            point.x >= 0f && point.x < body.imageWidth && point.y >= 0f && point.y < body.imageHeight
        val shoulders = body.shoulders ?: return null
        if (!valid(shoulders.first) || !valid(shoulders.second)) return null
        val width = (shoulders.second - shoulders.first).getDistance()
        if (width <= 0f) return null
        for (side in listOf(body.left, body.right)) {
            side ?: return null
            val ankle = side.ankle; val heel = side.heel; val toe = side.toe
            if (!valid(ankle) || !valid(heel) || !valid(toe)) return null
            val length = (toe!! - heel!!).getDistance()
            if (length < width * .07f || length > width * .65f ||
                (ankle!! - heel).getDistance() > length * 1.5f) return null
        }
    }
    return photoAutoPlacement(slot, item, aspect, canvas, body)
}

/** Root-coordinate hit test: blank Fit letterboxing is not the model photograph. */
internal fun photoDropPosition(rootPoint: Offset, canvasBounds: Rect, photoSize: Size?): Offset? {
    if (!rootPoint.x.isFinite() || !rootPoint.y.isFinite()) return null
    val bounds = photoDropBounds(canvasBounds, photoSize) ?: return null
    if (!bounds.contains(rootPoint)) return null
    return rootPoint - canvasBounds.topLeft
}

internal fun photoDropBounds(canvasBounds: Rect, photoSize: Size?): Rect? {
    if (photoSize == null || !photoSize.isUsableFittingSize() ||
        !listOf(canvasBounds.left, canvasBounds.top, canvasBounds.width, canvasBounds.height).all { it.isFinite() } ||
        canvasBounds.width <= 0 || canvasBounds.height <= 0) return null
    val scale = minOf(canvasBounds.width / photoSize.width, canvasBounds.height / photoSize.height)
    val width = photoSize.width * scale; val height = photoSize.height * scale
    val left = canvasBounds.left + (canvasBounds.width - width) / 2
    val top = canvasBounds.top + (canvasBounds.height - height) / 2
    return Rect(left, top, left + width, top + height)
}

internal data class PhotoDropTicket(val slot: FitSlot, val generation: Long, val request: Long,
    val center: Offset, val canvas: Size, val photoSize: Size)

internal data class PhotoDropUndo(val slot: FitSlot, val previous: WornGarment?,
    val previousActive: FitSlot?, val generation: Long)
