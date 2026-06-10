package com.jeiu.fashionproject.ui.screens

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
    val imageUrl: String = ""
)

private val products = listOf(
    Product("오버사이즈 베이지 니트", "ZARA", "43,000원", "69,000원", 4.8f, 284, Color(0xFFEDE3D5), "AI추천", "https://loremflickr.com/400/500/knit,sweater?lock=30"),
    Product("린넨 와이드 팬츠", "H&M", "58,000원", "79,000원", 4.6f, 156, Color(0xFFD5CABD), null, "https://loremflickr.com/400/500/pants,linen?lock=31"),
    Product("클래식 코튼 셔츠", "UNIQLO", "39,000원", "39,000원", 4.9f, 512, Color(0xFFF0EBE3), "베스트", "https://loremflickr.com/400/500/shirt,cotton?lock=32"),
    Product("미디 플리츠 스커트", "COS", "79,000원", "99,000원", 4.5f, 98, Color(0xFFE0D5CC), null, "https://loremflickr.com/400/500/skirt,fashion?lock=33"),
    Product("카멜 울 코트", "MANGO", "159,000원", "229,000원", 4.7f, 203, Color(0xFFD5C8B8), "AI추천", "https://loremflickr.com/400/500/coat,fashion?lock=34"),
    Product("데님 스트레이트 팬츠", "LEVIS", "89,000원", "119,000원", 4.4f, 341, Color(0xFFC8C0B5), null, "https://loremflickr.com/400/500/denim,jeans?lock=35"),
)

@Composable
fun ShopScreen(onProductClick: () -> Unit) {
    val likedSet = remember { mutableStateListOf<String>() }

    Column(modifier = Modifier.fillMaxSize().background(Cream)) {
        // 헤더
        Column(modifier = Modifier.fillMaxWidth().background(White).padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("추천 쇼핑", style = MaterialTheme.typography.headlineSmall, color = CharcoalBlack, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = "",
                onValueChange = {},
                placeholder = { Text("상품 검색", color = MediumGray) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MediumGray) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
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
            items(products) { product ->
                val isLiked = product.name in likedSet
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onProductClick),
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
                                model = product.imageUrl,
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
                                    tint = if (isLiked) Camel else White,
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
