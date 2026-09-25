package com.jeiu.fashionproject.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jeiu.fashionproject.data.WardrobeStore
import com.jeiu.fashionproject.ui.theme.*

/**
 * 가상 피팅 화면 (5·6주차 보고서: 가상 피팅 — 착용 이미지 미리보기).
 *
 * 전신 사진 위에 선택한 의류 이미지를 반투명으로 겹쳐, 드래그(이동)와
 * 핀치(크기 조절)로 착용 모습을 미리 확인하는 1차 프로토타입.
 * 추후 세그멘테이션/생성형 피팅으로 고도화하기 위한 기반 화면.
 */
private data class FittingItem(val name: String, val imageSource: Any)

private val sampleItems = listOf(
    FittingItem("베이지 니트", "https://loremflickr.com/300/380/knit,sweater?lock=40"),
    FittingItem("화이트 블라우스", "https://loremflickr.com/300/380/blouse,clothing?lock=41"),
    FittingItem("데님 자켓", "https://loremflickr.com/300/380/denim,jacket?lock=49"),
    FittingItem("베이지 코트", "https://loremflickr.com/300/380/coat,beige?lock=48"),
)

@Composable
fun VirtualFittingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { WardrobeStore.load(context) }
    val wardrobeItems by WardrobeStore.items.collectAsState()

    var bodyPhoto by remember { mutableStateOf<Uri?>(null) }
    var selected by remember { mutableStateOf<FittingItem?>(null) }

    // 오버레이 변환 상태
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var overlayAlpha by remember { mutableFloatStateOf(0.85f) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { bodyPhoto = it }
    }

    // 옷장 아이템 + 샘플을 피팅 후보로
    val fittingChoices = wardrobeItems.map { FittingItem(it.name, it.imageUri) } + sampleItems

    Column(modifier = Modifier.fillMaxSize().background(Cream)) {
        // 헤더
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = CharcoalBlack)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("가상 피팅", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold)
                Text("드래그로 이동 · 두 손가락으로 크기 조절", style = MaterialTheme.typography.bodySmall, color = MediumGray)
            }
            IconButton(onClick = { scale = 1f; offsetX = 0f; offsetY = 0f }) {
                Icon(Icons.Filled.RestartAlt, contentDescription = "초기화", tint = Brown)
            }
        }

        // 피팅 캔버스
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(LightGray),
            contentAlignment = Alignment.Center
        ) {
            if (bodyPhoto == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("전신 사진을 선택하면\n가상 피팅을 시작할 수 있어요", style = MaterialTheme.typography.bodyMedium, color = MediumGray)
                    Button(
                        onClick = { photoPicker.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Camel),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("전신 사진 선택")
                    }
                }
            } else {
                AsyncImage(
                    model = bodyPhoto,
                    contentDescription = "전신 사진",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                selected?.let { item ->
                    AsyncImage(
                        model = item.imageSource,
                        contentDescription = item.name,
                        modifier = Modifier
                            .size(180.dp)
                            .graphicsLayer(
                                scaleX = scale, scaleY = scale,
                                translationX = offsetX, translationY = offsetY
                            )
                            .alpha(overlayAlpha)
                            .pointerInput(item) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(0.4f, 3.5f)
                                    offsetX += pan.x
                                    offsetY += pan.y
                                }
                            },
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        // 투명도 조절
        if (bodyPhoto != null && selected != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("투명도", style = MaterialTheme.typography.bodySmall, color = MediumGray)
                Slider(
                    value = overlayAlpha,
                    onValueChange = { overlayAlpha = it },
                    valueRange = 0.3f..1f,
                    colors = SliderDefaults.colors(thumbColor = Camel, activeTrackColor = Camel),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 의류 선택 (내 옷장 + 샘플)
        Text(
            "입어볼 옷 선택",
            style = MaterialTheme.typography.titleSmall, color = CharcoalBlack, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(bottom = 20.dp)
        ) {
            items(fittingChoices) { item ->
                val isSel = selected == item
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(84.dp)
                ) {
                    Card(
                        onClick = {
                            selected = item
                            scale = 1f; offsetX = 0f; offsetY = 0f
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = if (isSel) BorderStroke(2.dp, Camel) else null,
                        colors = CardDefaults.cardColors(containerColor = if (isSel) CamelLight else White)
                    ) {
                        AsyncImage(
                            model = item.imageSource,
                            contentDescription = item.name,
                            modifier = Modifier.size(84.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Text(
                        item.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSel) Camel else MediumGray,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
