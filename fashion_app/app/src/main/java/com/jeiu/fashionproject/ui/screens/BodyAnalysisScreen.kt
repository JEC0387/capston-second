package com.jeiu.fashionproject.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import java.util.Locale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jeiu.fashionproject.ui.theme.*
import com.jeiu.fashionproject.ui.viewmodel.BodyAnalysisUiState
import com.jeiu.fashionproject.ui.viewmodel.BodyAnalysisViewModel
import com.jeiu.fashionproject.ui.viewmodel.BodyMeasurementCalculator

@Composable
fun BodyAnalysisScreen(
    onBack: () -> Unit,
    onStyleSuggestion: () -> Unit,
    onCameraClick: () -> Unit,
    viewModel: BodyAnalysisViewModel
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val heightInput by viewModel.heightInput.collectAsState()
    val validHeight = BodyMeasurementCalculator.parseHeight(heightInput) != null

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.analyzeImage(context, it) }
    }

    Column(modifier = Modifier.fillMaxSize().background(Cream)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로", tint = CharcoalBlack)
            }
            Text(
                "AI 체형 분석",
                style = MaterialTheme.typography.titleLarge,
                color = CharcoalBlack,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (val s = state) {
                is BodyAnalysisUiState.Idle -> {
                    OutlinedTextField(
                        value = heightInput,
                        onValueChange = viewModel::updateHeightInput,
                        label = { Text("키 (cm)") },
                        placeholder = { Text("예: 170 또는 170.5") },
                        supportingText = { Text("키를 먼저 입력해주세요. 지원 범위 100~230 cm, 소수점 첫째 자리까지") },
                        isError = heightInput.isNotEmpty() && !validHeight,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.5.dp, LightGray, RoundedCornerShape(20.dp))
                            .background(White)
                            .clickable(enabled = validHeight) { imagePicker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(36.dp))
                                    .background(CamelLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.CameraAlt,
                                    contentDescription = null,
                                    tint = Camel,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Text(
                                "전신 사진을 업로드하세요",
                                style = MaterialTheme.typography.titleMedium,
                                color = CharcoalBlack,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "정면에서 찍은 전신 사진이 가장 정확해요",
                                style = MaterialTheme.typography.bodySmall,
                                color = MediumGray
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CamelLight),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "촬영 팁",
                                style = MaterialTheme.typography.titleMedium,
                                color = Brown,
                                fontWeight = FontWeight.SemiBold
                            )
                            listOf(
                                "정면을 향해 서주세요",
                                "몸 전체가 나오도록 찍어주세요",
                                "밝은 곳에서 촬영해주세요",
                                "몸에 붙는 옷을 입으면 더 정확해요"
                            ).forEach {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Camel)
                                    )
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = Brown)
                                }
                            }
                        }
                    }

                    Button(
                        onClick = onCameraClick,
                        enabled = validHeight,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Camel, contentColor = White)
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "사진 촬영하기",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = { imagePicker.launch("image/*") },
                        enabled = validHeight,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Camel),
                        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "사진 선택하기",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                is BodyAnalysisUiState.Analyzing -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            CircularProgressIndicator(color = Camel, modifier = Modifier.size(48.dp))
                            Text(
                                "ML Kit로 체형을 분석하는 중이에요...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MediumGray
                            )
                        }
                    }
                }

                is BodyAnalysisUiState.Success -> {
                    val result = s.result
                    val proportions = listOf(
                        "어깨 너비" to result.shoulderRatio,
                        "허리 너비" to result.waistRatio,
                        "힙 너비" to result.hipRatio,
                        "다리 길이" to result.legRatio,
                    )

                    // 분석한 사진
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(20.dp))
                    ) {
                        AsyncImage(
                            model = s.imageUri,
                            contentDescription = "분석한 사진",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // 분석 결과
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = White),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "분석 완료! · 신뢰도 ${result.confidence}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Camel,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        result.bodyTypeKo,
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = CharcoalBlack,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = Camel,
                                    modifier = Modifier.size(40.dp)
                                )
                            }

                            HorizontalDivider(color = LightGray)

                            Text(
                                "신체 비율 분석",
                                style = MaterialTheme.typography.titleMedium,
                                color = CharcoalBlack,
                                fontWeight = FontWeight.SemiBold
                            )
                            proportions.forEach { (label, ratio) ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = CharcoalBlack
                                        )
                                        Text(
                                            "${(ratio * 100).toInt()}%",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Camel,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { ratio },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = Camel,
                                        trackColor = CamelLight
                                    )
                                }
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = White)
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("측정 결과 · 키 기준 cm 추정", style = MaterialTheme.typography.titleMedium,
                                color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
                            val cm = result.measurements
                            if (cm != null) {
                                listOf(
                                    "입력 키" to cm.heightCm,
                                    "어깨 너비" to cm.shoulderWidthCm,
                                    "허리 너비" to cm.waistWidthCm,
                                    "엉덩이 너비" to cm.hipWidthCm,
                                    "다리 길이 (힙~발목)" to cm.legLengthCm,
                                    "상체 길이 (어깨~힙)" to cm.torsoLengthCm
                                ).forEach { (label, value) ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(label, color = CharcoalBlack, style = MaterialTheme.typography.bodyMedium)
                                        Text(formatMeasurement(value, 1, " cm"), color = Camel)
                                    }
                                }
                                Text("정면 사진과 입력 키로 환산한 추정값이에요. 허리·엉덩이는 둘레가 아닌 너비이며, 옷·자세·카메라 각도에 따라 실측과 차이가 있어요.",
                                    color = MediumGray, style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text(result.measurementIssue ?: "cm 측정을 완료하지 못했어요. 다시 촬영해주세요.",
                                    color = MediumGray, style = MaterialTheme.typography.bodyMedium)
                                OutlinedButton(onClick = { viewModel.reset() }) { Text("다시 시도하기") }
                            }
                            HorizontalDivider(color = LightGray)
                            Text("어깨/엉덩이 비율  ${formatMeasurement(result.shoulderToHip, 2)}", color = CharcoalBlack)
                            Text("허리/엉덩이 비율  ${formatMeasurement(result.waistToHip, 2)}", color = CharcoalBlack)
                        }
                    }

                    // 체형 특징
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CamelLight),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "${result.bodyTypeKo} 특징",
                                style = MaterialTheme.typography.titleMedium,
                                color = Brown,
                                fontWeight = FontWeight.SemiBold
                            )
                            result.characteristics.forEach {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 6.dp)
                                            .size(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Brown)
                                    )
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = BrownDark)
                                }
                            }
                        }
                    }

                    // 스타일 추천
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = White),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "스타일 추천",
                                style = MaterialTheme.typography.titleMedium,
                                color = CharcoalBlack,
                                fontWeight = FontWeight.SemiBold
                            )
                            result.styleAdvice.forEach {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Camel)
                                    )
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CharcoalBlack
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = onStyleSuggestion,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Camel, contentColor = White)
                    ) {
                        Text(
                            "내 체형 맞춤 스타일 제안 보기",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = { viewModel.reset() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Camel),
                        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                    ) {
                        Text("다시 분석하기", style = MaterialTheme.typography.titleMedium)
                    }
                }

                is BodyAnalysisUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(White)
                            .border(1.dp, LightGray, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            s.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MediumGray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(20.dp)
                        )
                    }

                    Button(
                        onClick = { viewModel.reset() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Camel, contentColor = White)
                    ) {
                        Text(
                            "다시 시도하기",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// Defensive display boundary: never render NaN/Infinity/negative values, even for malformed model data.
private fun formatMeasurement(value: Float?, decimals: Int, suffix: String = ""): String =
    if (value != null && value.isFinite() && value > 0f)
        String.format(Locale.getDefault(), "%.${decimals}f", value) + suffix else "측정 불가"
