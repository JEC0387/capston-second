package com.jeiu.fashionproject.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import com.jeiu.fashionproject.data.WardrobeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bounds of visible pixels, not a claim that segmentation removed hair or skin. */
internal data class GarmentRegion(val left:Float,val top:Float,val width:Float,val height:Float) {
    fun expand(content:Placement, imageAspect:Float):Placement {
        val fullWidth=content.width/width
        return Placement(content.center-Offset((left+width/2-.5f)*fullWidth,
            (top+height/2-.5f)*fullWidth*imageAspect),fullWidth,content.rotation)
    }
}
internal data class GarmentImage(val uri:String,val aspect:Float,val region:GarmentRegion?)
internal object GarmentGeometry {
    fun bounds(bitmap:Bitmap):GarmentRegion? {
        var minX=bitmap.width;var minY=bitmap.height;var maxX=-1;var maxY=-1;var transparent=0
        for(y in 0 until bitmap.height)for(x in 0 until bitmap.width) {
            if((bitmap.getPixel(x,y) ushr 24)>=32) {
                minX=minOf(minX,x);maxX=maxOf(maxX,x);minY=minOf(minY,y);maxY=maxOf(maxY,y)
            } else transparent++
        }
        if(maxX<minX || transparent<bitmap.width*bitmap.height*.01f) return null
        val w=(maxX-minX+1).toFloat()/bitmap.width;val h=(maxY-minY+1).toFloat()/bitmap.height
        if(w<.05f || h<.05f) return null
        return GarmentRegion(minX.toFloat()/bitmap.width,minY.toFloat()/bitmap.height,w,h)
    }
    suspend fun load(context:Context,item:WardrobeItem):GarmentImage?=withContext(Dispatchers.IO) {
        val candidates=listOfNotNull(item.garmentUri,item.cutoutUri,item.imageUri,item.originalUri).distinct()
        var fallback:GarmentImage?=null
        for(source in candidates) {
            val uri=Uri.parse(source)
            if(uri.scheme !in setOf("content","file","android.resource"))continue
            val image=runCatching {
                val options=BitmapFactory.Options().apply {inJustDecodeBounds=true}
                context.contentResolver.openInputStream(uri)?.use {BitmapFactory.decodeStream(it,null,options)}
                if(options.outWidth<=0 || options.outHeight<=0)return@runCatching null
                options.inJustDecodeBounds=false
                options.inSampleSize=1
                while(maxOf(options.outWidth,options.outHeight)/options.inSampleSize>512)options.inSampleSize*=2
                val bitmap=context.contentResolver.openInputStream(uri)?.use {BitmapFactory.decodeStream(it,null,options)} ?: return@runCatching null
                try {GarmentImage(source,bitmap.height.toFloat()/bitmap.width,bounds(bitmap))} finally {bitmap.recycle()}
            }.getOrNull() ?: continue
            if(image.region!=null)return@withContext image
            if(fallback==null)fallback=image
        }
        fallback
    }
}
