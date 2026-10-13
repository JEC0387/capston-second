package com.jeiu.fashionproject.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.data.WardrobeStore
import com.jeiu.fashionproject.ui.theme.*
import com.jeiu.fashionproject.ui.viewmodel.FittingPoseDetector
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * 가상 피팅 화면 (5·6주차 보고서: 가상 피팅 — 착용 이미지 미리보기).
 *
 * 전신 사진 위에 옷 사진(누끼)을 겹쳐 착용 모습을 미리 본다.
 *  - 상의·아우터·하의·신발·액세서리를 종류별로 한 벌씩 함께 입어볼 수 있다.
 *  - 전신 사진에서 어깨·골반·발목을 찾아(ML Kit 포즈) 옷을 자동으로 맞춘다.
 *    상의·아우터·원피스 → 어깨, 하의 → 골반, 신발 → 발목
 *  - 맞춘 뒤 드래그(이동)·두 손가락(크기·회전)·크기 슬라이더로 다듬는다. 조절은 "지금 조절 중"인 옷에 적용된다.
 * 옷 사진은 저장된 버전 중 옷만 → 배경 제거 순으로 쓰고, 둘 다 없으면 원본을 쓰며 안내 문구를 띄운다.
 */

/** 옷을 입히는 자리. 번호가 작을수록 아래에 그려진다. */
private enum class FitSlot { BOTTOM, SHOES, TOP, OUTER, ACCESSORY }

private fun slotOf(item: WardrobeItem) = when (item.category) {
    "상의", "원피스" -> FitSlot.TOP
    "아우터" -> FitSlot.OUTER
    "하의" -> FitSlot.BOTTOM
    "신발" -> FitSlot.SHOES
    else -> FitSlot.ACCESSORY
}

/** 피팅에 쓸 사진: 옷만 → 배경 제거 → (없으면) 원본 */
private fun fittingUri(item: WardrobeItem): String =
    item.garmentUri ?: item.cutoutUri ?: item.imageUri.takeIf { item.isCutout } ?: item.originalUri ?: item.imageUri

private fun hasCutout(item: WardrobeItem) = item.garmentUri != null || item.cutoutUri != null || item.isCutout

/** 입은 옷 한 벌 — 위치·크기는 피팅 화면(캔버스) 픽셀 기준 */
private data class WornGarment(
    val item: WardrobeItem,
    val uri: String,
    val aspect: Float,        // 세로 / 가로
    val center: Offset,
    val baseWidth: Float,     // 자동으로 맞춘 너비
    val scale: Float = 1f,    // 사용자가 조절한 배율
    val rotation: Float = 0f  // 도(°)
)

private data class Placement(val center: Offset, val width: Float, val rotation: Float)

private const val MIN_SCALE = 0.4f
private const val MAX_SCALE = 3f

/**
 * 옷 종류에 맞춰 몸 위 자리·너비·기울기를 계산한다.
 * 옷 사진은 옷 영역에 맞게 잘려 있으므로(여백 약 5%), 사진 너비를 몸 너비의 몇 배로 할지 종류별 비율로 정한다.
 */
private fun fit(
    slot: FitSlot, item: WardrobeItem, aspect: Float, canvas: Size, body: FittingPoseDetector.BodyPoints?
): Placement {
    fun default(yRatio: Float) = Placement(Offset(canvas.width / 2, canvas.height * yRatio), canvas.width * 0.5f, 0f)
    val defaultY = when (slot) {
        FitSlot.TOP, FitSlot.OUTER -> 0.35f
        FitSlot.BOTTOM -> 0.62f
        FitSlot.SHOES -> 0.88f
        FitSlot.ACCESSORY -> 0.5f
    }
    if (body == null || canvas.width <= 0f) return default(defaultY)

    // 전신 사진은 ContentScale.Fit으로 그려지므로 같은 방식으로 사진 좌표 → 캔버스 좌표 변환
    val s = minOf(canvas.width / body.imageWidth, canvas.height / body.imageHeight)
    val ox = (canvas.width - body.imageWidth * s) / 2
    val oy = (canvas.height - body.imageHeight * s) / 2
    fun Offset.toCanvas() = Offset(ox + x * s, oy + y * s)

    /** 두 점 사이 가운데·거리·기울기 */
    fun measure(p: Pair<Offset, Offset>): Triple<Offset, Float, Float> {
        val a = p.first.toCanvas(); val b = p.second.toCanvas()
        val angle = Math.toDegrees(atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())).toFloat()
        return Triple((a + b) / 2f, (b - a).getDistance(), angle)
    }

    /** 옷 윗부분이 topY에 오도록 가운데 자리 계산 */
    fun placeTop(midX: Float, topY: Float, width: Float, rotation: Float) =
        Placement(Offset(midX, topY + width * aspect / 2), width, rotation)

    return when (slot) {
        FitSlot.TOP, FitSlot.OUTER -> body.shoulders?.let {
            val (mid, w, angle) = measure(it)
            val ratio = if (slot == FitSlot.OUTER) 2.1f else 2.0f
            placeTop(mid.x, mid.y - w * 0.32f, w * ratio, angle)   // 목선은 어깨보다 조금 위
        } ?: default(defaultY)

        FitSlot.BOTTOM -> body.hips?.let {
            val (mid, w, angle) = measure(it)
            val ratio = if (item.subCategory == "치마") 2.8f else 2.6f
            placeTop(mid.x, mid.y - w * 0.45f, w * ratio, angle)   // 허리는 골반 관절보다 위
        } ?: default(defaultY)

        FitSlot.SHOES -> body.ankles?.let {
            val (mid, w, _) = measure(it)
            // 발을 모으고 서면 발목 사이가 좁으므로 골반 너비를 최소값으로 쓴다
            val hipW = body.hips?.let { h -> measure(h).second } ?: 0f
            val width = maxOf(w * 2.0f, hipW * 1.6f)
            placeTop(mid.x, mid.y - width * aspect * 0.1f, width, 0f)
        } ?: default(defaultY)

        FitSlot.ACCESSORY -> default(defaultY)
    }
}

private enum class PoseStatus { NONE, LOADING, FOUND, NOT_FOUND }

@Composable
fun VirtualFittingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { WardrobeStore.load(context) }
    val wardrobeItems by WardrobeStore.items.collectAsState()

    var bodyPhoto by remember { mutableStateOf<Uri?>(null) }
    var body by remember { mutableStateOf<FittingPoseDetector.BodyPoints?>(null) }
    var poseStatus by remember { mutableStateOf(PoseStatus.NONE) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    val worn = remember { mutableStateMapOf<FitSlot, WornGarment>() }
    var activeSlot by remember { mutableStateOf<FitSlot?>(null) }
    var overlayAlpha by remember { mutableFloatStateOf(0.9f) }

    fun refit(slot: FitSlot) {
        val g = worn[slot] ?: return
        val p = fit(slot, g.item, g.aspect, canvasSize, body)
        worn[slot] = g.copy(center = p.center, baseWidth = p.width, rotation = p.rotation, scale = 1f)
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        bodyPhoto = uri
        body = null
        poseStatus = PoseStatus.LOADING
        scope.launch {
            body = FittingPoseDetector.detect(context, uri)
            poseStatus = if (body != null) PoseStatus.FOUND else PoseStatus.NOT_FOUND
            worn.keys.toList().forEach { refit(it) }
        }
    }

    /** 옷장에서 옷을 누르면: 이미 입은 옷이면 벗고, 아니면 그 종류 자리에 입힌다 */
    fun toggle(item: WardrobeItem) {
        val slot = slotOf(item)
        if (worn[slot]?.item?.id == item.id) {
            worn.remove(slot)
            if (activeSlot == slot) activeSlot = worn.keys.maxByOrNull { it.ordinal }
            return
        }
        scope.launch {
            val uri = fittingUri(item)
            val aspect = FittingPoseDetector.imageSize(context, uri)?.let { (w, h) -> h.toFloat() / w } ?: 1.2f
            val p = fit(slot, item, aspect, canvasSize, body)
            worn[slot] = WornGarment(item, uri, aspect, p.center, p.width, rotation = p.rotation)
            activeSlot = slot
        }
    }

    val active = activeSlot?.let { worn[it] }

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
                Text(
                    when (poseStatus) {
                        PoseStatus.NONE -> "전신 사진을 고르면 옷을 몸에 자동으로 맞춰요"
                        PoseStatus.LOADING -> "몸 위치를 찾는 중…"
                        PoseStatus.FOUND -> "드래그로 이동 · 두 손가락으로 크기·회전"
                        PoseStatus.NOT_FOUND -> "몸 위치를 찾지 못했어요. 직접 옮겨 주세요"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (poseStatus == PoseStatus.NOT_FOUND) HeartPink else MediumGray
                )
            }
            if (bodyPhoto != null) {
                IconButton(onClick = { photoPicker.launch("image/*") }) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = "전신 사진 바꾸기", tint = Brown)
                }
                IconButton(
                    onClick = { worn.keys.toList().forEach { refit(it) } },
                    enabled = worn.isNotEmpty()
                ) {
                    Icon(Icons.Filled.AutoFixHigh, contentDescription = "몸에 맞추기", tint = if (worn.isNotEmpty()) Camel else MediumGray)
                }
            }
        }

        // 피팅 캔버스
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(LightGray)
                .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(activeSlot) {
                    detectTransformGestures { _, pan, zoom, rotation ->
                        val slot = activeSlot ?: return@detectTransformGestures
                        val g = worn[slot] ?: return@detectTransformGestures
                        worn[slot] = g.copy(
                            center = g.center + pan,
                            scale = (g.scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE),
                            rotation = g.rotation + rotation
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (bodyPhoto == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "정면 전신 사진을 선택하면\n옷을 어깨·골반에 맞춰 입혀볼 수 있어요",
                        style = MaterialTheme.typography.bodyMedium, color = MediumGray
                    )
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
                // 아래 자리(하의)부터 위 자리(아우터) 순서로 겹쳐 그린다
                worn.values.sortedBy { slotOf(it.item).ordinal }.forEach { g ->
                    val w = g.baseWidth * g.scale
                    val h = w * g.aspect
                    AsyncImage(
                        model = g.uri,
                        contentDescription = g.item.name,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset { IntOffset((g.center.x - w / 2).roundToInt(), (g.center.y - h / 2).roundToInt()) }
                            .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                            .graphicsLayer(rotationZ = g.rotation, alpha = overlayAlpha)
                    )
                }
                // 누끼 사진이 없는 옷 안내
                active?.takeIf { !hasCutout(it.item) }?.let {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(White.copy(alpha = 0.92f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = Camel, modifier = Modifier.size(16.dp))
                        Text(
                            "'${it.item.name}'은(는) 누끼 사진이 없어 배경까지 함께 보여요",
                            style = MaterialTheme.typography.labelMedium, color = Brown
                        )
                    }
                }
                if (poseStatus == PoseStatus.LOADING) CircularProgressIndicator(color = Camel)
            }
        }

        // 입은 옷 — 누르면 그 옷을 조절, X로 벗기
        // (전신 사진을 고른 뒤에는 조절 영역 높이를 고정해, 옷을 입혀도 사진 위치가 바뀌지 않게 한다)
        if (bodyPhoto != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("조절할 옷", style = MaterialTheme.typography.labelMedium, color = MediumGray)
                if (worn.isEmpty()) {
                    Text("아래에서 옷을 고르면 여기에 나와요", style = MaterialTheme.typography.labelMedium, color = MediumGray)
                }
                worn.entries.sortedBy { it.key.ordinal }.forEach { (slot, g) ->
                    InputChip(
                        selected = slot == activeSlot,
                        onClick = { activeSlot = slot },
                        label = { Text(g.item.name, maxLines = 1) },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Close, contentDescription = "벗기",
                                modifier = Modifier.size(16.dp).clickable { toggle(g.item) }
                            )
                        },
                        colors = InputChipDefaults.inputChipColors(
                            selectedContainerColor = Camel, selectedLabelColor = White, selectedTrailingIconColor = White
                        )
                    )
                }
            }
        }

        // 크기 · 투명도 (고를 옷이 없으면 비활성)
        if (bodyPhoto != null) {
            val enabled = active != null
            fun setScale(v: Float) {
                val slot = activeSlot ?: return
                val g = worn[slot] ?: return
                worn[slot] = g.copy(scale = v.coerceIn(MIN_SCALE, MAX_SCALE))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("크기", style = MaterialTheme.typography.bodySmall, color = MediumGray, modifier = Modifier.width(40.dp))
                IconButton(onClick = { active?.let { setScale(it.scale - 0.1f) } }, enabled = enabled) {
                    Text("−", style = MaterialTheme.typography.titleLarge, color = if (enabled) Brown else MediumGray)
                }
                Slider(
                    value = active?.scale ?: 1f,
                    onValueChange = { setScale(it) },
                    enabled = enabled,
                    valueRange = MIN_SCALE..MAX_SCALE,
                    colors = SliderDefaults.colors(thumbColor = Camel, activeTrackColor = Camel),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { active?.let { setScale(it.scale + 0.1f) } }, enabled = enabled) {
                    Text("+", style = MaterialTheme.typography.titleLarge, color = if (enabled) Brown else MediumGray)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("투명도", style = MaterialTheme.typography.bodySmall, color = MediumGray, modifier = Modifier.width(40.dp))
                Slider(
                    value = overlayAlpha,
                    onValueChange = { overlayAlpha = it },
                    valueRange = 0.3f..1f,
                    colors = SliderDefaults.colors(thumbColor = Camel, activeTrackColor = Camel),
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                )
            }
        }

        // 입어볼 옷 선택 (종류별로 한 벌씩 함께 입을 수 있음)
        Text(
            "입어볼 옷 선택 · 종류별로 한 벌씩 함께 입어져요",
            style = MaterialTheme.typography.titleSmall, color = CharcoalBlack, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(bottom = 20.dp)
        ) {
            items(wardrobeItems, key = { it.id }) { item ->
                val isWorn = worn[slotOf(item)]?.item?.id == item.id
                val cut = hasCutout(item)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(84.dp)
                ) {
                    Card(
                        onClick = { toggle(item) },
                        shape = RoundedCornerShape(14.dp),
                        border = if (isWorn) BorderStroke(2.dp, Camel) else null,
                        colors = CardDefaults.cardColors(containerColor = if (isWorn) CamelLight else White)
                    ) {
                        AsyncImage(
                            model = fittingUri(item),
                            contentDescription = item.name,
                            modifier = Modifier
                                .size(84.dp)
                                .background(if (cut) CreamDark else LightGray)
                                .padding(if (cut) 6.dp else 0.dp),
                            contentScale = if (cut) ContentScale.Fit else ContentScale.Crop
                        )
                    }
                    Text(
                        item.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isWorn) Camel else MediumGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
