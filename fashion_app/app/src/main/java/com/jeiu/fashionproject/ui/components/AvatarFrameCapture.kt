package com.jeiu.fashionproject.ui.components

import android.graphics.Bitmap
import android.graphics.Color

/** Bound only to the visible GLES viewport; no network or stored body mutation. */
class AvatarFrameCapture {
    internal var reader: (suspend () -> Bitmap)? = null
    suspend fun captureBitmap(): Bitmap = reader?.invoke() ?: error("avatar_surface_unavailable")
}

internal object AvatarFramePixels {
    fun bitmap(width: Int, height: Int, rgba: ByteArray): Bitmap {
        require(width > 0 && height > 0 && width.toLong()*height <= 4_000_000)
        require(rgba.size.toLong() == width.toLong()*height*4)
        val pixels=IntArray(width*height)
        for (y in 0 until height) for (x in 0 until width) {
            val i=(y*width+x)*4
            val a=rgba[i+3].toInt() and 255
            require(a==255) { "avatar_transparent_frame" }
            pixels[(height-1-y)*width+x]=Color.argb(a,rgba[i].toInt() and 255,
                rgba[i+1].toInt() and 255,rgba[i+2].toInt() and 255)
        }
        require(pixels.any { it != pixels[0] }) { "avatar_empty_frame" }
        return Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888)
    }
}
