package com.jeiu.fashionproject.ui.components

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/** Same viewing scale for both photos. It never warps, composites or edits result pixels. */
@Composable
fun TryOnComparisonDialog(original: String?, result: ByteArray, previous: Boolean, onDismiss: () -> Unit) {
    var originalVisible by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (previous) "이전 AI 결과 확대 비교" else "AI 생성 이미지 확대 비교") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (originalVisible) "원본 인물 사진" else "AI 생성 결과 · 실제 착용/체형 보존 미보장")
                OutlinedButton(onClick = { originalVisible = !originalVisible }, enabled = original != null) {
                    Text(if (originalVisible) "같은 배율로 AI 결과" else "같은 배율로 원본")
                }
                Box(Modifier.fillMaxWidth().height(300.dp).clipToBounds().testTag("tryOnZoomPreview")
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            val maxX = size.width * (scale - 1f) / 2f
                            val maxY = size.height * (scale - 1f) / 2f
                            offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                            offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                        }
                    }) {
                    AsyncImage(if (originalVisible) original else result,
                        if (originalVisible) "확대한 원본" else "확대한 AI 결과", contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY
                        })
                }
                Slider(scale, { scale = it; offsetX = 0f; offsetY = 0f }, valueRange = 1f..4f,
                    modifier = Modifier.testTag("tryOnZoomSlider"))
                Text("두 손가락으로 확대하고 끌어서 얼굴·의류 경계를 확인하세요. 확대는 생성 품질을 바꾸지 않아요.")
                TextButton(onClick = { scale = 1f; offsetX = 0f; offsetY = 0f }) { Text("확대 초기화") }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("비교 닫기") } })
}
