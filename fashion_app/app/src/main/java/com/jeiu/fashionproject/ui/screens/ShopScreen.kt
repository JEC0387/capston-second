package com.jeiu.fashionproject.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jeiu.fashionproject.data.Gender
import com.jeiu.fashionproject.data.ProductInterestStore
import com.jeiu.fashionproject.data.ShopCatalog
import com.jeiu.fashionproject.data.ShopImageResult
import com.jeiu.fashionproject.ui.components.OutfitPhoto
import com.jeiu.fashionproject.ui.components.OutfitPhotoImage
import com.jeiu.fashionproject.ui.components.ShopSearchSheet
import com.jeiu.fashionproject.ui.theme.*
import com.jeiu.fashionproject.ui.viewmodel.ShopUiState
import com.jeiu.fashionproject.ui.viewmodel.ShopViewModel
import kotlinx.coroutines.delay

/** 쇼핑 탭의 두 가지 보기: 앱 추천 상품(관심 하트 → 옷장 '관심 옷'과 연결) / 네이버 이미지 스타일 검색 */
private enum class ShopTab(val label: String) { PRODUCTS("추천 상품"), STYLES("스타일 검색") }

@Composable
fun ShopScreen(
    onProductClick: () -> Unit,
    viewModel: ShopViewModel,
    gender: Gender? = null
) {
    val context = LocalContext.current
    val likedSet by ProductInterestStore.ids.collectAsState()
    LaunchedEffect(Unit) { ProductInterestStore.load(context) }

    // 홈에서 "전체보기"로 들어오면 홈에서 보던 검색어로 '스타일 검색'을 바로 연다
    val preset = remember { viewModel.consumePresetQuery() }
    var tab by remember { mutableStateOf(if (preset != null) ShopTab.STYLES else ShopTab.PRODUCTS) }
    var searchQuery by remember { mutableStateOf(preset ?: "") }
    var searchKeyword by remember { mutableStateOf<String?>(null) }

    // 스타일 검색: 입력 멈춘 지 400ms 지나면 검색 — 매 키입력마다 API 호출하지 않게 디바운스
    LaunchedEffect(tab, searchQuery, gender) {
        if (tab != ShopTab.STYLES) return@LaunchedEffect
        delay(400)
        viewModel.search(searchQuery, gender)
    }

    Column(modifier = Modifier.fillMaxSize().background(Cream)) {
        // 헤더
        Column(modifier = Modifier.fillMaxWidth().background(White).padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("추천 쇼핑", style = MaterialTheme.typography.headlineSmall, color = CharcoalBlack, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(if (tab == ShopTab.PRODUCTS) "상품 검색" else "스타일 검색 (예: 가을 코디)", color = MediumGray) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MediumGray) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = LightGray,
                    focusedBorderColor = Camel
                ),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShopTab.entries.forEach { t ->
                    FilterChip(
                        selected = tab == t,
                        onClick = { tab = t },
                        label = { Text(t.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Camel,
                            selectedLabelColor = White
                        )
                    )
                }
            }
        }

        // 안내 배너
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CamelLight)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                when {
                    tab == ShopTab.PRODUCTS -> "AI가 내 체형·취향을 분석해 추천한 상품이에요. 하트를 누르면 옷장 '관심 옷'에 담겨요"
                    gender != null -> "네이버 이미지 검색에서 ${gender.label} 맞춤 스타일을 가져왔어요. 탭하면 쇼핑몰에서 검색해볼 수 있어요"
                    else -> "네이버 이미지 검색 결과예요. 탭하면 쇼핑몰에서 검색해볼 수 있어요"
                },
                style = MaterialTheme.typography.bodySmall,
                color = Brown,
                fontWeight = FontWeight.Medium
            )
        }

        when (tab) {
            ShopTab.PRODUCTS -> ProductGrid(
                query = searchQuery,
                likedSet = likedSet,
                onToggleLike = { ProductInterestStore.toggle(context, it) },
                onOpen = { url ->
                    if (url.isNotEmpty()) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    else onProductClick()
                }
            )
            ShopTab.STYLES -> {
                val state by viewModel.state.collectAsState()
                StyleResults(state = state, onClick = {
                    // 이미지 제목은 블로그·SNS 글 제목이라 쇼핑몰 검색어로 쓰면 결과가 안 나온다.
                    // 사용자가 입력한 검색어(없으면 "성별 데일리룩")로 비슷한 상품을 찾게 한다.
                    searchKeyword = searchQuery.trim().ifBlank {
                        listOfNotNull(gender?.label, "데일리룩").joinToString(" ")
                    }
                })
            }
        }
    }

    searchKeyword?.let { keyword ->
        ShopSearchSheet(keyword = keyword, onDismiss = { searchKeyword = null })
    }
}

/** 앱 추천 상품 목록 — 하트를 누른 상품은 위로 (누른 순서대로) */
@Composable
private fun ProductGrid(
    query: String,
    likedSet: List<String>,
    onToggleLike: (String) -> Unit,
    onOpen: (purchaseUrl: String) -> Unit
) {
    val products = ShopCatalog.products
    val filteredProducts = remember(query) {
        if (query.isBlank()) products
        else products.filter {
            it.name.contains(query, ignoreCase = true) || it.brand.contains(query, ignoreCase = true)
        }
    }
    val displayProducts = filteredProducts.sortedBy { product ->
        val favIndex = likedSet.indexOf(product.id)
        if (favIndex >= 0) favIndex else Int.MAX_VALUE
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(displayProducts, key = { it.id }) { product ->
            val isLiked = product.id in likedSet
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem()
                    .clickable { onOpen(product.purchaseUrl) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = White),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .background(Color(product.color))
                    ) {
                        AsyncImage(
                            model = if (product.imageRes != 0) product.imageRes else product.imageUrl,
                            contentDescription = product.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        product.tag?.let {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Camel)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = White)
                            }
                        }
                        IconButton(
                            onClick = { onToggleLike(product.id) },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(
                                if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = if (isLiked) "관심 해제" else "관심 표시",
                                tint = HeartPink,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(product.brand, style = MaterialTheme.typography.labelSmall, color = MediumGray)
                        Text(product.name, style = MaterialTheme.typography.bodySmall, color = CharcoalBlack, fontWeight = FontWeight.Medium, maxLines = 2)
                        if (product.originalPrice != product.price) {
                            Text(product.originalPrice, style = MaterialTheme.typography.labelSmall, color = MediumGray, textDecoration = TextDecoration.LineThrough)
                        }
                        Text(product.price, style = MaterialTheme.typography.bodySmall, color = Camel, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(12.dp))
                            Text("${product.rating}", style = MaterialTheme.typography.labelSmall, color = CharcoalBlack)
                            Text("(${product.reviewCount})", style = MaterialTheme.typography.labelSmall, color = MediumGray)
                        }
                    }
                }
            }
        }
    }
}

/** 네이버 이미지 검색 결과 (로딩 / 오류 / 결과) */
@Composable
private fun StyleResults(state: ShopUiState, onClick: () -> Unit) {
    when (state) {
        is ShopUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Camel)
            }
        }
        is ShopUiState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MediumGray, modifier = Modifier.size(40.dp))
                    Text(state.message, style = MaterialTheme.typography.bodyMedium, color = MediumGray, textAlign = TextAlign.Center)
                }
            }
        }
        is ShopUiState.Success -> {
            if (state.results.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("검색 결과가 없어요", style = MaterialTheme.typography.bodyMedium, color = MediumGray)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(state.results) { result ->
                        ImageResultCard(result = result, onClick = onClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageResultCard(result: ShopImageResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(CreamDark)
            ) {
                OutfitPhotoImage(
                    photo = OutfitPhoto(result.imageUrl, result.thumbnailUrl), // 원본 → 실패 시 썸네일
                    contentDescription = result.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    result.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = CharcoalBlack,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2
                )
            }
        }
    }
}
