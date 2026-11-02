package com.jeiu.fashionproject.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material.icons.filled.CenterFocusStrong
import com.jeiu.fashionproject.data.TryOnPlan
import com.jeiu.fashionproject.data.TryOnGarment
import com.jeiu.fashionproject.data.TryOnRepository
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
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.data.WardrobeStore
import com.jeiu.fashionproject.data.AvatarProfile
import com.jeiu.fashionproject.data.AvatarProfileStore
import com.jeiu.fashionproject.data.AvatarFittingStore
import com.jeiu.fashionproject.data.AvatarOverlay
import com.jeiu.fashionproject.ui.components.BodyAvatarViewport
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
internal enum class FitSlot { BOTTOM, SHOES, TOP, OUTER, ACCESSORY }

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

internal data class Placement(val center: Offset, val width: Float, val rotation: Float)

private const val MIN_SCALE = 0.4f
private const val MAX_SCALE = 3f

/**
 * 옷 종류에 맞춰 몸 위 자리·너비·기울기를 계산한다.
 * 옷 사진은 옷 영역에 맞게 잘려 있으므로(여백 약 5%), 사진 너비를 몸 너비의 몇 배로 할지 종류별 비율로 정한다.
 */
internal fun fit(
    slot: FitSlot, item: WardrobeItem, aspect: Float, canvas: Size, body: FittingPoseDetector.BodyPoints?
): Placement {
    val garmentAspect = aspect.takeIf { it.isFinite() && it > 0f } ?: 1.2f
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
        Placement(Offset(midX, topY + width * garmentAspect / 2), width, rotation)

    return when (slot) {
        FitSlot.TOP, FitSlot.OUTER -> body.shoulders?.let {
            val (mid, w, angle) = measure(it)
            val ratio = if (slot == FitSlot.OUTER) 2.1f else 2.0f
            val topY = mid.y - w * .32f
            if (item.category == "원피스" && slot == FitSlot.TOP) {
                val hips = body.hips?.let(::measure)
                val ankles = body.ankles?.let(::measure)
                val targetBottom = if (hips != null && ankles != null)
                    hips.first.y + (ankles.first.y - hips.first.y) * .42f
                    else hips?.first?.y?.plus(w * 2.2f) ?: mid.y + w * 4f
                val lengthWidth = ((targetBottom - topY) / garmentAspect.coerceAtLeast(.4f)).coerceAtLeast(0f)
                val width = maxOf(w * 1.85f, (hips?.second ?: 0f) * 2.0f, lengthWidth)
                    .coerceAtMost(canvas.width * .85f)
                placeTop(mid.x, topY, width, angle)
            } else placeTop(mid.x, topY, w * ratio, angle)   // 목선은 어깨보다 조금 위
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
            placeTop(mid.x, mid.y - width * garmentAspect * 0.1f, width, 0f)
        } ?: default(defaultY)

        FitSlot.ACCESSORY -> default(defaultY)
    }
}

/** Projects the existing GLES avatar's physical proportions into its 38° camera viewport. */
internal fun fitAvatar(slot: FitSlot, item: WardrobeItem, aspect: Float, canvas: Size,
    profile: AvatarProfile): Placement {
    val p = profile.parameters()
    if (canvas.width <= 0f || canvas.height <= 0f || !aspect.isFinite() || aspect <= 0f)
        return Placement(Offset.Zero, 0f, 0f)
    val pixelsPerMeter = canvas.height / (2f * 3.8f * kotlin.math.tan(Math.toRadians(19.0)).toFloat())
    val h = p.heightCm / 100f
    val joints=p.joints();val hipY=joints.hipY;val shoulderY=joints.shoulderY
    fun screenY(modelY: Float) = canvas.height / 2f - (modelY - h / 2f) * pixelsPerMeter
    fun place(topY: Float, width: Float): Placement {
        val safeWidth = width.coerceIn(canvas.width * .08f, canvas.width * .85f)
        return Placement(Offset(canvas.width / 2f,
            (topY + safeWidth * aspect / 2f).coerceIn(-canvas.height, canvas.height * 2f)), safeWidth, 0f)
    }
    val shoulderWidth = p.shoulderCm / 100f * pixelsPerMeter
    val hipWidth = p.hipWidthCm / 100f * pixelsPerMeter
    return when (slot) {
        FitSlot.TOP -> {
            val isDress = item.category == "원피스"
            val topY = screenY(shoulderY) - shoulderWidth * .32f
            val dressWidth = if (isDress) maxOf(shoulderWidth * 1.85f,
                hipWidth * 2f,
                (screenY(hipY * .52f) - topY) / aspect.coerceAtLeast(.4f))
                else shoulderWidth * 2f
            place(topY, dressWidth)
        }
        FitSlot.OUTER -> place(screenY(shoulderY) - shoulderWidth * .36f, shoulderWidth * 2.2f)
        FitSlot.BOTTOM -> place(screenY(hipY) - hipWidth * .35f,
            hipWidth * if (item.subCategory == "치마") 2.8f else 2.6f)
        FitSlot.SHOES -> place(screenY(h * .05f), hipWidth * 1.6f)
        FitSlot.ACCESSORY -> place(screenY(shoulderY) - shoulderWidth * .4f, shoulderWidth * .8f)
    }
}

private enum class PoseStatus { NONE, LOADING, FOUND, NOT_FOUND }

/** Retains the complete manual canvas through configuration changes. No disk photo storage. */
private class ManualFittingViewModel : ViewModel() {
    var bodyPhoto by mutableStateOf<Uri?>(null)
    var photoGeneration by mutableLongStateOf(0L)
    var body by mutableStateOf<FittingPoseDetector.BodyPoints?>(null)
    var poseStatus by mutableStateOf(PoseStatus.NONE)
    var canvasSize by mutableStateOf(Size.Zero)
    val worn = mutableStateMapOf<FitSlot, WornGarment>()
    val avatarWorn = mutableStateMapOf<FitSlot, WornGarment>()
    var avatarMode by mutableStateOf(false)
    var activeSlot by mutableStateOf<FitSlot?>(null)
    var overlayAlpha by mutableFloatStateOf(0.9f)
    var showAi by mutableStateOf(false)
    var aiSelection by mutableStateOf<List<TryOnGarment>>(emptyList())
}

@Composable
fun VirtualFittingScreen(onAvatarSetup: () -> Unit = {}, onBack: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var savedOverlays by remember { mutableStateOf<List<AvatarOverlay>?>(null) }
    var overlayIssue by remember { mutableStateOf<String?>(null) }
    var askClearOverlay by remember { mutableStateOf(false) }
    var restoredAvatar by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        WardrobeStore.load(context)
        AvatarProfileStore.load(context)
        savedOverlays = AvatarFittingStore.load(context)
        overlayIssue = AvatarFittingStore.issue
    }
    val wardrobeItems by WardrobeStore.items.collectAsState()
    val avatarProfile by AvatarProfileStore.current.collectAsState()
    val avatarIssue by AvatarProfileStore.issue.collectAsState()

    val manual: ManualFittingViewModel = viewModel(factory = remember { viewModelFactory { initializer { ManualFittingViewModel() } } })
    val scope = manual.viewModelScope
    val aiModel = sharedTryOnViewModel()
    var bodyPhoto by manual::bodyPhoto
    var photoGeneration by manual::photoGeneration
    var body by manual::body
    var poseStatus by manual::poseStatus
    var canvasSize by manual::canvasSize
    var avatarMode by manual::avatarMode
    val worn = if (avatarMode) manual.avatarWorn else manual.worn
    var activeSlot by manual::activeSlot
    var overlayAlpha by manual::overlayAlpha
    var inputNotice by remember { mutableStateOf<String?>(null) }
    if (askClearOverlay) AlertDialog(onDismissRequest = { askClearOverlay = false },
        title = { Text("아바타 옷 배치 초기화") },
        text = { Text("저장된 아바타 옷 위치만 지워요. 옷장, 사진, 체형 프로필은 유지해요.") },
        confirmButton = { TextButton(onClick = {
            inputNotice = if (AvatarFittingStore.clear(context)) {
                overlayIssue = null
                "저장된 배치만 초기화했어요."
            } else "저장된 배치를 지우지 못했어요."
            askClearOverlay = false
        }) { Text("초기화") } },
        dismissButton = { TextButton(onClick = { askClearOverlay = false }) { Text("취소") } })

    LaunchedEffect(savedOverlays, canvasSize, wardrobeItems) {
        val entries = savedOverlays
        if (restoredAvatar || entries == null || canvasSize.width <= 0f || canvasSize.height <= 0f) return@LaunchedEffect
        entries.forEach { entry ->
            val item = wardrobeItems.firstOrNull { it.id == entry.itemId } ?: return@forEach
            val slot = slotOf(item)
            val uri = fittingUri(item)
            val aspect = FittingPoseDetector.imageSize(context.applicationContext, uri)
                ?.let { (w, h) -> h.toFloat() / w } ?: 1.2f
            manual.avatarWorn[slot] = WornGarment(item, uri, aspect,
                Offset(entry.centerX * canvasSize.width, entry.centerY * canvasSize.height),
                entry.width * canvasSize.width, entry.scale, entry.rotation)
        }
        restoredAvatar = true
    }

    fun refit(slot: FitSlot) {
        val g = worn[slot] ?: return
        val p = if (avatarMode && avatarProfile != null)
            fitAvatar(slot, g.item, g.aspect, canvasSize, avatarProfile!!)
            else fit(slot, g.item, g.aspect, canvasSize, body)
        worn[slot] = g.copy(center = p.center, baseWidth = p.width, rotation = p.rotation, scale = 1f)
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        bodyPhoto = uri
        photoGeneration++
        val requestGeneration = photoGeneration
        body = null
        poseStatus = PoseStatus.LOADING
        scope.launch {
            val detected = try { FittingPoseDetector.detect(context.applicationContext, uri) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            if (requestGeneration != photoGeneration) return@launch
            body = detected
            poseStatus = if (detected != null) PoseStatus.FOUND else PoseStatus.NOT_FOUND
            worn.keys.toList().forEach { refit(it) }
        }
    }

    /** 옷장에서 옷을 누르면: 이미 입은 옷이면 벗고, 아니면 그 종류 자리에 입힌다 */
    fun toggle(item: WardrobeItem) {
        if (avatarMode && avatarProfile == null) {
            inputNotice = "먼저 3D 체형 설정을 저장해주세요."
            return
        }
        val slot = slotOf(item)
        if (worn[slot]?.item?.id == item.id) {
            worn.remove(slot)
            if (activeSlot == slot) activeSlot = worn.keys.maxByOrNull { it.ordinal }
            return
        }
        scope.launch {
            val uri = fittingUri(item)
            val aspect = FittingPoseDetector.imageSize(context.applicationContext, uri)?.let { (w, h) -> h.toFloat() / w } ?: 1.2f
            val p = if (avatarMode && avatarProfile != null)
                fitAvatar(slot, item, aspect, canvasSize, avatarProfile!!)
                else fit(slot, item, aspect, canvasSize, body)
            worn[slot] = WornGarment(item, uri, aspect, p.center, p.width, rotation = p.rotation)
            activeSlot = slot
        }
    }

    val active = activeSlot?.let { worn[it] }
    var showAi by manual::showAi
    var aiSelection by manual::aiSelection
    if (showAi) {
        AiTryOnScreen(
            defaultPhoto = bodyPhoto,
            garments = aiSelection,
            onBack = { showAi = false },
            model = aiModel
        )
        return
    }


    BoxWithConstraints(Modifier.fillMaxSize().background(Cream)) {
    val compact = maxHeight < 480.dp
    val controlsMaxHeight = (maxHeight * if (compact) 0.3f else 0.4f).coerceAtMost(260.dp)
    Column(modifier = Modifier.fillMaxSize()) {
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
                if (!compact) Text(
                    if (avatarMode) "저장한 3D 추정 형상 위에 옷 사진을 배치해요"
                    else when (poseStatus) {
                        PoseStatus.NONE -> "전신 사진을 고르면 옷을 몸에 자동으로 맞춰요"
                        PoseStatus.LOADING -> "몸 위치를 찾는 중…"
                        PoseStatus.FOUND -> "드래그로 이동 · 두 손가락으로 크기·회전"
                        PoseStatus.NOT_FOUND -> "몸 위치를 찾지 못했어요. 직접 옮겨 주세요"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (poseStatus == PoseStatus.NOT_FOUND) HeartPink else MediumGray
                )
            }
            if (!avatarMode && bodyPhoto != null) {
                IconButton(onClick = { photoPicker.launch("image/*") }) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = "전신 사진 바꾸기", tint = Brown)
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !avatarMode, onClick = {
                avatarMode = false; activeSlot = manual.worn.keys.maxByOrNull { it.ordinal }
            }, label = { Text("내 전신 사진") }, modifier = Modifier.testTag("fittingPhotoMode"))
            FilterChip(selected = avatarMode, onClick = {
                avatarMode = true; activeSlot = manual.avatarWorn.keys.maxByOrNull { it.ordinal }
            }, label = { Text("내 3D 아바타") }, modifier = Modifier.testTag("fittingAvatarMode"))
        }

        if (avatarMode) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { worn.keys.toList().forEach { refit(it) } },
                    enabled = avatarProfile != null && worn.isNotEmpty(), modifier = Modifier.testTag("avatarAutoFit")) {
                    Icon(Icons.Filled.AutoFixHigh, contentDescription = null, tint = Camel)
                    Text("마법봉 · 자동핏", color = Camel)
                }
                TextButton(onClick = {
                    val entries = worn.values.map { g -> AvatarOverlay(g.item.id,
                        g.center.x / canvasSize.width, g.center.y / canvasSize.height,
                        g.baseWidth / canvasSize.width, g.scale, g.rotation) }
                    inputNotice = if (canvasSize.width > 0f && avatarProfile != null &&
                        AvatarFittingStore.save(context, entries)) "아바타 옷 배치를 이 기기에 저장했어요."
                        else "옷 배치를 저장하지 못했어요. 저장값 오류나 3D 설정을 확인해주세요."
                }, enabled = avatarProfile != null && worn.isNotEmpty(),
                    modifier = Modifier.testTag("avatarSaveFitting")) { Text("배치 저장") }
            }
            avatarIssue?.let { Text(it, color = HeartPink, modifier = Modifier.padding(horizontal = 20.dp)) }
            overlayIssue?.let {
                Text(it, color = HeartPink, modifier = Modifier.padding(horizontal = 20.dp))
                TextButton(onClick = { askClearOverlay = true },
                    modifier = Modifier.padding(horizontal = 20.dp)) { Text("저장된 배치만 초기화") }
            }
        } else if (bodyPhoto != null) {
            TextButton(onClick = { worn.keys.toList().forEach { refit(it) } },
                enabled = worn.isNotEmpty(), modifier = Modifier.padding(horizontal = 20.dp).testTag("manualAutoAlign")) {
                Icon(Icons.Filled.CenterFocusStrong, contentDescription = null, tint = Camel)
                Text("마법봉 · 자동핏", color = Camel)
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
            if (!compact) Text(if (avatarMode) "3D 형상 + 정면 고정 의류 이미지예요. 회전해도 옷은 몸을 감싸지 않아요. 의복 시뮬레이션이나 AI 착장 생성이 아니에요."
                else "수동 배치 · 옷 사진을 드래그해 배치해요. 자동 정렬은 AI 합성이 아니에요.",
                color = Brown, style = MaterialTheme.typography.bodySmall)
            if (!avatarMode) TextButton(onClick = {
                inputNotice = when {
                    aiModel.state.pending -> null // re-open the existing job without generating
                    bodyPhoto == null -> "먼저 전신 사진을 선택해주세요."
                    worn.isEmpty() -> "먼저 옷장에서 입어볼 옷을 선택해주세요."
                    else -> TryOnPlan.build(worn.values.map { g -> TryOnGarment(g.item.id, g.item.name, g.item.category,
                        g.item.originalUri?.takeIf { it.isNotBlank() } ?: g.item.imageUri.ifBlank { g.item.garmentUri ?: g.item.cutoutUri.orEmpty() }) }).issue
                }
                if (inputNotice == null) {
                    aiSelection = worn.values.map { g -> TryOnGarment(g.item.id, g.item.name, g.item.category,
                        g.item.originalUri?.takeIf { it.isNotBlank() } ?: g.item.imageUri.ifBlank { g.item.garmentUri ?: g.item.cutoutUri.orEmpty() }) }
                    showAi = true
                }
            }, modifier = Modifier.testTag("aiTryOnWand"), contentPadding = PaddingValues(vertical = 2.dp)) {
                Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (aiModel.state.pending) "진행 중인 AI 피팅 보기" else "마법봉 · AI로 입어보기", color = Camel)
            }
            inputNotice?.let { Text(it, color = Brown, modifier = Modifier.testTag("fittingInputNotice")) }
            if (!avatarMode && !compact && !TryOnRepository.isConfigured()) Text("AI 피팅 서비스가 아직 연결되지 않았어요. 수동 배치는 사용할 수 있어요.",
                color = MediumGray, style = MaterialTheme.typography.labelSmall)
        }

        // 피팅 캔버스
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(LightGray)
                .onSizeChanged { size ->
                    val old = canvasSize
                    val updated = Size(size.width.toFloat(), size.height.toFloat())
                    if (old.width > 0f && old.height > 0f && old != updated) {
                        manual.avatarWorn.keys.toList().forEach { slot ->
                            manual.avatarWorn[slot]?.let { g ->
                                manual.avatarWorn[slot] = g.copy(
                                    center = Offset(g.center.x * updated.width / old.width,
                                        g.center.y * updated.height / old.height),
                                    baseWidth = g.baseWidth * updated.width / old.width)
                            }
                        }
                    }
                    canvasSize = updated
                }
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
            if (avatarMode && avatarProfile == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("3D 체형 설정을 저장하면 여기에서 아바타와 내 옷을 함께 볼 수 있어요.",
                        color = MediumGray)
                    Button(onClick = onAvatarSetup, modifier = Modifier.testTag("fittingGoAvatarSetup")) {
                        Text("3D 체형 설정하기")
                    }
                }
            } else if (!avatarMode && bodyPhoto == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "정면 전신 사진을 선택하면\n옷 사진을 어깨·골반에 맞춰 배치할 수 있어요",
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
                if (avatarMode) {
                    avatarProfile?.let { profile ->
                        BodyAvatarViewport(profile.parameters(), profile.thigh,
                            Modifier.fillMaxSize().testTag("fittingAvatarViewport"))
                    }
                } else {
                    AsyncImage(model = bodyPhoto, contentDescription = "전신 사진",
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
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
                if (!avatarMode && poseStatus == PoseStatus.LOADING) CircularProgressIndicator(color = Camel)
            }
        }

        Column(Modifier.fillMaxWidth().heightIn(max = controlsMaxHeight)
            .verticalScroll(rememberScrollState()).testTag("manualFittingControls")) {
        if (avatarMode && compact) Text("의류 이미지 오버레이 · 3D 의복 시뮬레이션 아님",
            modifier = Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.labelSmall,
            color = Brown)
        // 입은 옷 — 누르면 그 옷을 조절, X로 벗기
        // (전신 사진을 고른 뒤에는 조절 영역 높이를 고정해, 옷을 입혀도 사진 위치가 바뀌지 않게 한다)
        if (avatarMode && avatarProfile != null || !avatarMode && bodyPhoto != null) {
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
        if (avatarMode && avatarProfile != null || !avatarMode && bodyPhoto != null) {
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
                IconButton(onClick = { active?.let { setScale(it.scale - 0.1f) } }, enabled = enabled, modifier = Modifier.testTag("manualScaleDown")) {
                    Text("−", style = MaterialTheme.typography.titleLarge, color = if (enabled) Brown else MediumGray)
                }
                Slider(
                    value = active?.scale ?: 1f,
                    onValueChange = { setScale(it) },
                    enabled = enabled,
                    valueRange = MIN_SCALE..MAX_SCALE,
                    colors = SliderDefaults.colors(thumbColor = Camel, activeTrackColor = Camel),
                    modifier = Modifier.weight(1f).testTag("manualScale")
                )
                IconButton(onClick = { active?.let { setScale(it.scale + 0.1f) } }, enabled = enabled, modifier = Modifier.testTag("manualScaleUp")) {
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
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp).testTag("manualAlpha")
                )
            }
        }

        // 입어볼 옷 선택 (종류별로 한 벌씩 함께 입을 수 있음)
        Text(
            "입어볼 옷 선택 · 종류별로 한 벌씩 배치해요",
            style = MaterialTheme.typography.titleSmall, color = CharcoalBlack, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(bottom = 20.dp).testTag("manualWardrobe")
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
                        modifier = Modifier.testTag("manualGarment-${item.id}"),
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
    }
}
