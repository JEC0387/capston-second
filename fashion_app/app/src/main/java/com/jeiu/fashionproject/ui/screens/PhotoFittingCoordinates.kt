package com.jeiu.fashionproject.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.min

internal fun Size.isUsableFittingSize() = width.isFinite() && height.isFinite() && width > 0f && height > 0f

/** Map through the centered ContentScale.Fit image, not through the whole canvas.
 * [image] is the decoded, display-oriented size; EXIF rotation must already be applied.
 * Unknown/invalid geometry means keep the user's placement, never invent a scale.
 */
internal fun resizePhotoPlacement(placement: Placement, image: Size?, before: Size, after: Size): Placement? {
    if (image == null || !image.isUsableFittingSize() || !before.isUsableFittingSize() ||
        !after.isUsableFittingSize()) return null
    // Use double intermediates so repeated reciprocal scales do not accumulate float drift.
    val oldScale = min(before.width.toDouble() / image.width, before.height.toDouble() / image.height)
    val newScale = min(after.width.toDouble() / image.width, after.height.toDouble() / image.height)
    val ratio = newScale / oldScale
    val oldLeft = (before.width - image.width * oldScale) / 2
    val oldTop = (before.height - image.height * oldScale) / 2
    val newLeft = (after.width - image.width * newScale) / 2
    val newTop = (after.height - image.height * newScale) / 2
    val center = Offset(((placement.center.x - oldLeft) * ratio + newLeft).toFloat(),
        ((placement.center.y - oldTop) * ratio + newTop).toFloat())
    val width = (placement.width * ratio).toFloat()
    return if (center.x.isFinite() && center.y.isFinite() && width.isFinite() && width > 0f)
        placement.copy(center = center, width = width) else null
}
