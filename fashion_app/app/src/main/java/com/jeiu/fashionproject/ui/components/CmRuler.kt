package com.jeiu.fashionproject.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeiu.fashionproject.data.CmRulerMath
import com.jeiu.fashionproject.ui.theme.*
import java.util.Locale
import kotlin.math.*

/** A moving tape beneath a fixed centre marker. The caller's cm value is the only displayed state. */
@Composable
fun CmRuler(value:Float,range:ClosedFloatingPointRange<Float>,label:String,onValueChange:(Float)->Unit,
    onDirectInput:()->Unit,modifier:Modifier=Modifier) {
    val density=LocalDensity.current
    val pixelsPerCm=with(density){16.dp.toPx()}
    val textSize=with(density){13.sp.toPx()}
    val paint=remember(textSize){Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Brown.toArgb();this.textSize=textSize;textAlign=Paint.Align.CENTER}}
    val current by rememberUpdatedState(value)
    val update by rememberUpdatedState(onValueChange)
    // The valid range changes when a different body part is edited. Keep it live
    // without restarting the pointer coroutine during an active ruler drag.
    val activeRange by rememberUpdatedState(range)
    Column(modifier,verticalArrangement=Arrangement.spacedBy(4.dp)) {
        TextButton(onClick=onDirectInput,modifier=Modifier.align(Alignment.CenterHorizontally).testTag("rulerCurrentValue")) {
            Text(String.format(Locale.KOREA,"%.1f cm",value),style=MaterialTheme.typography.headlineMedium,color=Brown)
        }
        Canvas(Modifier.fillMaxWidth().height(88.dp).testTag("avatarCmRuler")
            .semantics {
                contentDescription="$label 줄자. 왼쪽으로 밀면 증가, 오른쪽으로 밀면 감소. 0.5cm 단위. 숫자를 눌러 직접 입력할 수 있어요."
                stateDescription=String.format(Locale.KOREA,"%.1f cm",value)
                progressBarRangeInfo=ProgressBarRangeInfo(value,range,((range.endInclusive-range.start)*2).toInt().coerceAtLeast(1)-1)
                setProgress {v->if(v.isFinite()) {update(CmRulerMath.drag(v,0f,pixelsPerCm,range));true} else false}
            }
            .pointerInput(label,pixelsPerCm) {
                var dragStartCm=current
                var dragDistancePx=0f
                detectHorizontalDragGestures(
                    onDragStart={ dragStartCm=current; dragDistancePx=0f },
                    onHorizontalDrag={change,amount->
                        change.consume()
                        dragDistancePx+=amount
                        update(CmRulerMath.drag(dragStartCm,dragDistancePx,pixelsPerCm,activeRange))
                    }
                )
            }) {
            drawRoundRect(CamelLight,cornerRadius=androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()))
            val half=size.width/(2*pixelsPerCm)
            val start=max(range.start.toInt(),floor(value-half-1).toInt())
            val end=min(ceil(range.endInclusive).toInt(),ceil(value+half+1).toInt())
            val top=12.dp.toPx()
            for(number in start..end) {
                val x=size.width/2+(number-value)*pixelsPerCm
                val major=number%5==0
                drawLine(if(major)Brown else Camel,androidx.compose.ui.geometry.Offset(x,top),
                    androidx.compose.ui.geometry.Offset(x,top+(if(major)30.dp else 17.dp).toPx()),if(major)2.dp.toPx() else 1.dp.toPx())
                if(major)drawContext.canvas.nativeCanvas.drawText(number.toString(),x,top+54.dp.toPx(),paint)
                val halfX=x+pixelsPerCm/2
                if(number+.5f in range)drawLine(Camel,androidx.compose.ui.geometry.Offset(halfX,top),
                    androidx.compose.ui.geometry.Offset(halfX,top+8.dp.toPx()),1.dp.toPx())
            }
            drawLine(Brown,androidx.compose.ui.geometry.Offset(size.width/2,3.dp.toPx()),
                androidx.compose.ui.geometry.Offset(size.width/2,48.dp.toPx()),3.dp.toPx())
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            OutlinedButton(onClick={onValueChange(CmRulerMath.drag(value-.5f,0f,pixelsPerCm,range))},
                enabled=value>range.start,modifier=Modifier.testTag("rulerMinus")) {Text("−0.5 cm")}
            TextButton(onClick=onDirectInput,modifier=Modifier.testTag("rulerDirectInput")) {Text("직접 입력")}
            OutlinedButton(onClick={onValueChange(CmRulerMath.drag(value+.5f,0f,pixelsPerCm,range))},
                enabled=value<range.endInclusive,modifier=Modifier.testTag("rulerPlus")) {Text("+0.5 cm")}
        }
    }
}
