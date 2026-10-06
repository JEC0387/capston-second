package com.jeiu.fashionproject.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jeiu.fashionproject.data.Gender
import com.jeiu.fashionproject.data.ShopImageResult
import com.jeiu.fashionproject.ui.components.ShopSearchSheet
import com.jeiu.fashionproject.ui.theme.*
import com.jeiu.fashionproject.ui.viewmodel.ShopUiState
import com.jeiu.fashionproject.ui.viewmodel.ShopViewModel
import kotlinx.coroutines.delay

@Composable
fun ShopScreen(
    gender: Gender? = null,
    viewModel: ShopViewModel
) {
    val state by viewModel.state.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var searchKeyword by remember { mutableStateOf<String?>(null) }

    // 입력 멈춘 지 400ms 지나면 검색 — 매 키입력마다 API 호출하지 않게 디바운스
    LaunchedEffect(searchQuery, gender) {
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
                if (gender != null) "네이버 이미지 검색에서 ${gender.label} 맞춤 스타일을 가져왔어요. 탭하면 쇼핑몰에서 검색해볼 수 있어요"
                else "네이버 이미지 검색 결과예요. 탭하면 쇼핑몰에서 검색해볼 수 있어요",
                style = MaterialTheme.typography.bodySmall,
                color = Brown,
                fontWeight = FontWeight.Medium
            )
        }

        when (val s = state) {
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
                        Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MediumGray, textAlign = TextAlign.Center)
                    }
                }
            }
            is ShopUiState.Success -> {
                if (s.results.isEmpty()) {
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
                        items(s.results) { result ->
                            ImageResultCard(
                                result = result,
                                onClick = { searchKeyword = result.title }
                            )
                        }
                    }
                }
            }
        }
    }

    searchKeyword?.let { keyword ->
        ShopSearchSheet(keyword = keyword, onDismiss = { searchKeyword = null })
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
                AsyncImage(
                    model = result.thumbnailUrl,
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
