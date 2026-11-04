package com.jeiu.fashionproject.ui.screens

import android.content.ActivityNotFoundException
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
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jeiu.fashionproject.data.ProductDetailSelection
import com.jeiu.fashionproject.data.ProductInterestStore
import com.jeiu.fashionproject.data.ShopCatalog
import com.jeiu.fashionproject.ui.theme.*

@Composable
fun ProductDetailScreen(productId: String?, onBack: () -> Unit, onProductClick: (String) -> Unit) {
    // A different selection must not retain scroll/error state from the previous product.
    key(productId) {
        ProductDetailContent(productId, onBack, onProductClick)
    }
}

@Composable
private fun ProductDetailContent(productId: String?, onBack: () -> Unit, onProductClick: (String) -> Unit) {
    val context = LocalContext.current
    val selection = remember(productId) { ProductDetailSelection.resolve(productId) }
    val likedIds by ProductInterestStore.ids.collectAsState()
    LaunchedEffect(Unit) { ProductInterestStore.load(context) }
    var actionError by remember { mutableStateOf<String?>(null) }
    val product = selection?.product
    val isFavorited = selection != null && selection.interestId in likedIds

    fun openIntent(intent: Intent) {
        actionError = null
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            actionError = "연결할 앱을 찾지 못했어요. 브라우저 또는 공유 앱을 확인해주세요."
        } catch (_: SecurityException) {
            actionError = "현재 기기에서 이 동작을 열 수 없어요."
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Cream)) {
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
                if (selection != null) {
                    IconButton(onClick = { ProductInterestStore.toggle(context, selection.interestId) }) {
                        Icon(
                            if (isFavorited) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (isFavorited) "관심 해제" else "관심 표시",
                            tint = if (isFavorited) Camel else CharcoalBlack
                        )
                    }
                    IconButton(onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, selection.shareText)
                        }
                        openIntent(Intent.createChooser(shareIntent, "공유하기"))
                    }) { Icon(Icons.Filled.Share, contentDescription = "공유", tint = CharcoalBlack) }
                }
            }
        }
        if (selection == null || product == null) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("상품을 찾을 수 없어요", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack)
                Text("목록으로 돌아가 상품을 다시 선택해주세요.", color = MediumGray)
                TextButton(onClick = onBack) { Text("다시 선택하기", color = Camel) }
            }
            return@Column
        }

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(320.dp).background(Color(product.color))) {
                AsyncImage(model = selection.image, contentDescription = product.name,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Column(Modifier.background(White).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(product.brand, style = MaterialTheme.typography.bodySmall, color = MediumGray, fontWeight = FontWeight.Medium)
                    Text(product.name, style = MaterialTheme.typography.headlineSmall, color = CharcoalBlack, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = LightGray)
                Text(product.price, style = MaterialTheme.typography.headlineMedium, color = CharcoalBlack, fontWeight = FontWeight.Bold)
                Text("앱에 등록된 카탈로그 정보예요. 실시간 가격·재고가 아니며, 최종 가격과 옵션은 판매처에서 확인해주세요.",
                    style = MaterialTheme.typography.bodySmall, color = MediumGray)
                HorizontalDivider(color = LightGray)
                Text("색상: ${product.clothingColorName ?: "판매처 확인 필요"}", color = CharcoalBlack)
                Text("판매 중인 색상·사이즈와 실제 사이즈표는 판매처에서 확인해주세요. 이 화면은 실측 핏을 보장하지 않아요.",
                    style = MaterialTheme.typography.bodySmall, color = MediumGray)
                actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.background(White).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("다른 카탈로그 상품", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(ShopCatalog.products.filter { it.id != product.id }, key = { it.id }) { item ->
                        Column(Modifier.width(120.dp).clickable { onProductClick(item.id) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AsyncImage(model = if (item.imageRes != 0) item.imageRes else item.imageUrl,
                                contentDescription = item.name, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp)).background(Color(item.color)))
                            Text(item.name, style = MaterialTheme.typography.bodySmall, color = CharcoalBlack, maxLines = 3)
                            Text(item.price, style = MaterialTheme.typography.bodySmall, color = Camel)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Column(Modifier.fillMaxWidth().background(White).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(selection.purchaseUrl?.let { Uri.parse(it).host } ?: "등록된 판매 링크가 없어요",
                style = MaterialTheme.typography.bodySmall, color = MediumGray)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { ProductInterestStore.toggle(context, selection.interestId) }, modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(12.dp)) {
                    Icon(if (isFavorited) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (isFavorited) "관심 해제" else "관심 표시", tint = Camel)
                }
                Button(onClick = { selection.purchaseUrl?.let { openIntent(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } },
                    enabled = selection.purchaseUrl != null,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Camel, contentColor = White)) {
                    Text("판매처에서 확인", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
