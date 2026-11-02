package com.jeiu.fashionproject.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.jeiu.fashionproject.ui.viewmodel.BodyMeasurementGuides

/** Same ContentScale.Fit mapping as the image. Letterboxing must not shift the guides. */
internal fun bodyImageRect(width:Float,height:Float,imageWidth:Int,imageHeight:Int):Rect? {
    if(!width.isFinite() || !height.isFinite() || width<=0 || height<=0 || imageWidth<=0 || imageHeight<=0)return null
    val scale=minOf(width/imageWidth,height/imageHeight)
    val w=imageWidth*scale;val h=imageHeight*scale
    return Rect((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2)
}

@Composable
fun BodyMeasurementOverlay(guides:BodyMeasurementGuides?,modifier:Modifier=Modifier) {
    if(guides?.valid()!=true)return
    Canvas(modifier) {
        val rect=bodyImageRect(size.width,size.height,guides.imageWidth,guides.imageHeight) ?: return@Canvas
        guides.lines.forEachIndexed { index,line ->
            val color=listOf(Color(0xFF00897B),Color(0xFFFFB300),Color(0xFF5E35B1))[index]
            drawLine(color,Offset(rect.left+rect.width*line.left,rect.top+rect.height*line.y),
                Offset(rect.left+rect.width*line.right,rect.top+rect.height*line.y),3f)
        }
    }
}
