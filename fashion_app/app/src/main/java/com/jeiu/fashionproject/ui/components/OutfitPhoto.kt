package com.jeiu.fashionproject.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.jeiu.fashionproject.R
import com.jeiu.fashionproject.data.Gender
import com.jeiu.fashionproject.data.NaverImageSearchRepository

/**
 * 화면에 띄울 사진. primary(원본·고화질)를 먼저 띄우고, 원본이 막혀 있거나
 * 깨지면 fallback(네이버 썸네일·저화질)으로 대신 보여준다.
 */
data class OutfitPhoto(val primary: Any?, val fallback: Any? = null)

/** 같은 검색어는 앱이 켜져 있는 동안 한 번만 네이버에 요청한다. */
private val photoCache = mutableMapOf<String, OutfitPhoto>()

/**
 * 앱에 넣어둔 코디 사진이 없는 경우(사진 없음, 또는 대표 자리표시 사진 casual_daily_look)
 * 네이버 이미지 검색으로 사진을 채운다. 앱에 전용 사진이 있으면 그 사진을 그대로 쓴다.
 */
private fun needsSearchPhoto(source: Any?): Boolean =
    source == null || source == 0 || source == R.drawable.casual_daily_look

/** "여성 트렌치코트 슬랙스 코디" 같은 네이버 검색어를 만든다. */
fun outfitQuery(gender: Gender?, vararg parts: String, suffix: String = ""): String =
    (listOfNotNull(gender?.label) + parts.toList() + suffix)
        .joinToString(" ")
        .replace("+", " ")
        .replace(Regex("\\s+"), " ")
        .trim()

@Composable
fun rememberOutfitPhoto(source: Any?, query: String): OutfitPhoto {
    val needsSearch = needsSearchPhoto(source)
    val initial = if (needsSearch) photoCache[query] ?: OutfitPhoto(source.takeIf { it != 0 }) else OutfitPhoto(source)
    val photo by produceState(initialValue = initial, query, needsSearch) {
        if (!needsSearch || photoCache.containsKey(query)) return@produceState
        val first = runCatching {
            NaverImageSearchRepository.search(query, display = 3).firstOrNull()
        }.getOrNull()
        if (first != null) {
            val found = OutfitPhoto(primary = first.imageUrl, fallback = first.thumbnailUrl)
            photoCache[query] = found
            value = found
        }
    }
    return photo
}

/** 원본(고화질) → 실패하면 썸네일 순서로 보여주는 이미지 */
@Composable
fun OutfitPhotoImage(
    photo: OutfitPhoto,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    var useFallback by remember(photo) { mutableStateOf(false) }
    AsyncImage(
        model = if (useFallback) photo.fallback else photo.primary,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        onError = { if (!useFallback && photo.fallback != null) useFallback = true }
    )
}
