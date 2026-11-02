package com.jeiu.fashionproject.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.recommend.GenderFit
import com.jeiu.fashionproject.recommend.OutfitEngine
import com.jeiu.fashionproject.recommend.OutfitSources
import com.jeiu.fashionproject.recommend.ProductRecommender
import com.jeiu.fashionproject.ui.components.ClothingThumb
import com.jeiu.fashionproject.ui.components.ShopSearchSheet
import com.jeiu.fashionproject.ui.theme.*

/**
 * 상품 상세 — 쇼핑 탭에서 누른 상품을 그대로 보여 준다 (예전에는 어떤 상품이든 같은 니트 · 고정 평점이었음).
 *  - 추천 이유: ProductRecommender (내 옷장 코디 수 · 선호 스타일 · 취향)
 *  - 내 옷장 옷과 입는 코디: 이 상품을 옷장에 더했다고 치고 코디 엔진으로 만든 실제 조합
 *  - 비슷한 상품: 같은 분류의 앱 추천 상품
 * 평점 · 리뷰 · 사이즈 재고처럼 앱이 모르는 정보는 보여 주지 않고, 구매는 쇼핑몰 페이지에서 한다.
 */
@Composable
fun ProductDetailScreen(
    onBack: () -> Unit,
    productId: String? = null,
    gender: Gender? = null,
    onProduct: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val product = remember(productId) { ShopCatalog.allProducts.firstOrNull { it.id == productId } ?: ShopCatalog.detailProduct }
    LaunchedEffect(Unit) {
        ProductInterestStore.load(context); WardrobeStore.load(context)
        StylePreferenceStore.load(context); TastePreferenceStore.load(context)
    }
    ProfileStore.load(context)
    val likedIds by ProductInterestStore.ids.collectAsState()
    val wardrobe by WardrobeStore.items.collectAsState()
    val preferred by ProfileStore.styles.collectAsState()
    val learned by StylePreferenceStore.scores.collectAsState()
    val taste by TastePreferenceStore.scores.collectAsState()
    val isFavorited = product.id in likedIds
    var shopKeyword by remember { mutableStateOf<String?>(null) }

    val pick = remember(product, wardrobe, gender, preferred, learned, taste) {
        ProductRecommender.rank(listOf(product), wardrobe, null, preferred, learned, taste).firstOrNull()
    }
    // 이 상품을 옷장에 더했을 때 만들 수 있는 코디 (색 정보가 있는 상품만)
    val outfits = remember(product, wardrobe, gender) {
        val candidate = OutfitSources.candidate(product, 1L) ?: return@remember emptyList()
        val owned = GenderFit.filter(wardrobe.filter { it.isOwned }, gender)
        (OutfitEngine.recommend(owned + candidate, null, emptyList(), emptyMap(), count = 6, gender = gender)
            as? OutfitEngine.Result.Ok)?.outfits.orEmpty().filter { o -> o.items.any { it.id == candidate.id } }.take(3)
    }
    val similar = remember(product, gender) {
        ShopCatalog.products.filter { it.id != product.id && it.category == product.category &&
            GenderFit.allows(it.category, it.subCategory, it.detailType, it.name, gender) }
    }

    Column(modifier = Modifier.fillMaxSize().background(Cream)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(White).padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = CharcoalBlack) }
            Text("상품 상세", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            IconButton(onClick = { ProductInterestStore.toggle(context, product.id) }) {
                Icon(if (isFavorited) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isFavorited) "관심 해제" else "관심 표시", tint = HeartPink)
            }
            if (product.purchaseUrl.isNotBlank()) IconButton(onClick = {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "${product.name}\n${product.purchaseUrl}")
                }
                context.startActivity(Intent.createChooser(share, "상품 공유"))
            }) { Icon(Icons.Filled.Share, contentDescription = "공유", tint = CharcoalBlack) }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(360.dp).background(Color(product.color))) {
                AsyncImage(
                    model = if (product.imageRes != 0) product.imageRes else product.imageUrl,
                    contentDescription = product.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                )
            }
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(product.brand, style = MaterialTheme.typography.bodySmall, color = MediumGray, fontWeight = FontWeight.Medium)
                Text(product.name, style = MaterialTheme.typography.headlineSmall, color = CharcoalBlack, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(product.price, style = MaterialTheme.typography.headlineMedium, color = CharcoalBlack, fontWeight = FontWeight.Bold)
                    if (product.originalPrice != product.price) {
                        Text(product.originalPrice, style = MaterialTheme.typography.bodyMedium, color = MediumGray,
                            textDecoration = TextDecoration.LineThrough)
                    }
                }
                val info = listOfNotNull(product.category, product.detailType ?: product.subCategory, product.clothingColorName)
                if (info.isNotEmpty()) Text(info.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Brown)
                Text("가격은 등록 시점 기준이에요. 사이즈 · 재고 · 후기는 쇼핑몰 페이지에서 확인해 주세요.",
                    style = MaterialTheme.typography.labelSmall, color = MediumGray)
            }

            // 추천 이유
            Column(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CamelLight).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("나에게 맞는 이유", style = MaterialTheme.typography.titleSmall, color = BrownDark, fontWeight = FontWeight.Bold)
                val reasons = pick?.reasons.orEmpty()
                if (reasons.isEmpty()) Text("아직 취향 · 옷장 정보가 적어 이유를 찾지 못했어요. 옷을 등록하고 취향 학습을 해 보세요.",
                    style = MaterialTheme.typography.bodySmall, color = Brown)
                reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = Brown) }
            }

            // 내 옷장 옷과 코디
            Text("내 옷장 옷과 이렇게 입어요", style = MaterialTheme.typography.titleMedium, color = CharcoalBlack,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp))
            if (outfits.isEmpty()) {
                Text(
                    if (OutfitSources.candidate(product, 1L) == null) "이 상품은 색 정보가 없어 코디를 계산하지 못했어요."
                    else "지금 옷장으로는 이 상품과 어울리는 조합을 찾지 못했어요.",
                    style = MaterialTheme.typography.bodySmall, color = MediumGray, modifier = Modifier.padding(horizontal = 20.dp)
                )
            } else outfits.forEach { o ->
                Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    o.items.forEach { item ->
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(CreamDark)) {
                            if (item.isOwned) ClothingThumb(item, Modifier.fillMaxSize(), cutoutPadding = 4.dp)
                            else AsyncImage(model = if (product.imageRes != 0) product.imageRes else product.imageUrl,
                                contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
                o.reasons.firstOrNull()?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MediumGray, modifier = Modifier.padding(horizontal = 20.dp))
                }
            }

            // 비슷한 상품
            if (similar.isNotEmpty()) {
                Text("비슷한 상품", style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(similar, key = { it.id }) { item ->
                        Column(Modifier.width(120.dp).clickable { onProduct(item.id) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(12.dp)).background(Color(item.color))) {
                                AsyncImage(model = if (item.imageRes != 0) item.imageRes else item.imageUrl, contentDescription = item.name,
                                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            Text(item.name, style = MaterialTheme.typography.bodySmall, color = CharcoalBlack, maxLines = 2)
                            Text(item.price, style = MaterialTheme.typography.bodySmall, color = Camel, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // 하단 버튼 — 구매는 쇼핑몰에서
        Row(Modifier.fillMaxWidth().background(White).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { shopKeyword = outfitKeyword(product, gender) },
                modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(12.dp)
            ) { Text("다른 쇼핑몰 비교", color = Brown) }
            Button(
                onClick = {
                    if (product.purchaseUrl.isNotBlank()) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(product.purchaseUrl)))
                    else shopKeyword = outfitKeyword(product, gender)
                },
                modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Camel)
            ) { Text(if (product.purchaseUrl.isNotBlank()) "구매하러 가기" else "쇼핑몰에서 찾기", fontWeight = FontWeight.Bold) }
        }
    }

    shopKeyword?.let { ShopSearchSheet(keyword = it, onDismiss = { shopKeyword = null }) }
}

/** 쇼핑몰 검색어 — 성별 + 색 + 종류 (상품명 전체는 몰마다 달라 검색이 잘 안 됨) */
private fun outfitKeyword(p: ShopProduct, gender: Gender?): String =
    listOfNotNull(gender?.label, p.clothingColorName, p.detailType ?: p.subCategory ?: p.category).joinToString(" ")
        .ifBlank { p.name }
