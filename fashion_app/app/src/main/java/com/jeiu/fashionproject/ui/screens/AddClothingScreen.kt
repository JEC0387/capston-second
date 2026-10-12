package com.jeiu.fashionproject.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.jeiu.fashionproject.data.ClothingCategories
import com.jeiu.fashionproject.data.StylePreferenceStore
import com.jeiu.fashionproject.data.WardrobeStore
import com.jeiu.fashionproject.recommend.StyleTagger
import com.jeiu.fashionproject.ui.theme.*
import com.jeiu.fashionproject.ui.viewmodel.AddClothingUiState
import com.jeiu.fashionproject.ui.viewmodel.AddClothingViewModel
import com.jeiu.fashionproject.ui.viewmodel.ImageMode

/**
 * AI 옷 등록 / 옷 정보 수정 화면 (5주차 보고서: 원터치 가상 옷장 구축 / 계획 3 — 옷장).
 *
 * 등록: 사진 선택 → 누끼(배경 제거) → 서버 DeepFashion2로 옷 종류 분류 → 고른 옷만 오려내기 + 색상 추출 →
 *       사용자가 사진 종류·분류·세부 종류·계절을 확인/수정 후 옷장에 추가. 서버 미연결 시 종류를 직접 고른다.
 * 수정([editItemId]가 있을 때): 저장된 옷을 같은 화면에 불러와 고친다.
 *       사진 종류는 저장해 둔 버전(옷만·배경 제거·원본) 중에서만 고를 수 있다.
 */
@Composable
fun AddClothingScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    editItemId: String? = null,
    viewModel: AddClothingViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val tagged = state as? AddClothingUiState.Tagged
    val isEdit = editItemId != null

    var category by remember { mutableStateOf("상의") }
    var subCategory by remember { mutableStateOf<String?>(null) }
    var detailType by remember { mutableStateOf<String?>(null) }
    var imageMode by remember { mutableStateOf(ImageMode.ORIGINAL) }
    // 사용자가 직접 고친 값 (null이면 자동 값 사용)
    var nameOverride by remember { mutableStateOf<String?>(null) }
    var seasonOverride by remember { mutableStateOf<String?>(null) }
    var styleOverride by remember { mutableStateOf<String?>(null) }
    // 수정 화면에서는 AI 결과로 입력값을 덮어쓰지 않다가, 사용자가 AI 후보를 직접 누를 때만 반영한다
    var applyAiOnce by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.tagImage(context, it) }
    }
    // 수정 모드: 저장된 옷을 불러와 입력칸을 채운다
    LaunchedEffect(editItemId) {
        viewModel.attach(context)
        if (editItemId == null || viewModel.editingItem?.id == editItemId) return@LaunchedEffect
        WardrobeStore.load(context)
        val item = WardrobeStore.items.value.find { it.id == editItemId } ?: return@LaunchedEffect onBack()
        viewModel.startEdit(context, item)
        category = item.category
        subCategory = item.subCategory
        detailType = item.detailType
        nameOverride = item.name
        seasonOverride = item.season
        styleOverride = item.style
    }
    // 등록 모드에서 새 사진이거나, AI 후보를 바꾸면 분류를 AI 결과로 채운다
    LaunchedEffect(tagged?.originalUri, tagged?.selected) {
        val s = tagged ?: return@LaunchedEffect
        if (isEdit && !applyAiOnce) return@LaunchedEffect
        applyAiOnce = false
        val mapping = AddClothingViewModel.mappingOf(s.selectedCandidate)
        if (isEdit && mapping == null) return@LaunchedEffect
        category = mapping?.category ?: "상의"
        subCategory = mapping?.subCategory
        detailType = mapping?.detailType
        if (!isEdit) nameOverride = null
        seasonOverride = null
    }
    // 옷만 오려내기가 끝나면 추천 사진 종류로 바꿔 보여준다
    LaunchedEffect(tagged?.originalUri, tagged?.defaultMode) {
        tagged?.let { imageMode = it.defaultMode }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
            .verticalScroll(rememberScrollState())
    ) {
        // 헤더
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = CharcoalBlack)
            }
            Text(
                if (isEdit) "옷 정보 수정" else "AI 옷 등록",
                style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold
            )
        }

        when (val s = state) {
            is AddClothingUiState.Idle -> {
                if (isEdit) {
                    // 수정할 옷을 불러오는 중
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Camel)
                    }
                } else {
                    PhotoPickIntro(onPick = { imagePicker.launch("image/*") })
                }
            }

            is AddClothingUiState.Tagging -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = Camel)
                    Text("배경을 지우고 옷을 분석하고 있어요…", style = MaterialTheme.typography.bodyMedium, color = MediumGray)
                }
            }

            is AddClothingUiState.Tagged -> {
                val color = s.colors[imageMode] ?: s.colors.values.firstOrNull()
                val colorName = color?.name ?: "베이지"
                val autoName = AddClothingViewModel.suggestedName(colorName, category, subCategory, detailType)
                val autoSeason = ClothingCategories.season(subCategory, detailType)
                val autoStyle = StyleTagger.auto(nameOverride ?: autoName, category, subCategory, detailType, color?.value ?: 0xFFB5A38F)

                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 미리보기 — 누끼는 크림색 바탕 가운데에 옷 전체, 원본은 칸을 꽉 채움
                    val uri = s.uriFor(imageMode) ?: s.availableModes().firstNotNullOfOrNull { s.uriFor(it) }
                    val isCut = imageMode != ImageMode.ORIGINAL && s.uriFor(imageMode) != null
                    AsyncImage(
                        model = uri,
                        contentDescription = "옷 사진",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isCut) CreamDark else LightGray)
                            .padding(if (isCut) 16.dp else 0.dp),
                        contentScale = if (isCut) ContentScale.Fit else ContentScale.Crop
                    )

                    // 사진 종류: 옷만 / 배경 제거 / 원본 (있는 것만)
                    val modes = buildList {
                        if (s.garmentUri != null || s.garmentLoading) add(ImageMode.GARMENT to "옷만")
                        if (s.cutoutUri != null) add(ImageMode.CUTOUT to "배경 제거")
                        if (s.originalUri != null) add(ImageMode.ORIGINAL to "원본")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        modes.forEach { (mode, label) ->
                            val loading = mode == ImageMode.GARMENT && s.garmentLoading
                            FilterChip(
                                selected = imageMode == mode,
                                enabled = !loading,
                                onClick = { imageMode = mode },
                                label = { Text(if (loading) "$label…" else label) },
                                colors = chipColors()
                            )
                        }
                    }
                    if (s.analyzed && s.cutoutUri == null) {
                        Text(
                            "배경 제거를 사용할 수 없어 원본 사진으로 등록해요.",
                            style = MaterialTheme.typography.bodySmall, color = MediumGray
                        )
                    }

                    // AI 분류 결과 배지
                    val aiFound = s.fromServer && s.candidates.isNotEmpty()
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (aiFound) CamelLight else LightGray),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                when {
                                    !s.analyzed -> Icons.Filled.Edit
                                    s.fromServer -> Icons.Filled.AutoAwesome
                                    else -> Icons.Filled.CloudOff
                                },
                                contentDescription = null, tint = if (aiFound) Camel else MediumGray, modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    when {
                                        !s.analyzed -> "저장된 옷 정보를 고쳐요"
                                        aiFound -> "패션 AI 분류 완료"
                                        s.fromServer -> "AI가 옷을 찾지 못했어요 — 직접 골라주세요"
                                        else -> "오프라인 — 종류를 직접 골라주세요"
                                    },
                                    style = MaterialTheme.typography.labelLarge, color = CharcoalBlack, fontWeight = FontWeight.SemiBold
                                )
                                Text("대표 색상: $colorName", style = MaterialTheme.typography.bodySmall, color = MediumGray)
                            }
                            Spacer(Modifier.weight(1f))
                            color?.let { Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Color(it.value))) }
                        }
                    }

                    // AI가 찾은 옷 후보 (여러 벌이 잡히면 바꿔 선택)
                    if (aiFound) {
                        Label("AI가 찾은 옷")
                        ChipRow {
                            s.candidates.forEachIndexed { i, c ->
                                val nameKo = ClothingCategories.fromModelLabel(c.label)?.nameKo ?: c.label
                                FilterChip(
                                    selected = s.selected == i,
                                    onClick = {
                                        if (s.selected != i) {
                                            applyAiOnce = true
                                            viewModel.selectCandidate(context, i)
                                        }
                                    },
                                    label = { Text("$nameKo ${(c.confidence * 100).toInt()}%") },
                                    colors = chipColors()
                                )
                            }
                        }
                        if (isEdit) {
                            Text(
                                "후보를 누르면 그 옷으로 다시 오려내고, 카테고리·종류도 AI 결과로 바꿔요.",
                                style = MaterialTheme.typography.bodySmall, color = MediumGray
                            )
                        }
                    }

                    // 이름 수정 (고치지 않으면 색 + 종류로 자동)
                    OutlinedTextField(
                        value = nameOverride ?: autoName,
                        onValueChange = { nameOverride = it },
                        label = { Text("아이템 이름") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Camel, focusedLabelColor = Camel)
                    )

                    // 카테고리 → 소분류 → 세부 종류
                    Label("카테고리")
                    ChipRow {
                        ClothingCategories.categories.forEach { c ->
                            FilterChip(
                                selected = category == c,
                                onClick = {
                                    if (category != c) { category = c; subCategory = null; detailType = null }
                                },
                                label = { Text(c) },
                                colors = chipColors()
                            )
                        }
                    }

                    ClothingCategories.subCategories[category]?.let { subs ->
                        Label("종류")
                        ChipRow {
                            subs.forEach { sub ->
                                FilterChip(
                                    selected = subCategory == sub,
                                    onClick = {
                                        if (subCategory != sub) { subCategory = sub; detailType = null }
                                    },
                                    label = { Text(sub) },
                                    colors = chipColors()
                                )
                            }
                        }
                    }

                    ClothingCategories.detailTypes[subCategory]?.let { details ->
                        Label("세부 종류 (선택)")
                        ChipRow {
                            details.forEach { d ->
                                FilterChip(
                                    selected = detailType == d,
                                    // 한 번 더 누르면 선택 해제
                                    onClick = { detailType = if (detailType == d) null else d },
                                    label = { Text(d) },
                                    colors = chipColors()
                                )
                            }
                        }
                    }

                    // 계절 (규칙으로 자동, 수정 가능)
                    Label("계절")
                    ChipRow {
                        ClothingCategories.seasons.forEach { season ->
                            FilterChip(
                                selected = (seasonOverride ?: autoSeason) == season,
                                onClick = { seasonOverride = season },
                                label = { Text(season) },
                                colors = chipColors()
                            )
                        }
                    }

                    // 스타일 (옷 종류·색으로 자동, 수정 가능) — 코디 추천에 쓰인다
                    Label("스타일")
                    ChipRow {
                        StylePreferenceStore.styles.forEach { style ->
                            FilterChip(
                                selected = (styleOverride ?: autoStyle) == style,
                                // 자동으로 골라진 스타일을 다시 누르면 자동으로 되돌린다
                                onClick = { styleOverride = if (style == autoStyle) null else style },
                                label = { Text(style) },
                                colors = chipColors()
                            )
                        }
                    }
                    if (styleOverride == null) {
                        Text("옷 종류와 색을 보고 자동으로 골랐어요. 다른 스타일을 눌러 바꿀 수 있어요.", style = MaterialTheme.typography.bodySmall, color = MediumGray)
                    }

                    // 저장 / 다시 선택(등록) · 취소(수정)
                    Button(
                        onClick = {
                            val name = nameOverride ?: autoName
                            val season = seasonOverride ?: autoSeason
                            if (isEdit) viewModel.saveEdit(context, name, category, subCategory, detailType, season, styleOverride, imageMode)
                            else viewModel.save(context, name, category, subCategory, detailType, season, styleOverride, imageMode)
                            onDone()
                        },
                        enabled = !s.garmentLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = Camel),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) { Text(if (isEdit) "수정 완료" else "옷장에 추가하기") }

                    OutlinedButton(
                        onClick = { if (isEdit) onBack() else viewModel.reset(context) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text(if (isEdit) "취소" else "다른 사진 선택", color = Brown) }

                    Spacer(Modifier.height(24.dp))
                }
            }

            is AddClothingUiState.Error -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MediumGray)
                    Button(
                        onClick = { if (isEdit) onBack() else viewModel.reset(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = Camel)
                    ) {
                        Text(if (isEdit) "돌아가기" else "다시 시도")
                    }
                }
            }
        }
    }
}

/** 등록 첫 화면 — 사진 선택 안내와 촬영 팁 */
@Composable
private fun PhotoPickIntro(onPick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier.size(96.dp).clip(CircleShape).background(CamelLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Camel, modifier = Modifier.size(40.dp))
        }
        Text("옷 사진을 올리면 AI가 자동으로 태깅해요", style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
        Text(
            "배경을 자동으로 지우고, 패션 AI(DeepFashion2)와 색상 분석으로\n종류 · 색상 · 계절을 채워드립니다.",
            style = MaterialTheme.typography.bodyMedium, color = MediumGray
        )
        // 촬영 팁 — 누끼가 잘 되는 사진 찍는 법
        Card(
            colors = CardDefaults.cardColors(containerColor = CreamDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = Camel, modifier = Modifier.size(18.dp))
                Text(
                    "옷을 바닥·침대에 펼치거나 옷걸이에 걸어서, 옷과 색이 다른 배경에서 찍어주세요.",
                    style = MaterialTheme.typography.bodySmall, color = Brown
                )
            }
        }
        Button(
            onClick = onPick,
            colors = ButtonDefaults.buttonColors(containerColor = Camel),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("사진 선택하기")
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        content = content
    )
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Camel, selectedLabelColor = White
)
