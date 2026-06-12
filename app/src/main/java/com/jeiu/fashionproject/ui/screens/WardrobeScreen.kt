package com.jeiu.fashionproject.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jeiu.fashionproject.ui.theme.*

private data class ClothingItem(val name: String, val category: String, val color: Color, val imageUrl: String)

private val allClothes = listOf(
    ClothingItem("베이지 니트", "상의", Color(0xFFEDE3D5), "https://loremflickr.com/300/380/knit,sweater?lock=40"),
    ClothingItem("화이트 블라우스", "상의", Color(0xFFF5F2EE), "https://loremflickr.com/300/380/blouse,clothing?lock=41"),
    ClothingItem("그래픽 티셔츠", "상의", Color(0xFFE8DDD0), "https://loremflickr.com/300/380/tshirt,graphic?lock=42"),
    ClothingItem("스트라이프 셔츠", "상의", Color(0xFFDDD8D2), "https://loremflickr.com/300/380/shirt,stripe?lock=43"),
    ClothingItem("와이드 팬츠", "하의", Color(0xFFCDC5BC), "https://loremflickr.com/300/380/pants,wide?lock=44"),
    ClothingItem("슬랙스", "하의", Color(0xFFB8B0A8), "https://loremflickr.com/300/380/slacks,trousers?lock=45"),
    ClothingItem("카고 팬츠", "하의", Color(0xFFCFCAC0), "https://loremflickr.com/300/380/cargo,pants?lock=46"),
    ClothingItem("미니 스커트", "하의", Color(0xFFE0D5CC), "https://loremflickr.com/300/380/skirt,mini?lock=47"),
    ClothingItem("베이지 코트", "아우터", Color(0xFFD5C8B8), "https://loremflickr.com/300/380/coat,beige?lock=48"),
    ClothingItem("데님 자켓", "아우터", Color(0xFFC8C0B5), "https://loremflickr.com/300/380/denim,jacket?lock=49"),
    ClothingItem("스니커즈", "신발", Color(0xFFF0EBE3), "https://loremflickr.com/300/380/sneakers,shoes?lock=50"),
    ClothingItem("로퍼", "신발", Color(0xFFB8A898), "https://loremflickr.com/300/380/loafer,shoes?lock=51"),
)

@Composable
fun WardrobeScreen(onProductClick: () -> Unit) {
    val categories = listOf("전체", "상의", "하의", "아우터", "신발", "액세서리")
    var selectedCategory by remember { mutableStateOf("전체") }
    val favoriteSet = remember { mutableStateListOf<String>() }
    val customClothes = remember { mutableStateListOf<ClothingItem>() }
    val allDisplayed = allClothes + customClothes

    var showAddDialog by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var newItemName by remember { mutableStateOf("") }
    var newItemCategory by remember { mutableStateOf("상의") }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { pendingUri = it; newItemName = ""; showAddDialog = true }
    }

    val filtered = if (selectedCategory == "전체") allDisplayed else allDisplayed.filter { it.category == selectedCategory }

    Column(modifier = Modifier.fillMaxSize().background(Cream)) {
        // 헤더
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("내 옷장", style = MaterialTheme.typography.headlineSmall, color = CharcoalBlack, fontWeight = FontWeight.Bold)
            Text("${allDisplayed.size}개", style = MaterialTheme.typography.bodyMedium, color = MediumGray)
        }

        // 카테고리 탭
        ScrollableTabRow(
            selectedTabIndex = categories.indexOf(selectedCategory),
            containerColor = Cream,
            contentColor = Camel,
            edgePadding = 20.dp,
            indicator = {},
            divider = {}
        ) {
            categories.forEach { cat ->
                val isSelected = cat == selectedCategory
                Tab(
                    selected = isSelected,
                    onClick = { selectedCategory = cat },
                    modifier = Modifier
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) Camel else CreamDark)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        cat,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) White else MediumGray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 의류 그리드
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(filtered) { item ->
                val isFav = item.name in favoriteSet
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = White),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .background(item.color)
                        ) {
                            AsyncImage(
                                model = item.imageUrl,
                                contentDescription = item.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = {
                                    if (isFav) favoriteSet.remove(item.name) else favoriteSet.add(item.name)
                                },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Icon(
                                    if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = "즐겨찾기",
                                    tint = if (isFav) Camel else White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(White.copy(alpha = 0.85f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(item.category, style = MaterialTheme.typography.labelSmall, color = Brown)
                            }
                        }
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(item.name, style = MaterialTheme.typography.bodySmall, color = CharcoalBlack, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // 하단 버튼
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(White)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { imagePicker.launch("image/*") },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Camel)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("의류 추가", style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = onProductClick,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Camel, contentColor = White)
                ) {
                    Text("코디 추천받기", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showAddDialog) {
        val catOptions = listOf("상의", "하의", "아우터", "신발", "액세서리")
        AlertDialog(
            onDismissRequest = { showAddDialog = false; pendingUri = null },
            title = { Text("의류 추가", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newItemName,
                        onValueChange = { newItemName = it },
                        label = { Text("이름") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Camel, focusedLabelColor = Camel)
                    )
                    Text("카테고리", style = MaterialTheme.typography.labelLarge, color = CharcoalBlack)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        catOptions.forEach { cat ->
                            FilterChip(
                                selected = newItemCategory == cat,
                                onClick = { newItemCategory = cat },
                                label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Camel,
                                    selectedLabelColor = White
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingUri?.let { uri ->
                        customClothes.add(ClothingItem(newItemName.ifBlank { "새 의류" }, newItemCategory, Color(0xFFEDE3D5), uri.toString()))
                    }
                    showAddDialog = false; pendingUri = null
                }) { Text("추가", color = Camel) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; pendingUri = null }) { Text("취소", color = MediumGray) }
            }
        )
    }
}
