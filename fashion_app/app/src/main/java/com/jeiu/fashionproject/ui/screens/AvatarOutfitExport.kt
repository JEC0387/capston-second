package com.jeiu.fashionproject.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.Size
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import com.jeiu.fashionproject.data.AvatarProfile
import com.jeiu.fashionproject.ui.components.AvatarFrameCapture
import kotlinx.coroutines.*

internal data class AvatarOutfitSnapshot(val profile: AvatarProfile, val canvas: Size,
    val layers: List<WornGarment>, val alpha: Float, val inspecting: Boolean, val cameraRevision: Int = 0)

internal object AvatarOutfitExport {
    suspend fun create(context: Context, input: AvatarOutfitSnapshot, capture: AvatarFrameCapture): ByteArray {
        require(!input.inspecting && input.layers.isNotEmpty() && input.canvas.isUsableFittingSize())
        require(input.layers.all { ManualPhotoExport.localInput(it.uri) })
        // Capture immediately after freezing the UI snapshot, before asynchronous image decoding.
        val frame=capture.captureBitmap()
        try {
            require(frame.width==input.canvas.width.toInt() && frame.height==input.canvas.height.toInt())
            return withContext(Dispatchers.Default) {
                val images=input.layers.sortedBy { slotOf(it.item).ordinal }.map { garment ->
                    ensureActive()
                    val request=ImageRequest.Builder(context).data(Uri.parse(garment.uri)).size(1600,1600)
                        .precision(Precision.INEXACT).allowHardware(false).memoryCachePolicy(CachePolicy.DISABLED)
                        .diskCachePolicy(CachePolicy.DISABLED).networkCachePolicy(CachePolicy.DISABLED).build()
                    val image=(context.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap()
                        ?: error("avatar_garment_unavailable")
                    garment to image
                }
                ensureActive()
                // Same placement, rotation, opacity and z-order as the on-screen fitting canvas.
                ManualPhotoExport.render(frame,images,input.canvas,input.alpha)
            }
        } finally { frame.recycle() }
    }
}
