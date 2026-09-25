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
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.data.WardrobeStore
import com.jeiu.fashionproject.ui.theme.*
import com.jeiu.fashionproject.ui.viewmodel.AddClothingUiState
import com.jeiu.fashionproject.ui.viewmodel.AddClothingViewModel

/**
 * AI 옷 등록 화면 (5주차 보고서: 원터치 가상 옷장 구축).
 *
 * 사진 선택 → 백엔드 YOLOv8 탐지 + 색상 추출로 이름·카테고리·색상을 자동 태깅 →
 * 사용자가 확인/수정 후 옷장에 추가. 서버 미연결 시 색상 기반 로컬 태깅으로 동작.
 */
@Composable
fun AddClothingScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: AddClothingViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val categories = listOf("상의", "하의", "아우터", "신발", "액세서리")

    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("상의") }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.tagImage(context, it) }
    }

    LaunchedEffect(state) {
        val s = state
        if (s is AddClothingUiState.Tagged) {
            name = s.tag.suggestedName
            category = s.tag.suggestedCategory
        }
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
            Text("AI 옷 등록", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold)
        }

        when (val s = state) {
            is AddClothingUiState.Idle -> {
                // 사진 선택 안내
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
                        "YOLOv8 객체 탐지와 색상 분석으로\n이름 · 카테고리 · 색상을 자동으로 채워드립니다.",
                        style = MaterialTheme.typography.bodyMedium, color = MediumGray
                    )
                    Button(
                        onClick = { imagePicker.launch("image/*") },
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

            is AddClothingUiState.Tagging -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = Camel)
                    Text("AI가 옷을 분석하고 있어요…", style = MaterialTheme.typography.bodyMedium, color = MediumGray)
                }
            }

            is AddClothingUiState.Tagged -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 탐지 결과 이미지 (서버 연결 시 바운딩 박스 포함)
                    AsyncImage(
                        model = s.tag.resultImageUrl ?: s.imageUri,
                        contentDescription = "분석 이미지",
                        modifier = Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(16.dp)).background(LightGray),
                        contentScale = ContentScale.Crop
                    )

                    // AI 태깅 결과 배지
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (s.tag.fromServer) CamelLight else LightGray),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                if (s.tag.fromServer) Icons.Filled.AutoAwesome else Icons.Filled.CloudOff,
                                contentDescription = null, tint = if (s.tag.fromServer) Camel else MediumGray, modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    if (s.tag.fromServer) "YOLOv8 서버 탐지 완료" else "오프라인 — 색상 기반 로컬 태깅",
                                    style = MaterialTheme.typography.labelLarge, color = CharcoalBlack, fontWeight = FontWeight.SemiBold
                                )
                                if (s.tag.detectedLabels.isNotEmpty()) {
                                    Text("탐지: ${s.tag.detectedLabels.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MediumGray)
                                }
                                Text("대표 색상: ${s.tag.colorName}", style = MaterialTheme.typography.bodySmall, color = MediumGray)
                            }
                            Spacer(Modifier.weight(1f))
                            Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Color(s.tag.colorValue)))
                        }
                    }

                    // 이름 수정
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("아이템 이름") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Camel, focusedLabelColor = Camel)
                    )

                    // 카테고리 선택
                    Text("카테고리", style = MaterialTheme.typography.labelLarge, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        categories.forEach { c ->
                            FilterChip(
                                selected = category == c,
                                onClick = { category = c },
                                label = { Text(c) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Camel, selectedLabelColor = White
                                )
                            )
                        }
                    }

                    // 등록 / 다시 선택
                    Button(
                        onClick = {
                            WardrobeStore.add(
                                context,
                                WardrobeItem(
                                    name = name.ifBlank { s.tag.suggestedName },
                                    category = category,
                                    colorName = s.tag.colorName,
                                    colorValue = s.tag.colorValue,
                                    imageUri = s.imageUri.toString(),
                                    aiTagged = s.tag.fromServer
                                )
                            )
                            viewModel.reset()
                            onDone()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Camel),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) { Text("옷장에 추가하기") }

                    OutlinedButton(
                        onClick = { viewModel.reset() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("다른 사진 선택", color = Brown) }

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
                    Button(onClick = { viewModel.reset() }, colors = ButtonDefaults.buttonColors(containerColor = Camel)) {
                        Text("다시 시도")
                    }
                }
            }
        }
    }
}
