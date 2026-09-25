package com.jeiu.fashionproject.ui.screens

import android.content.Intent
import android.net.Uri
import com.jeiu.fashionproject.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jeiu.fashionproject.ui.theme.*

private data class Product(
    val name: String,
    val brand: String,
    val price: String,
    val originalPrice: String,
    val rating: Float,
    val reviewCount: Int,
    val color: Color,
    val tag: String? = null,
    val imageUrl: String = "",
    val purchaseUrl: String = "",
    val imageRes: Int = 0
)

private val products = listOf(
    Product("CKS 스탠다드 슬림핏 반팔 셔츠 네이비", "CHICKS", "39,000원", "39,000원", 4.7f, 184, Color(0xFFD5E0EC), "AI추천", "", "https://www.musinsa.com/products/6405693", R.drawable.shop_cks_shirt),
    Product("사이드 핀턱 빈티지 워싱 벌룬핏 데님 팬츠", "미스터제이슨", "8,522원", "22,000원", 4.5f, 231, Color(0xFFA8B8C8), null, "https://d3ha2047wt6x28.cloudfront.net/hEmVfMXLFDQ/pr:GOODS_DETAIL/czM6Ly9hYmx5LWltYWdlLWxlZ2FjeS9kYXRhL2dvb2RzLzIwMjYwMjI2XzE3NzIwNjgyMzQ2NTQ5NjRtLmpwZw", "https://4910.kr/desktop/goods/51479882"),
    Product("[made]썸머크루즈가디건", "어텀", "26,560원", "35,000원", 4.6f, 98, Color(0xFFEDE3D5), "AI추천", "https://cf.product-image.s.zigzag.kr/original/c/17/026/333/170263336-3421640085957955899.jpeg?width=400&height=400&quality=80&format=webp", "https://zigzag.kr/catalog/products/170263336"),
    Product("아치스 조리샌들_블랙", "ARCHIES", "35,000원", "35,000원", 4.8f, 342, Color(0xFF3A3A3A), "베스트", "", "https://www.musinsa.com/products/3142059", R.drawable.shop_archies_sandal),
    Product("백 사틴 오피서 팬츠 [블랙]", "무신사스탠다드", "59,900원", "59,900원", 4.8f, 512, Color(0xFF4A4A4A), null, "", "https://www.musinsa.com/products/3304653", R.drawable.shop_officer_pants),
    Product("[MADE]러블린셔링블라우스 - 4color", "Ni:some", "32,200원", "42,900원", 4.9f, 284, Color(0xFFF5EEE8), "AI추천", "https://cf.product-image.s.zigzag.kr/original/d/2026/4/27/5875_202604271053470543_53810.jpeg?width=400&height=400&quality=80&format=webp", "https://zigzag.kr/catalog/products/161164288"),
    Product("바이오워싱 크롭 데님 반팔 셔츠", "DIMITRI BLACK", "49,000원", "49,000원", 4.4f, 156, Color(0xFFA8C0D0), null, "", "https://www.musinsa.com/products/6317950", R.drawable.shop_denim_shirt),
    Product("8부 원턱 버뮤다 스웨트 팬츠 3color", "NANDE", "49,000원", "49,000원", 4.5f, 203, Color(0xFFD5D0C8), null, "", "https://www.musinsa.com/products/6068184", R.drawable.shop_sweat_pants),
)

@Composable
fun ShopScreen(onProductClick: () -> Unit) {
    val context = LocalContext.current
    val likedSet = remember { mutableStateListOf<String>() }
    var searchQuery by remember { mutableStateOf("") }
    val filteredProducts = remember(searchQuery) {
        if (searchQuery.isBlank()) products
        else products.filter {
            it.name.contains(searchQuery, ignoreCase = true) || it.brand.contains(searchQuery, ignoreCase = true)
        }
    }

    // 하트를 누른 상품은 상단으로, 해제하면 원래 자리로 (누른 순서대로 위쪽 배치)
    val displayProducts = filteredProducts.sortedBy { product ->
        val favIndex = likedSet.indexOf(product.name)
        if (favIndex >= 0) favIndex else Int.MAX_VALUE
    }

    Column(modifier = Modifier.fillMaxSize().background(Cream)) {
        // 헤더
        Column(modifier = Modifier.fillMaxWidth().background(White).padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("추천 쇼핑", style = MaterialTheme.typography.headlineSmall, color = CharcoalBlack, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("상품 검색", color = MediumGray) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MediumGray) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = LightGray,
                    focusedBorderColor = Camel
                ),
                singleLine = true
            )
        }

        // AI 추천 배너
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CamelLight)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                "AI가 내 체형·취향을 분석해 추천한 상품이에요",
                style = MaterialTheme.typography.bodySmall,
                color = Brown,
                fontWeight = FontWeight.Medium
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(displayProducts, key = { it.name }) { product ->
                val isLiked = product.name in likedSet
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .clickable {
                            if (product.purchaseUrl.isNotEmpty())
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(product.purchaseUrl)))
                            else
                                onProductClick()
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = White),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .background(product.color)
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
                                onClick = { if (isLiked) likedSet.remove(product.name) else likedSet.add(product.name) },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Icon(
                                    if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = null,
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
}
