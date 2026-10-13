package com.jeiu.fashionproject.ui.screens

import android.content.Intent
import android.net.Uri
import com.jeiu.fashionproject.data.ShopCatalog
import com.jeiu.fashionproject.data.ProductInterestStore
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


@Composable
fun ShopScreen(onProductClick: () -> Unit) {
    val context = LocalContext.current
    val likedSet by ProductInterestStore.ids.collectAsState()
    LaunchedEffect(Unit) { ProductInterestStore.load(context) }
    val products = ShopCatalog.products
    var searchQuery by remember { mutableStateOf("") }
    val filteredProducts = remember(searchQuery) {
        if (searchQuery.isBlank()) products
        else products.filter {
            it.name.contains(searchQuery, ignoreCase = true) || it.brand.contains(searchQuery, ignoreCase = true)
        }
    }

    // 하트를 누른 상품은 상단으로, 해제하면 원래 자리로 (누른 순서대로 위쪽 배치)
    val displayProducts = filteredProducts.sortedBy { product ->
        val favIndex = likedSet.indexOf(product.id)
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
            items(displayProducts, key = { it.id }) { product ->
                val isLiked = product.id in likedSet
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
                                onClick = { ProductInterestStore.toggle(context, product.id) },
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
}
