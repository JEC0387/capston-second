package com.jeiu.fashionproject.ui.screens

import android.net.Uri
import android.content.Intent
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.saveable.rememberSaveable
import com.jeiu.fashionproject.data.ProductInterestStore
import com.jeiu.fashionproject.data.ShopCatalog
import com.jeiu.fashionproject.recommend.ClothingScope
import com.jeiu.fashionproject.recommend.OutfitSources
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.data.ClothingCategories
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.data.WardrobeStore
import com.jeiu.fashionproject.data.WearDates
import com.jeiu.fashionproject.data.WearLogStore
import com.jeiu.fashionproject.recommend.MissingItemFinder
import com.jeiu.fashionproject.ui.components.ClothingThumb
import com.jeiu.fashionproject.ui.components.UnwornAlertCard
import com.jeiu.fashionproject.ui.viewmodel.WearAnalyzer
import com.jeiu.fashionproject.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WardrobeScreen(
    onOutfitRecommend: (ClothingScope) -> Unit,
    onShop: () -> Unit,
    onProductDetail: () -> Unit,
    onAddClothing: () -> Unit = {},
    onVirtualFitting: () -> Unit = {},
    onWearLog: () -> Unit = {},          // 오늘 입은 옷 기록
    onWearCalendar: () -> Unit = {},     // 착용 기록 달력
    onEditClothing: (id: String) -> Unit = {},  // 옷 정보 수정
    onMissingItem: () -> Unit = {},             // "이것만 사면" 화면
    onMusinsaCloset: () -> Unit = {}            // 무신사 랭킹 옷장 (성별 맞춤 데모 옷 · 가상 피팅)
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        WardrobeStore.load(context)
        ProductInterestStore.load(context)
        WearLogStore.load(context)
    }
    // 샘플 옷·AI 등록 옷·직접 추가한 옷이 모두 저장소 하나에 들어 있다.
    val allDisplayed by WardrobeStore.items.collectAsState()
    val logs by WearLogStore.logs.collectAsState()
    val categories = listOf("전체") + ClothingCategories.categories
    val likedIds by ProductInterestStore.ids.collectAsState()
    val interested = ShopCatalog.interested(likedIds)
    var selectedScope by rememberSaveable { mutableStateOf(ClothingScope.ALL) }
    var showScopeDialog by remember { mutableStateOf(false) }
    var selectedCategory by rememberSaveable { mutableStateOf("전체") }
    var detailItemId by remember { mutableStateOf<String?>(null) }   // 상세 창에 보여줄 옷

    var showAddDialog by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var newItemName by remember { mutableStateOf("") }
    var newItemCategory by remember { mutableStateOf("상의") }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { pendingUri = it; newItemName = ""; showAddDialog = true }
    }

    val filtered = OutfitSources.entries(allDisplayed, interested, selectedScope, selectedCategory)

    // 보유 옷 즐겨찾기는 기존 순서대로 위쪽에 유지한다.
    // 관심상품 하트와 구분하도록 보유 옷 즐겨찾기는 별 표시를 사용한다.
    val displayList = filtered.sortedBy { it.owned?.favoritedAt ?: Long.MAX_VALUE }

    Column(modifier = Modifier.fillMaxSize().background(Cream)) {
        // 헤더
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("내 옷장", style = MaterialTheme.typography.headlineSmall, color = CharcoalBlack, fontWeight = FontWeight.Bold)
                Text("${OutfitSources.entries(allDisplayed, interested, selectedScope).size}개", style = MaterialTheme.typography.bodyMedium, color = MediumGray)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onWearCalendar) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = "착용 기록 달력", tint = Brown)
                }
                FilledTonalButton(
                    onClick = onWearLog,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = CamelLight, contentColor = Brown)
                ) {
                    Icon(Icons.Filled.Checkroom, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("오늘 입은 옷", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // 3개월 넘게 안 입은 옷 알림 (해당 옷이 있을 때만)
        val unworn = remember(allDisplayed, logs) { WearAnalyzer.unworn(allDisplayed, logs) }
        UnwornAlertCard(
            result = unworn,
            onDelete = { WardrobeStore.remove(context, it.id) },
            onKeep = { WardrobeStore.keep(context, it.id) },
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp)
        )

        // "이것만 사면 코디 완성" 요약 (계획 4 — 5단계). 계산은 화면이 멈추지 않게 따로 한다
        var missingReport by remember { mutableStateOf<MissingItemFinder.Report?>(null) }
        LaunchedEffect(allDisplayed) {
            missingReport = withContext(Dispatchers.Default) { MissingItemFinder.analyze(allDisplayed) }
        }
        missingReport?.let { r ->
            BuyThisCard(r, onClick = onMissingItem, modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp))
        }

        // 무신사 랭킹 옷장으로 들어가는 카드
        Card(
            onClick = onMusinsaCloset,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CamelLight)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.ShoppingBag, contentDescription = null, tint = Brown, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("무신사 랭킹 옷장", style = MaterialTheme.typography.labelLarge, color = Brown, fontWeight = FontWeight.SemiBold)
                    Text("20대 인기 상품으로 날씨 코디 · 가상 피팅 해보기", style = MaterialTheme.typography.labelSmall, color = Brown)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Brown)
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClothingScope.entries.forEach { scope ->
                FilterChip(selected = selectedScope == scope, onClick = { selectedScope = scope },
                    label = { Text(scope.label) })
            }
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

        // Scope and category filters share one projection of the current interest state.
        if (displayList.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text(OutfitSources.emptyMessage(selectedScope, selectedCategory), color = MediumGray)
                if (selectedScope != ClothingScope.INTERESTED) {
                    TextButton(onClick = onAddClothing) { Text("옷 등록하기") }
                }
                if (selectedScope != ClothingScope.OWNED) {
                    TextButton(onClick = onShop) { Text("쇼핑 둘러보기") }
                }
            }
        } else LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(displayList, key = { entry -> entry.key }) { entry ->
                val product = entry.product
                if (product != null) {
                    Card(onClick = {
                        if (product.purchaseUrl.isNotBlank()) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(product.purchaseUrl)))
                        else onProductDetail()
                    }, modifier = Modifier.fillMaxWidth().animateItem(), shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = White)) {
                        Box(Modifier.fillMaxWidth().height(150.dp).background(Color(product.color))) {
                            AsyncImage(model = if (product.imageRes != 0) product.imageRes else product.imageUrl,
                                contentDescription = product.name, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize())
                            IconButton(onClick = { ProductInterestStore.toggle(context, product.id) },
                                modifier = Modifier.align(Alignment.TopEnd)) {
                                Icon(Icons.Filled.Favorite, contentDescription = "관심 해제", tint = HeartPink)
                            }
                        }
                        Column(Modifier.padding(12.dp)) {
                            Text("♥ 관심 옷 · ${product.category}", color = HeartPink, style = MaterialTheme.typography.labelSmall)
                            Text(product.name, color = CharcoalBlack, style = MaterialTheme.typography.bodySmall)
                            if (OutfitSources.candidate(product, 1L) == null) {
                                Text("추천에 필요한 정보가 부족해요", color = MediumGray,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    return@items
                }
                val item = entry.owned!!
                val isFav = item.favoritedAt != null
                Card(
                    onClick = { detailItemId = item.id },
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = White),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column {
                        ClothingThumb(
                            item = item,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                        ) {
                            IconButton(
                                onClick = { WardrobeStore.toggleFavorite(context, item.id) },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Icon(
                                    if (isFav) Icons.Filled.Star else Icons.Filled.StarBorder,
                                    contentDescription = "내 옷 즐겨찾기",
                                    tint = HeartPink,
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
                                // 세부 종류(예: 청바지) → 소분류(예: 긴바지) → 대분류 순으로 가장 자세한 이름 표시
                                Text(item.detailType ?: item.subCategory ?: item.category, style = MaterialTheme.typography.labelSmall, color = Brown)
                            }
                        }
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("내 옷", style = MaterialTheme.typography.labelSmall, color = MediumGray)
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
                    onClick = onAddClothing,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Camel)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        "AI 등록",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                OutlinedButton(
                    onClick = onVirtualFitting,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Brown)
                ) {
                    Text(
                        "가상 피팅",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Button(
                    onClick = { showScopeDialog = true },
                    modifier = Modifier.weight(1.4f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Camel, contentColor = White)
                ) {
                    Text(
                        "코디 추천받기",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }

    if (showScopeDialog) {
        AlertDialog(onDismissRequest = { showScopeDialog = false }, title = { Text("추천에 사용할 옷") },
            text = {
                Column {
                    listOf(ClothingScope.OWNED, ClothingScope.ALL, ClothingScope.INTERESTED).forEach { scope ->
                        TextButton(onClick = { showScopeDialog = false; onOutfitRecommend(scope) }) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(scope.recommendationLabel, fontWeight = FontWeight.Bold, color = Brown)
                                Text(scope.description, style = MaterialTheme.typography.bodySmall, color = MediumGray)
                            }
                        }
                    }
                }
            }, confirmButton = {}, dismissButton = {
                TextButton(onClick = { showScopeDialog = false }) { Text("취소") }
            })
    }

    // 옷 상세 (옷 칸을 누르면 아래에서 올라옴)
    allDisplayed.find { it.id == detailItemId }?.let { item ->
        ClothingDetailSheet(
            item = item,
            stat = WearAnalyzer.statOf(logs, item.id),
            onEdit = {
                detailItemId = null
                onEditClothing(item.id)
            },
            onDelete = {
                WardrobeStore.remove(context, item.id)
                detailItemId = null
            },
            onDismiss = { detailItemId = null }
        )
    }

    if (showAddDialog) {
        val catOptions = ClothingCategories.categories
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
                        WardrobeStore.add(
                            context,
                            WardrobeItem(
                                name = newItemName.ifBlank { "새 의류" },
                                category = newItemCategory,
                                colorName = "베이지",
                                colorValue = 0xFFEDE3D5,
                                imageUri = uri.toString()
                            )
                        )
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

/** 옷 상세 창 — 분류·계절, 착용 횟수·마지막 착용일, 옷장에서 삭제. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClothingDetailSheet(
    item: WardrobeItem,
    stat: WearAnalyzer.WearStat,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = White) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                ClothingThumb(
                    item = item, cutoutPadding = 8.dp,
                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp))
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.Bold)
                    Text(
                        listOfNotNull(item.category, item.subCategory, item.detailType, item.season).distinct().joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MediumGray
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(Color(item.colorValue)))
                        Text(item.colorName, style = MaterialTheme.typography.bodySmall, color = MediumGray)
                    }
                }
            }

            // 착용 정보
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CreamDark).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.Checkroom, contentDescription = null, tint = Camel)
                Text(
                    if (stat.lastWorn == null) "아직 한 번도 기록하지 않았어요"
                    else "착용 ${stat.count}회 · 마지막 착용 ${WearDates.ago(stat.lastWorn)}\n${WearDates.label(stat.lastWorn)}",
                    style = MaterialTheme.typography.bodyMedium, color = Brown
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onEdit,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Camel, contentColor = White),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("정보 수정")
                }
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = MediumGray, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("옷장에서 삭제", color = MediumGray)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("옷 삭제") },
            text = { Text("'${item.name}'을(를) 옷장에서 지울까요?\n착용 기록은 남지만 달력에서는 보이지 않아요.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("삭제", color = HeartPink) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("취소", color = MediumGray) }
            }
        )
    }
}

/** 옷장 위 "이것만 사면" 한 줄 카드 — 누르면 자세히 보기. 추천도 외톨이 옷도 없으면 그리지 않는다 */
@Composable
private fun BuyThisCard(report: MissingItemFinder.Report, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val top = report.suggestions.firstOrNull()
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CamelLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Filled.ShoppingBag, contentDescription = null, tint = Brown, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                if (top == null) Text("부족한 아이템 살펴보기", color = BrownDark)
                top?.let {
                    Text(
                        "${it.item.name} 하나만 사면 코디 ${it.gain}개가 더 생겨요",
                        style = MaterialTheme.typography.bodyMedium, color = BrownDark, fontWeight = FontWeight.SemiBold
                    )
                }
                if (report.orphans.isNotEmpty()) {
                    Text("어울리는 짝이 없는 옷 ${report.orphans.size}벌", style = MaterialTheme.typography.bodySmall, color = Brown)
                }
            }
            Text("자세히", style = MaterialTheme.typography.labelLarge, color = Camel)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Camel, modifier = Modifier.size(18.dp))
        }
    }
}
