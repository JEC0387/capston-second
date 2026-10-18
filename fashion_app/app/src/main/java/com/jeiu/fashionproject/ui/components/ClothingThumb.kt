package com.jeiu.fashionproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.ui.theme.CreamDark

/**
 * 옷 사진 한 칸 — 옷장·착용 기록·달력에서 같은 모양으로 보여준다.
 * 누끼 옷은 같은 바탕색 위 가운데 정렬(잘리지 않게), 일반 사진은 칸을 꽉 채운다.
 * [overlay]로 하트·라벨·체크 표시 등을 위에 얹을 수 있다.
 */
@Composable
fun ClothingThumb(
    item: WardrobeItem,
    modifier: Modifier = Modifier,
    cutoutPadding: Dp = 14.dp,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    // 일반 사진은 옷 대표색을 사진 로딩 전 자리 표시로 쓴다
    Box(modifier = modifier.background(if (item.isCutout) CreamDark else Color(item.colorValue))) {
        AsyncImage(
            model = item.imageUri,
            contentDescription = item.name,
            contentScale = if (item.isCutout) ContentScale.Fit else ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .padding(if (item.isCutout) cutoutPadding else 0.dp)
        )
        overlay()
    }
}
