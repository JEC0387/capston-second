package com.jeiu.fashionproject.ui.screens

import android.content.Intent
import android.net.Uri
import com.jeiu.fashionproject.ui.components.ShopSearchSheet
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
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
import com.jeiu.fashionproject.R
import com.jeiu.fashionproject.ui.theme.*

private val colorOptions = listOf(
    Pair("베이지", Color(0xFFEDE3D5)),
    Pair("화이트", Color(0xFFF5F2EE)),
    Pair("그레이", Color(0xFFCDC9C3)),
    Pair("블랙", Color(0xFF3A3A3A)),
)
private val sizeOptions = listOf("XS", "S", "M", "L", "XL")

private data class SimilarProduct(val name: String, val price: String, val bgColor: Color, val imageUrl: String)

private val similarProducts = listOf(
    SimilarProduct("크롭 니트", "32,000원", Color(0xFFE8DDD0), "https://loremflickr.com/240/300/crop,knit?lock=60"),
    SimilarProduct("터틀넥 니트", "55,000원", Color(0xFFD5CABD), "https://loremflickr.com/240/300/turtleneck,sweater?lock=61"),
    SimilarProduct("V넥 니트", "48,000원", Color(0xFFEDE3D5), "https://loremflickr.com/240/300/vneck,knit?lock=62"),
    SimilarProduct("카디건", "67,000원", Color(0xFFCDC5BC), "https://loremflickr.com/240/300/cardigan,fashion?lock=63"),
)

@Composable
fun ProductDetailScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedColor by remember { mutableStateOf("베이지") }
    var selectedSize by remember { mutableStateOf("M") }
    var showShopSheet by remember { mutableStateOf(false) }
    var isFavorited by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Cream)) {
        // 상단 바
        Row(
            modifier = Modifier.fillMaxWidth().background(White).padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로", tint = CharcoalBlack)
            }
            Text("상품 상세", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
            Row {
                IconButton(onClick = { isFavorited = !isFavorited }) {
                    Icon(
                        if (isFavorited) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "찜",
                        tint = if (isFavorited) Camel else CharcoalBlack
                    )
                }
                IconButton(onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "아이보리 라이트 베이지 브이넥 니트 J19\nhttps://zigzag.kr/catalog/products/170263336")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "공유하기"))
                }) { Icon(Icons.Filled.Share, contentDescription = "공유", tint = CharcoalBlack) }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // 상품 메인 이미지
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(Color(0xFFEDE3D5))
            ) {
                AsyncImage(
                    model = R.drawable.casual_daily_look,
                    contentDescription = "상품 이미지",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Camel)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("AI 추천", style = MaterialTheme.typography.labelSmall, color = White)
                }
            }

            Column(
                modifier = Modifier
                    .background(White)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 브랜드 & 상품명
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("저스트메리드", style = MaterialTheme.typography.bodySmall, color = MediumGray, fontWeight = FontWeight.Medium)
                    Text("아이보리 라이트 베이지 브이넥 니트 J19", style = MaterialTheme.typography.headlineSmall, color = CharcoalBlack, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(5) { i ->
                            Icon(Icons.Filled.Star, contentDescription = null, tint = if (i < 4) Color(0xFFFFC107) else LightGray, modifier = Modifier.size(14.dp))
                        }
                        Text("4.8", style = MaterialTheme.typography.bodySmall, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
                        Text("(284개 리뷰)", style = MaterialTheme.typography.bodySmall, color = MediumGray)
                    }
                }

                HorizontalDivider(color = LightGray)

                // 가격
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("43,800원", style = MaterialTheme.typography.headlineMedium, color = CharcoalBlack, fontWeight = FontWeight.Bold)
                }

                HorizontalDivider(color = LightGray)

                // 색상 선택
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("색상: $selectedColor", style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        colorOptions.forEach { (name, color) ->
                            val isSelected = name == selectedColor
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(if (isSelected) Modifier.border(2.dp, Camel, CircleShape) else Modifier)
                                    .clickable { selectedColor = name }
                            )
                        }
                    }
                }

                // 사이즈 선택
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("사이즈: $selectedSize", style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sizeOptions.forEach { size ->
                            val isSelected = size == selectedSize
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Camel else CreamDark)
                                    .border(1.dp, if (isSelected) Camel else LightGray, RoundedCornerShape(8.dp))
                                    .clickable { selectedSize = size }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(size, style = MaterialTheme.typography.bodySmall, color = if (isSelected) White else CharcoalBlack, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 비슷한 상품
            Column(
                modifier = Modifier.background(White).padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("비슷한 상품", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(similarProducts) { item ->
                        Column(modifier = Modifier.width(120.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(item.bgColor)
                            ) {
                                AsyncImage(
                                    model = item.imageUrl,
                                    contentDescription = item.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Text(item.name, style = MaterialTheme.typography.bodySmall, color = CharcoalBlack, fontWeight = FontWeight.Medium)
                            Text(item.price, style = MaterialTheme.typography.bodySmall, color = Camel, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 하단 구매 버튼
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(White)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "zigzag.kr · 저스트메리드 공식",
                style = MaterialTheme.typography.bodySmall,
                color = MediumGray
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { isFavorited = !isFavorited },
                    modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isFavorited) Camel else CharcoalBlack)
                ) {
                    Icon(
                        if (isFavorited) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Button(
                    onClick = { showShopSheet = true },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Camel, contentColor = White)
                ) {
                    Text("쇼핑에서 찾기", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showShopSheet) {
        ShopSearchSheet(
            keyword = "아이보리 라이트 베이지 브이넥 니트",
            onDismiss = { showShopSheet = false }
        )
    }
}
