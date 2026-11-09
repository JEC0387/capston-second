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
import com.jeiu.fashionproject.data.DeviceGarmentInput
import com.jeiu.fashionproject.data.DeviceGarmentRole
import com.jeiu.fashionproject.data.DeviceVtonSelection
import com.jeiu.fashionproject.ui.viewmodel.OnDeviceVtonViewModel
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
internal data class WornGarment(
    val item: WardrobeItem,
    val uri: String,
    val aspect: Float,        // 세로 / 가로
    val center: Offset,
    val baseWidth: Float,     // 자동으로 맞춘 너비
    val scale: Float = 1f,    // 사용자가 조절한 배율
    val rotation: Float = 0f,  // 도(°)
    val region: GarmentRegion? = null
)

internal data class Placement(val center: Offset, val width: Float, val rotation: Float)

/** Snapshot all selected originals and placement state. Never silently discard an unsupported slot. */
internal fun onDeviceFittingSelection(person: String?, revision: Long, garments: Collection<WornGarment>,
    canvas: Size): DeviceVtonSelection? {
    if (person.isNullOrBlank() || garments.any { it.item.category !in setOf("상의", "하의", "신발") }) return null
    return DeviceVtonSelection(person, revision, garments.map { g ->
        DeviceGarmentInput(g.item.id, when (g.item.category) {
            "상의" -> DeviceGarmentRole.TOP
            "하의" -> DeviceGarmentRole.BOTTOM
            else -> DeviceGarmentRole.SHOES
        }, g.item.originalUri?.takeIf { it.isNotBlank() }
            ?: g.item.imageUri.ifBlank { g.item.garmentUri ?: g.item.cutoutUri.orEmpty() },
            listOf(canvas.width, canvas.height, g.center.x, g.center.y, g.baseWidth,
                g.scale, g.rotation, g.aspect).joinToString(","))
    }.sortedBy { it.role.ordinal })
}

/** Null means preserve the existing placement; never report a fallback as detection. */
internal fun photoAutoPlacement(slot: FitSlot, item: WardrobeItem, aspect: Float,
    canvas: Size, body: FittingPoseDetector.BodyPoints?): Placement? {
    if (body == null || body.imageWidth <= 0 || body.imageHeight <= 0 ||
        canvas.width <= 0f || canvas.height <= 0f) return null
    fun valid(pair: Pair<Offset, Offset>?) = pair != null &&
        listOf(pair.first.x, pair.first.y, pair.second.x, pair.second.y).all { it.isFinite() } &&
        (pair.first - pair.second).getDistance() > 0f
    val supported = when (slot) {
        FitSlot.TOP -> valid(body.shoulders) && (item.category != "원피스" || valid(body.hips))
        FitSlot.OUTER -> valid(body.shoulders)
        FitSlot.BOTTOM -> valid(body.hips)
        FitSlot.SHOES -> valid(body.ankles)
        FitSlot.ACCESSORY -> false
    }
    return if (supported) fit(slot, item, aspect, canvas, body) else null
}

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
    fun place(topY: Float, width: Float, maxHeight:Float=canvas.height*.82f): Placement {
        // A minimum display width must never defeat the garment's height limit.
        // Below the persisted placement's 1% width limit, report unsupported rather than stretch it.
        val safeWidth = minOf(width, maxHeight / aspect.coerceAtLeast(.1f), canvas.width * .85f)
        if (!safeWidth.isFinite() || safeWidth < canvas.width * .01f)
            return Placement(Offset.Zero, 0f, 0f)
        return Placement(Offset(canvas.width / 2f,
            (topY + safeWidth * aspect / 2f).coerceIn(-canvas.height, canvas.height * 2f)), safeWidth, 0f)
    }
    val shoulderWidth = p.shoulderCm / 100f * pixelsPerMeter
    val torsoWidth=maxOf(p.shoulderCm,p.chestWidthCm,p.waistWidthCm)/100f*pixelsPerMeter
    val hipWidth = maxOf(p.hipWidthCm,p.waistWidthCm) / 100f * pixelsPerMeter
    return when (slot) {
        FitSlot.TOP -> {
            val isDress = item.category == "원피스"
            val topY = screenY(shoulderY) - shoulderWidth * .32f
            val dressWidth = if (isDress) maxOf(torsoWidth * 1.25f,
                hipWidth * 1.35f,
                (screenY(hipY * .52f) - topY) / aspect.coerceAtLeast(.4f))
                else torsoWidth * 1.25f
            place(topY, dressWidth, if(isDress) screenY(h*.08f)-topY else (screenY(hipY)-topY)*1.15f)
        }
        FitSlot.OUTER -> place(screenY(shoulderY) - shoulderWidth * .36f, torsoWidth * 1.45f,(screenY(hipY)-screenY(shoulderY)+shoulderWidth*.36f)*1.3f)
        FitSlot.BOTTOM -> place(screenY(hipY) - hipWidth * .35f,
            hipWidth * if (item.subCategory == "치마") 1.75f else 1.35f,screenY(h*.02f)-screenY(hipY)+hipWidth*.35f)
        FitSlot.SHOES -> place(screenY(h * .05f), hipWidth * 1.2f)
        FitSlot.ACCESSORY -> place(screenY(shoulderY) - shoulderWidth * .4f, shoulderWidth * .8f)
    }
}

internal enum class PoseStatus { NONE, LOADING, FOUND, NOT_FOUND }

/** Retains the complete manual canvas through configuration changes. No disk photo storage. */
internal class ManualFittingViewModel : ViewModel() {
    var bodyPhoto by mutableStateOf<Uri?>(null)
    var photoDisplaySize by mutableStateOf<Size?>(null)
    var photoGeneration by mutableLongStateOf(0L)
    var body by mutableStateOf<FittingPoseDetector.BodyPoints?>(null)
    var poseStatus by mutableStateOf(PoseStatus.NONE)
    var canvasSize by mutableStateOf(Size.Zero)
    val worn = mutableStateMapOf<FitSlot, WornGarment>()
    val avatarWorn = mutableStateMapOf<FitSlot, WornGarment>()
    var avatarMode by mutableStateOf(false)
    var activeSlot by mutableStateOf<FitSlot?>(null)
    var overlayAlpha by mutableFloatStateOf(0.9f)
    val selectionRequests = mutableMapOf<FitSlot,Long>()
    var showAi by mutableStateOf(false)
    var showLocalAi by mutableStateOf(false)
    var showOnDeviceAi by mutableStateOf(false)
    var showServerOutfitAi by mutableStateOf(false)
    var serverOutfitSelection by mutableStateOf<DeviceVtonSelection?>(null)
    var deviceSelection by mutableStateOf<DeviceVtonSelection?>(null)
    var aiSelection by mutableStateOf<List<TryOnGarment>>(emptyList())
    var photoDropUndo by mutableStateOf<PhotoDropUndo?>(null)
    val pendingPhotoDrops = mutableStateMapOf<FitSlot, PhotoDropTicket>()

    fun beginPhotoDrop(slot: FitSlot, center: Offset): PhotoDropTicket? {
        val photo = photoDisplaySize ?: return null
        if (avatarMode || showAi || showLocalAi || showOnDeviceAi || showServerOutfitAi || bodyPhoto == null || !photo.isUsableFittingSize() || !canvasSize.isUsableFittingSize() ||
            !center.x.isFinite() || !center.y.isFinite()) return null
        val request = (selectionRequests[slot] ?: 0L) + 1
        selectionRequests[slot] = request
        return PhotoDropTicket(slot, photoGeneration, request, center, canvasSize, photo).also { pendingPhotoDrops[slot] = it }
    }

    fun finishPhotoDrop(ticket: PhotoDropTicket) {
        if (pendingPhotoDrops[ticket.slot] == ticket) pendingPhotoDrops.remove(ticket.slot)
    }

    fun cancelPhotoDrop(slot: FitSlot) {
        if (pendingPhotoDrops.remove(slot) != null) selectionRequests[slot] = (selectionRequests[slot] ?: 0L) + 1
    }

    fun cancelPhotoDrops() { pendingPhotoDrops.keys.toList().forEach(::cancelPhotoDrop) }

    fun openOnDevice(selection: DeviceVtonSelection) {
        cancelPhotoDrops()
        // An earlier card-click decode must not replace the visible outfit after it was frozen.
        selectionRequests.keys.toList().forEach { slot -> selectionRequests[slot] = (selectionRequests[slot] ?: 0L) + 1L }
        deviceSelection = selection.copy()
        showOnDeviceAi = true
    }

    /** Explicit server mode; the on-device wand and original wardrobe remain unchanged. */
    fun openServerOutfit(selection: DeviceVtonSelection?) {
        cancelPhotoDrops()
        selectionRequests.keys.toList().forEach { slot -> selectionRequests[slot] = (selectionRequests[slot] ?: 0L) + 1L }
        serverOutfitSelection = selection?.copy()
        showServerOutfitAi = true
    }

    fun applyPhotoDrop(ticket: PhotoDropTicket, garment: WornGarment): Boolean {
        if (avatarMode || showAi || showLocalAi || showOnDeviceAi || showServerOutfitAi || bodyPhoto == null || ticket.generation != photoGeneration ||
            selectionRequests[ticket.slot] != ticket.request || pendingPhotoDrops[ticket.slot] != ticket ||
            slotOf(garment.item) != ticket.slot) return false
        finishPhotoDrop(ticket)
        photoDropUndo = PhotoDropUndo(ticket.slot, worn[ticket.slot], activeSlot, photoGeneration)
        worn[ticket.slot] = garment
        activeSlot = ticket.slot
        return true
    }

    fun undoPhotoDrop(): Boolean {
        val undo = photoDropUndo ?: return false
        if (avatarMode || undo.generation != photoGeneration) return false
        cancelPhotoDrop(undo.slot)
        selectionRequests[undo.slot] = (selectionRequests[undo.slot] ?: 0L) + 1
        if (undo.previous == null) worn.remove(undo.slot) else worn[undo.slot] = undo.previous
        activeSlot = undo.previousActive?.takeIf { worn.containsKey(it) } ?: worn.keys.maxByOrNull { it.ordinal }
        photoDropUndo = null
        return true
    }

    fun selectPhoto(uri: Uri): Long {
        bodyPhoto = uri
        photoGeneration++
        cancelPhotoDrops()
        photoDropUndo = null
        photoDisplaySize = null
        body = null
        poseStatus = PoseStatus.LOADING
        return photoGeneration
    }

    // Coil's displayed drawable has already applied orientation, even if pose detection fails.
    // Ignore old requests (including the same URI reselected) and duplicate size notifications.
    // Learning dimensions must never move a placement edited while the image was loading.
    fun acceptPhotoDisplaySize(generation: Long, uri: Uri?, size: Size) {
        if (generation == photoGeneration && uri != null && uri == bodyPhoto &&
            photoDisplaySize == null && size.isUsableFittingSize()) photoDisplaySize = size
    }

    fun resizeCanvas(updated: Size) {
        if (!updated.isUsableFittingSize()) return
        val old = canvasSize
        if (old.isUsableFittingSize() && old != updated) {
            // Avatar storage and its canvas-normalized coordinate contract are unchanged.
            avatarWorn.keys.toList().forEach { slot -> avatarWorn[slot]?.let { g ->
                    avatarWorn[slot] = g.copy(center = Offset(g.center.x * updated.width / old.width,
                        g.center.y * updated.height / old.height), baseWidth = g.baseWidth * updated.width / old.width)
            } }
            worn.keys.toList().forEach { slot -> worn[slot]?.let { g ->
                resizePhotoPlacement(Placement(g.center,g.baseWidth,g.rotation),photoDisplaySize,old,updated)?.let { p ->
                    worn[slot] = g.copy(center=p.center,baseWidth=p.width)
                }
            } }
            photoDropUndo = photoDropUndo?.let { undo -> undo.copy(previous = undo.previous?.let { g ->
                resizePhotoPlacement(Placement(g.center, g.baseWidth, g.rotation), photoDisplaySize, old, updated)?.let { p ->
                    g.copy(center = p.center, baseWidth = p.width)
                } ?: g
            }) }
        }
        canvasSize = updated
    }
}

@Composable
fun VirtualFittingScreen(onAvatarSetup: () -> Unit = {},startAvatar:Boolean=false,onBack: () -> Unit) {
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
    val deviceModel: OnDeviceVtonViewModel = viewModel(factory = remember { viewModelFactory { initializer { OnDeviceVtonViewModel() } } })
    val serverOutfitModel: com.jeiu.fashionproject.ui.viewmodel.ServerOutfitVtonViewModel = viewModel()
    var bodyPhoto by manual::bodyPhoto
    var photoGeneration by manual::photoGeneration
    var body by manual::body
    var poseStatus by manual::poseStatus
    var canvasSize by manual::canvasSize
    var avatarMode by manual::avatarMode
    LaunchedEffect(startAvatar) {if(startAvatar)manual.avatarMode=true}
    val worn = if (avatarMode) manual.avatarWorn else manual.worn
    val currentDeviceSelection = onDeviceFittingSelection(bodyPhoto?.toString(), photoGeneration, manual.worn.values.toList(), canvasSize)
    LaunchedEffect(currentDeviceSelection) { deviceModel.selectionChanged(currentDeviceSelection) }
    var activeSlot by manual::activeSlot
    var overlayAlpha by manual::overlayAlpha
    var inputNotice by remember { mutableStateOf<String?>(null) }
    var avatarCameraReset by remember { mutableIntStateOf(0) }
    var inspectAvatar by remember { mutableStateOf(false) }
    var fittingHelp by remember { mutableStateOf(false) }
    var dragItem by remember { mutableStateOf<WardrobeItem?>(null) }
    var dragRootPoint by remember { mutableStateOf(Offset.Zero) }
    var dragGeneration by remember { mutableLongStateOf(-1L) }
    var canvasBounds by remember { mutableStateOf(Rect.Zero) }
    var screenOrigin by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(avatarMode, photoGeneration, manual.showAi, manual.showLocalAi, manual.showOnDeviceAi, manual.showServerOutfitAi) {
        dragItem = null
        if (avatarMode || manual.showAi || manual.showLocalAi || manual.showOnDeviceAi || manual.showServerOutfitAi) manual.cancelPhotoDrops()
    }
    val controlsScroll = rememberScrollState()
    var manualToolsExpanded by rememberSaveable { mutableStateOf(false) }
    var advancedToolsExpanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(inputNotice) {
        if (inputNotice != null) controlsScroll.scrollTo(0)
    }
    var undoAvatar by remember { mutableStateOf<Map<FitSlot,WornGarment>?>(null) }
    var lastProfile by remember {mutableStateOf(avatarProfile)}
    LaunchedEffect(avatarProfile) {
        if(lastProfile!=null && lastProfile!=avatarProfile && manual.avatarWorn.isNotEmpty())
            inputNotice="3D 체형이 바뀌었어요. 기존 의류 배치는 보존했어요. 위치 자동맞춤을 누르면 정면·기본 줌에서 새 체형으로 다시 맞춰요."
        lastProfile=avatarProfile
    }
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
            val image = GarmentGeometry.load(context.applicationContext,item)
            val uri = image?.uri ?: fittingUri(item)
            val aspect = image?.aspect ?: 1.2f
            // A slow image read must not replace a newer user selection or removal.
            if(manual.avatarWorn.containsKey(slot) || manual.selectionRequests.containsKey(slot))return@forEach
            manual.avatarWorn[slot] = WornGarment(item, uri, aspect,
                Offset(entry.centerX * canvasSize.width, entry.centerY * canvasSize.height),
                entry.width * canvasSize.width, entry.scale, entry.rotation, image?.region)
        }
        if (avatarMode && activeSlot !in manual.avatarWorn.keys)
            activeSlot = manual.avatarWorn.keys.maxByOrNull { it.ordinal }
        restoredAvatar = true
    }

    fun refit(slot: FitSlot) {
        val g = worn[slot] ?: return
        val p = if (avatarMode && avatarProfile != null)
            fitAvatar(slot, g.item, g.aspect, canvasSize, avatarProfile!!)
            else fit(slot, g.item, g.aspect, canvasSize, body)
        worn[slot] = g.copy(center = p.center, baseWidth = p.width, rotation = p.rotation, scale = 1f)
    }

    // A successful tap must either change placement or explain why it cannot.
    // Never silently fall back to centre-of-screen coordinates when ML Kit did not
    // find the landmarks needed for the selected garment.
    fun autoFitNow() {
        if (worn.isEmpty()) {
            inputNotice = "먼저 아래 옷장에서 입어볼 의류를 선택해주세요."
            return
        }
        if (canvasSize.width <= 0f || canvasSize.height <= 0f) {
            inputNotice = "피팅 화면을 준비 중이에요. 화면이 표시된 뒤 다시 눌러주세요."
            return
        }
        if (avatarMode) {
            if (avatarProfile == null) {
                inputNotice = "먼저 3D 체형 설정을 저장해주세요."
                return
            }
            val previous = worn.toMap()
            val placements=worn.mapNotNull { (slot,g) ->
                val region=g.region ?: return@mapNotNull null
                val content=fitAvatar(slot,g.item,g.aspect*region.height/region.width,canvasSize,avatarProfile!!)
                val p=region.expand(content,g.aspect)
                if(p.width<=0f || p.width>canvasSize.width*2f) null else slot to p
            }
            if(placements.isEmpty()) {
                inputNotice=if (worn.values.none { it.region != null })
                    "투명 의류 영역을 확인하지 못했어요. 기존 배치는 유지했어요. 옷장 사진을 확인하거나 수동으로 조절해주세요."
                else "의류 비율이나 투명 여백 때문에 자동 정렬할 수 없어요. 기존 배치는 유지했어요. 다른 사진을 선택해주세요."
                return
            }
            inspectAvatar=false; avatarCameraReset++
            placements.forEach { (slot,p) -> worn[slot]?.let {g -> worn[slot]=g.copy(center=p.center,baseWidth=p.width,rotation=p.rotation,scale=1f)} }
            val changed=worn.toMap()!=previous
            // A no-op wand tap must retain the undo point from the last actual alignment.
            if (changed) undoAvatar=previous
            inputNotice=(if(changed) "의류 영역 기준으로 정면 위치와 크기를 맞췄어요." else "이미 자동 정렬된 위치예요.")+
                if(placements.size<worn.size) " 영역·비율을 적용할 수 없는 옷은 기존 배치를 유지했어요." else ""
            return
        }
        if (bodyPhoto == null) {
            inputNotice = "먼저 전신 사진을 선택해주세요."
            return
        }
        if (poseStatus == PoseStatus.LOADING) {
            inputNotice = "사진에서 어깨·골반 위치를 찾고 있어요. 분석이 끝난 뒤 다시 눌러주세요."
            return
        }
        val detected = body
        if (detected == null) {
            inputNotice = "사진의 자세를 인식하지 못해 자동핏을 적용하지 않았어요. 전신이 보이는 정면 사진으로 다시 시도하거나 옷을 손으로 배치해주세요."
            return
        }
        val supported = worn.mapNotNull { (slot, garment) ->
            photoAutoPlacement(slot, garment.item, garment.aspect, canvasSize, detected)?.let { slot to it }
        }
        val before = worn.toMap()
        supported.forEach { (slot, p) ->
            worn[slot]?.let { g -> worn[slot] = g.copy(center=p.center, baseWidth=p.width, rotation=p.rotation, scale=1f) }
        }
        val skipped = worn.size - supported.size
        inputNotice = if (supported.isEmpty()) {
            "선택한 옷에 필요한 어깨·골반·발목 위치를 찾지 못했어요. 옷을 손으로 배치하거나 다시 촬영해주세요."
        } else if (before == worn.toMap()) {
            "인식 가능한 의류는 이미 관절 기준 위치와 크기예요." +
                if (skipped > 0) " 위치 검출이 부족한 ${skipped}개는 기존 배치를 유지했어요." else ""
        } else {
            "사진 관절 기준으로 ${supported.size}개 의류의 위치와 크기를 다시 맞췄어요." +
                if (skipped > 0) " 위치 검출이 부족한 ${skipped}개는 기존 배치를 유지했어요." else ""
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val requestGeneration = manual.selectPhoto(uri)
        scope.launch {
            val detected = try { FittingPoseDetector.detect(context.applicationContext, uri) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            if (requestGeneration != photoGeneration) return@launch
            body = detected
            poseStatus = if (detected != null) PoseStatus.FOUND else PoseStatus.NOT_FOUND
            // Detection only updates landmarks. Placement changes require explicit auto-fit;
            // a late result must not overwrite a drag or the other mode’s outfit.
        }
    }

    /** 옷장에서 옷을 누르면: 이미 입은 옷이면 벗고, 아니면 그 종류 자리에 입힌다 */
    fun toggle(item: WardrobeItem) {
        if (avatarMode && avatarProfile == null) {
            inputNotice = "먼저 3D 체형 설정을 저장해주세요."
            return
        }
        val slot = slotOf(item)
        if (!avatarMode) manual.cancelPhotoDrop(slot)
        if (worn[slot]?.item?.id == item.id) {
            manual.selectionRequests[slot]=(manual.selectionRequests[slot] ?: 0L)+1
            worn.remove(slot)
            if (activeSlot == slot) activeSlot = worn.keys.maxByOrNull { it.ordinal }
            return
        }
        val targetAvatar=avatarMode
        val targetPhotoGeneration=photoGeneration
        val target=worn
        val request=(manual.selectionRequests[slot] ?: 0L)+1
        manual.selectionRequests[slot]=request
        if (targetAvatar) {inspectAvatar=false;avatarCameraReset++}
        scope.launch {
            val image=GarmentGeometry.load(context.applicationContext,item)
            if(manual.selectionRequests[slot]!=request ||
                (!targetAvatar && targetPhotoGeneration!=photoGeneration))return@launch
            val uri=image?.uri ?: fittingUri(item)
            val aspect=image?.aspect ?: 1.2f
            val region=image?.region
            val content=if(targetAvatar && avatarProfile!=null)
                fitAvatar(slot,item,aspect*(region?.height ?: 1f)/(region?.width ?: 1f),canvasSize,avatarProfile!!)
                else fit(slot,item,aspect,canvasSize,body)
            val p=if(targetAvatar && region!=null)region.expand(content,aspect) else content
            if (targetAvatar && (p.width <= 0f || p.width > canvasSize.width * 2f)) {
                if (avatarMode == targetAvatar)
                    inputNotice="의류 비율이나 투명 여백 때문에 자동 배치할 수 없어요. 기존 옷 배치는 유지했어요. 다른 사진을 선택해주세요."
                return@launch
            }
            target[slot]=WornGarment(item,uri,aspect,p.center,p.width.coerceAtMost(canvasSize.width*2f),rotation=p.rotation,region=region)
            if(avatarMode==targetAvatar) {
                activeSlot=slot
                if(region==null)inputNotice="유효한 투명 의류 영역이 없어요. 원본 배경이 보일 수 있어요. 수동으로 배치하거나 옷장 사진을 확인해주세요."
            }

        }
    }

    fun drop(item: WardrobeItem, rootPoint: Offset) {
        if (avatarMode || dragGeneration != photoGeneration) return
        val center = photoDropPosition(rootPoint, canvasBounds, manual.photoDisplaySize)
        if (center == null) {
            inputNotice = "사진 밖에 놓아 배치를 취소했어요. 기존 옷은 그대로예요."
            return
        }
        val slot = slotOf(item)
        val ticket = manual.beginPhotoDrop(slot, center) ?: return
        scope.launch {
            val image = try { GarmentGeometry.load(context.applicationContext, item) }
            catch (cancelled: CancellationException) { manual.finishPhotoDrop(ticket); throw cancelled }
            catch (_: Exception) { null }
            if (manual.avatarMode || manual.showAi || manual.showLocalAi || manual.showOnDeviceAi || manual.showServerOutfitAi ||
                manual.photoGeneration != ticket.generation || manual.selectionRequests[slot] != ticket.request) {
                manual.finishPhotoDrop(ticket); return@launch
            }
            if (image == null) {
                manual.finishPhotoDrop(ticket)
                inputNotice = "의류 사진을 읽지 못했어요. 기존 배치는 유지했어요."
                return@launch
            }
            val dropCenter = resizePhotoPlacement(Placement(ticket.center, 1f, 0f), ticket.photoSize,
                ticket.canvas, manual.canvasSize)?.center ?: run { manual.finishPhotoDrop(ticket); return@launch }
            // Seed the compositor reference, not the finger offset, only with bilateral foot evidence.
            val automatic = photoDropPlacement(slot, item, image.aspect, manual.canvasSize, manual.body)
            val fallback = fit(slot, item, image.aspect, manual.canvasSize, null)
            val placement = automatic ?: fallback.copy(center = dropCenter)
            val garment = WornGarment(item, image.uri, image.aspect, placement.center,
                placement.width.coerceAtMost(manual.canvasSize.width * 2f), rotation = placement.rotation, region = image.region)
            if (manual.applyPhotoDrop(ticket, garment)) inputNotice = when {
                slot == FitSlot.SHOES && automatic != null -> "신발 사진을 발목 기준 초기 위치에 배치했어요. 현재는 한 쌍 미리보기이며, 좌우 발 방향과 변형은 자동합성 결과에서 별도 확인해요."
                slot == FitSlot.SHOES -> "발끝·발뒤꿈치 근거가 부족해 놓은 위치에 수동 배치했어요. 자동 발 정렬 성공이 아니에요."
                automatic != null -> "관절 기준 초기 위치에 배치했어요. 자동 착장 합성과 품질 검사는 별도예요. 드래그·크기·회전으로 조절할 수 있어요."
                else -> "신체 위치를 확인하지 못해 놓은 위치에 수동 배치했어요. 자동 정렬 성공이 아니에요."
            } + if (image.region == null) " 의류 배경이 남아 있을 수 있어요." else ""
        }
    }

    val active = activeSlot?.let { worn[it] }
    var showAi by manual::showAi
    var showLocalAi by manual::showLocalAi
    var aiSelection by manual::aiSelection
    if (manual.showServerOutfitAi) {
        ServerOutfitVtonScreen(manual.serverOutfitSelection,
            onBack = { manual.showServerOutfitAi = false }, model = serverOutfitModel)
        return
    }
    if (manual.showOnDeviceAi) {
        manual.deviceSelection?.let { selection ->
            OnDeviceVtonScreen(selection, onBack = { manual.showOnDeviceAi = false }, model = deviceModel)
        }
        return
    }
    if (showLocalAi) {
        LocalVtonScreen(
            defaultPhoto = bodyPhoto,
            garments = wardrobeItems.filter { TryOnPlan.categoryFor(it.category) != null }.map { item ->
                TryOnGarment(item.id, item.name, item.category,
                    item.originalUri?.takeIf { it.isNotBlank() }
                        ?: item.imageUri.ifBlank { item.garmentUri ?: item.cutoutUri.orEmpty() })
            },
            onBack = { showLocalAi = false }
        )
        return
    }
    if (showAi) {
        AiTryOnScreen(
            defaultPhoto = bodyPhoto,
            garments = aiSelection,
            onBack = { showAi = false },
            model = aiModel
        )
        return
    }


    BoxWithConstraints(Modifier.fillMaxSize().background(Cream).onGloballyPositioned { screenOrigin = it.boundsInRoot().topLeft }) {
    val compact = maxHeight < 480.dp
    val controlsMaxHeight = (maxHeight * if (compact) 0.18f else 0.32f).coerceAtMost(260.dp)
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
                avatarMode = false; inspectAvatar=false; activeSlot = manual.worn.keys.maxByOrNull { it.ordinal }
            }, label = { Text("내 전신 사진") }, modifier = Modifier.testTag("fittingPhotoMode"))
            FilterChip(selected = avatarMode, onClick = {
                avatarMode = true; activeSlot = manual.avatarWorn.keys.maxByOrNull { it.ordinal }
            }, label = { Text("내 3D 아바타") }, modifier = Modifier.testTag("fittingAvatarMode"))
        }


        if (avatarMode) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { autoFitNow() },
                    enabled = avatarProfile != null && worn.isNotEmpty(), modifier = Modifier.testTag("avatarAutoFit")) {
                    Icon(Icons.Filled.CenterFocusStrong, contentDescription = null, tint = Camel)
                    Text("위치 맞춤", color = Camel)
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
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
            if (!avatarMode) Button(onClick = { manual.openServerOutfit(currentDeviceSelection) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("aiTryOnWand"),
                colors = ButtonDefaults.buttonColors(containerColor = Camel)) {
                Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("AI로 입어보기")
            }
            if (!avatarMode && !compact) Text("선택한 사진과 상의·하의·신발을 확인하고 전송에 동의한 뒤 시작해요.",
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
                .onGloballyPositioned { canvasBounds = it.boundsInRoot() }
                .onSizeChanged { size ->
                    val old = canvasSize
                    val updated = Size(size.width.toFloat(), size.height.toFloat())
                    if (old.isUsableFittingSize() && updated.isUsableFittingSize() && old != updated) {
                        undoAvatar=undoAvatar?.mapValues { (_,g) -> g.copy(center=Offset(g.center.x*updated.width/old.width,g.center.y*updated.height/old.height),baseWidth=g.baseWidth*updated.width/old.width) }
                    }
                    manual.resizeCanvas(updated)
                }
                .testTag("fittingCanvas"),
            contentAlignment = Alignment.Center
        ) {
            if (dragItem != null) {
                val inPhoto = photoDropPosition(dragRootPoint, canvasBounds, manual.photoDisplaySize) != null
                val target = photoDropBounds(canvasBounds, manual.photoDisplaySize)
                Box(Modifier.matchParentSize().zIndex(3f).drawWithContent {
                    drawContent()
                    target?.let { drawRect(if (inPhoto) Camel else MediumGray,
                        topLeft = it.topLeft-canvasBounds.topLeft, size = it.size, style = Stroke(3.dp.toPx())) }
                }.testTag("photoDropTarget"))
            }
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
                            Modifier.fillMaxSize().testTag("fittingAvatarViewport"),
                            resetKey=avatarCameraReset,allowTouch=inspectAvatar || worn.isEmpty())
                    }
                } else {
                    val displayedPhoto = bodyPhoto
                    val displayedGeneration = photoGeneration
                    // A reselected identical URI is still a new generation with its own completion.
                    key(displayedGeneration) {
                        AsyncImage(model = displayedPhoto, contentDescription = "전신 사진",
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                            onSuccess = { success ->
                                val drawable = success.result.drawable
                                manual.acceptPhotoDisplaySize(displayedGeneration, displayedPhoto,
                                    Size(drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat()))
                            })
                    }
                }
                // Render back-to-front. Selection feedback follows the image; all input stays on the fixed canvas.
                (if(inspectAvatar && avatarMode) emptyList() else worn.values.toList()).sortedBy { slotOf(it.item).ordinal }.forEach { g ->
                    val w = g.baseWidth * g.scale
                    val h = w * g.aspect
                    AsyncImage(
                        model = g.uri,
                        contentDescription = g.item.name,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset { IntOffset((g.center.x - w / 2).roundToInt(), (g.center.y - h / 2).roundToInt()) }
                            .wrapContentSize(Alignment.TopStart,unbounded=true)
                            .requiredSize(with(density) { w.toDp() }, with(density) { h.toDp() })
                            .graphicsLayer(rotationZ = g.rotation, alpha = overlayAlpha)
                            .drawWithContent {
                                drawContent()
                                if (slotOf(g.item) == activeSlot) {
                                    val bounds = g.region ?: GarmentRegion(0f, 0f, 1f, 1f)
                                    drawRect(Camel, topLeft = Offset(bounds.left * size.width, bounds.top * size.height),
                                        size = Size(bounds.width * size.width, bounds.height * size.height),
                                        style = Stroke(2.dp.toPx()))
                                }
                            }
                            .semantics { selected = slotOf(g.item) == activeSlot }
                            .testTag("fittingOverlay-${g.item.id}")

                    )
                }
                // One stable canvas-space receiver above images and the native Surface.
                // Moving/rotating an image cannot change the coordinates of its own gesture detector.
                if(worn.isNotEmpty() && !inspectAvatar) {
                    Box(Modifier.matchParentSize().testTag("fittingGestureLayer")
                        .pointerInput(avatarMode) {
                            awaitEachGesture {
                                val down=awaitFirstDown(requireUnconsumed=false)
                                val hit=worn.values.sortedByDescending {slotOf(it.item).ordinal}.firstOrNull {g ->
                                    val d=down.position-g.center
                                    val r=Math.toRadians(-g.rotation.toDouble())
                                    val x=(d.x*kotlin.math.cos(r)-d.y*kotlin.math.sin(r)).toFloat()
                                    val y=(d.x*kotlin.math.sin(r)+d.y*kotlin.math.cos(r)).toFloat()
                                    val width=g.baseWidth*g.scale;val height=width*g.aspect
                                    val bounds=g.region ?: GarmentRegion(0f,0f,1f,1f)
                                    x in (bounds.left-.5f)*width..(bounds.left+bounds.width-.5f)*width &&
                                        y in (bounds.top-.5f)*height..(bounds.top+bounds.height-.5f)*height
                                }
                                if(hit!=null)manual.activeSlot=slotOf(hit.item)
                                waitForUpOrCancellation()
                            }
                        }
                        .pointerInput(avatarMode) {
                            detectTransformGestures {_,pan,zoom,rotation ->
                                val slot=manual.activeSlot ?: return@detectTransformGestures
                                worn[slot]?.let {g -> worn[slot]=g.copy(center=g.center+pan,
                                    scale=(g.scale*zoom).coerceIn(MIN_SCALE,MAX_SCALE),rotation=g.rotation+rotation)}
                            }
                        })
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

        // Frequently used edits stay visible; only the wardrobe/tools tray scrolls.
        if (active != null) {
            val enabled = !inspectAvatar
            fun setScale(v: Float) {
                val slot = activeSlot ?: return
                val g = worn[slot] ?: return
                worn[slot] = g.copy(scale = v.coerceIn(MIN_SCALE, MAX_SCALE))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("크기", style = MaterialTheme.typography.bodySmall, color = MediumGray, modifier = Modifier.width(56.dp))
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
                Text("투명도", style = MaterialTheme.typography.bodySmall, color = MediumGray, modifier = Modifier.width(56.dp))
                Slider(
                    value = overlayAlpha,
                    onValueChange = { overlayAlpha = it },
                    valueRange = 0.3f..1f,
                    colors = SliderDefaults.colors(thumbColor = Camel, activeTrackColor = Camel),
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp).testTag("manualAlpha")
                )
            }
        }

        Column(Modifier.fillMaxWidth().heightIn(max = controlsMaxHeight)
            .verticalScroll(controlsScroll).testTag("manualFittingControls")) {
        if (avatarMode) {
            avatarIssue?.let { Text(it, color = HeartPink, modifier = Modifier.padding(horizontal = 20.dp)) }
            overlayIssue?.let {
                Text(it, color = HeartPink, modifier = Modifier.padding(horizontal = 20.dp))
                TextButton(onClick = { askClearOverlay = true },
                    modifier = Modifier.padding(horizontal = 20.dp)) { Text("저장된 배치만 초기화") }
            }
        } else if (bodyPhoto != null && manualToolsExpanded) {
            TextButton(onClick = { autoFitNow() },
                enabled = worn.isNotEmpty(), modifier = Modifier.padding(horizontal = 20.dp).testTag("manualAutoAlign")) {
                Icon(Icons.Filled.CenterFocusStrong, contentDescription = null, tint = Camel)
                Text("옷 위치 맞춤", color = Camel)
            }
        }

        // Status changes must not resize the avatar viewport and move an otherwise unchanged auto-fit.
        inputNotice?.let { Text(it, color = Brown,
            modifier = Modifier.padding(horizontal = 20.dp).testTag("fittingInputNotice")) }
        if (!avatarMode) {
            val exportInput = bodyPhoto?.takeIf { manual.photoDisplaySize != null && canvasSize.isUsableFittingSize() && worn.isNotEmpty() }?.let {
                ManualPhotoSnapshot(it.toString(), photoGeneration, canvasSize,
                    worn.entries.sortedBy { entry -> entry.key.ordinal }.map { entry -> entry.value }, overlayAlpha, body = body)
            }
            OutlinedButton(onClick = { manualToolsExpanded = !manualToolsExpanded },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("manualToolsToggle")) {
                Text(if (manualToolsExpanded) "직접 배치 저장 닫기" else "직접 배치한 사진 저장")
            }
            if (manualToolsExpanded) ManualPhotoExportPanel(exportInput) { snapshot -> ManualPhotoExport.create(context.applicationContext, snapshot) }
            if (manual.pendingPhotoDrops.isNotEmpty()) TextButton(onClick = {
                manual.cancelPhotoDrops(); inputNotice = "의류 읽기를 취소했어요. 기존 배치는 그대로예요."
            }, modifier = Modifier.padding(horizontal = 20.dp).testTag("photoDropCancelLoading")) { Text("의류 읽기 취소") }
            if (manual.photoDropUndo != null) TextButton(onClick = {
                if (manual.undoPhotoDrop()) inputNotice = "마지막 드롭 전 의류 배치로 되돌렸어요. 옷장 기록은 바꾸지 않았어요."
            }, modifier = Modifier.padding(horizontal = 20.dp).testTag("photoDropUndo")) { Text("마지막 드롭 되돌리기") }
        }
        if (com.jeiu.fashionproject.BuildConfig.DEBUG && !com.jeiu.fashionproject.BuildConfig.INSTALL_TEST_BUILD) TextButton(onClick = { advancedToolsExpanded = !advancedToolsExpanded },
            modifier = Modifier.padding(horizontal = 20.dp).testTag("fittingAdvancedToggle")) {
            Text(if (advancedToolsExpanded) "고급 기능 접기" else "고급·개발용 기능", color = MediumGray)
        }
        if (advancedToolsExpanded && com.jeiu.fashionproject.BuildConfig.DEBUG && !com.jeiu.fashionproject.BuildConfig.INSTALL_TEST_BUILD) {
        if (!avatarMode) TextButton(onClick = {
            if (currentDeviceSelection != null) currentDeviceSelection?.let(manual::openOnDevice)
            else inputNotice = "전신 사진과 상의·하의·신발을 먼저 선택해주세요."
        }, modifier = Modifier.padding(horizontal = 20.dp).testTag("openOnDeviceFromFitting")) { Text("온디바이스 모델 검사") }
        TextButton(onClick = { showLocalAi = true },
            modifier = Modifier.padding(horizontal = 20.dp).testTag("openLocalVtonFromFitting")) {
            Icon(Icons.Filled.AutoFixHigh, contentDescription = null, tint = Camel)
            Spacer(Modifier.width(6.dp))
            Text("자체 AI로 입어보기 · 옷장에서 한 벌 선택", color = Camel)
        }
        if (!avatarMode) TextButton(onClick = {
            val selected = worn.values.map { g -> TryOnGarment(g.item.id, g.item.name, g.item.category,
                g.item.originalUri?.takeIf { it.isNotBlank() } ?: g.item.imageUri.ifBlank { g.item.garmentUri ?: g.item.cutoutUri.orEmpty() }) }
            inputNotice = when {
                aiModel.state.pending -> null
                bodyPhoto == null -> "먼저 전신 사진을 선택해주세요."
                worn.isEmpty() -> "먼저 옷장에서 입어볼 옷을 선택해주세요."
                else -> TryOnPlan.build(selected).issue
            }
            if (inputNotice == null) { aiSelection = selected; showAi = true }
        }, modifier = Modifier.padding(horizontal = 20.dp).testTag("openExternalTryOnFromFitting")) {
            Text("외부 AI 서버 · 별도 설정·전송 동의 필요", color = Camel)
        }
        }
        if(avatarMode) {
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp)) {
                TextButton(onClick={inspectAvatar=!inspectAvatar;if(!inspectAvatar)avatarCameraReset++},modifier=Modifier.testTag("fittingInspectModel")) {
                    Text(if(inspectAvatar) "옷 조작으로 돌아가기" else "모델 회전 확인")
                }
                if(undoAvatar!=null)TextButton(onClick={
                    manual.avatarWorn.clear();manual.avatarWorn.putAll(undoAvatar!!);undoAvatar=null
                    inputNotice="자동 정렬 전 배치로 되돌렸어요. 저장값은 별도로 유지돼요."
                },modifier=Modifier.testTag("avatarUndoFit")) {Text("자동 정렬 되돌리기")}
            }
            Text(if(inspectAvatar) "모델 확인 중 · 옷은 숨겼어요. 저장된 배치는 유지돼요." else "정면 2D 의류 합성 · 옷 조작 중 모델 회전은 잠겨 있어요.",
                modifier=Modifier.padding(horizontal=20.dp),style=MaterialTheme.typography.bodySmall,color=Brown)
        }
            TextButton(onClick={fittingHelp=!fittingHelp}) {Text(if(fittingHelp) "조작 안내 접기" else "조작 안내")}
            if (fittingHelp) Text(if (avatarMode) "옷을 올리면 모델은 정면으로 고정돼요. 옷 사진을 드래그하거나 아래 이동 버튼으로 조절하세요. 3D 의복 시뮬레이션은 아니에요."
                else "수동 배치 · 옷 사진을 드래그해 배치해요. 자동 정렬은 AI 합성이 아니에요.",
                color = Brown, style = MaterialTheme.typography.bodySmall)
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
        if (avatarMode && avatarProfile != null || !avatarMode && bodyPhoto != null && manualToolsExpanded) {
            val enabled = active != null && !inspectAvatar
            fun moveActive(dx: Float, dy: Float) {
                val slot = activeSlot ?: return
                val current = worn[slot] ?: return
                if (canvasSize.width <= 0f || canvasSize.height <= 0f) return
                val x = (current.center.x + dx * canvasSize.width).coerceIn(-canvasSize.width, canvasSize.width * 2f)
                val y = (current.center.y + dy * canvasSize.height).coerceIn(-canvasSize.height, canvasSize.height * 2f)
                worn[slot] = current.copy(center = Offset(x, y))
                inputNotice = null
            }
            if (avatarMode) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("위치", color = MediumGray, style = MaterialTheme.typography.bodySmall)
                    listOf(
                        Triple("←", "fittingMoveLeft", -0.035f to 0f),
                        Triple("↑", "fittingMoveUp", 0f to -0.035f),
                        Triple("↓", "fittingMoveDown", 0f to 0.035f),
                        Triple("→", "fittingMoveRight", 0.035f to 0f)
                    ).forEach { (label, tag, step) ->
                        IconButton(onClick = { moveActive(step.first, step.second) },
                            enabled = enabled, modifier = Modifier.testTag(tag)) {
                            Text(label, color = if (enabled) Brown else MediumGray,
                                style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }

        // 입어볼 옷 선택 (종류별로 한 벌씩 함께 입을 수 있음)
        Text(
            "입어볼 옷 선택 · 종류별로 한 벌씩 배치해요",
            style = MaterialTheme.typography.titleSmall, color = CharcoalBlack, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        if (!avatarMode) Text("카드를 길게 누른 뒤 사진에 놓으세요 · 클릭 선택도 가능해요",
            style = MaterialTheme.typography.labelSmall, color = Brown, modifier = Modifier.padding(horizontal = 20.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(bottom = 20.dp).testTag("manualWardrobe")
        ) {
            items(wardrobeItems, key = { it.id }) { item ->
                var cardOrigin by remember(item.id) { mutableStateOf(Offset.Zero) }
                val isWorn = worn[slotOf(item)]?.item?.id == item.id
                val cut = hasCutout(item)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(84.dp)
                ) {
                    Card(
                        onClick = { toggle(item) },
                        modifier = Modifier.testTag("manualGarment-${item.id}")
                            .onGloballyPositioned { cardOrigin = it.boundsInRoot().topLeft }
                            .pointerInput(item.id, avatarMode, bodyPhoto, photoGeneration) {
                                if (!avatarMode) detectDragGesturesAfterLongPress(
                                    onDragStart = { local ->
                                        if (manual.bodyPhoto != null && manual.photoDisplaySize != null) {
                                            dragItem = item; dragGeneration = manual.photoGeneration
                                            dragRootPoint = cardOrigin + local
                                        } else inputNotice = "먼저 전신 사진을 선택하고 사진 표시를 기다려주세요."
                                    },
                                    onDrag = { change, amount ->
                                        if (dragItem?.id == item.id) { change.consume(); dragRootPoint += amount }
                                    },
                                    onDragEnd = { val chosen = dragItem; dragItem = null; if (chosen != null) drop(chosen, dragRootPoint) },
                                    onDragCancel = { dragItem = null }
                                )
                            },
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
    dragItem?.let { item ->
        Surface(Modifier.offset { IntOffset((dragRootPoint.x-screenOrigin.x).roundToInt()-36.dp.roundToPx(),
            (dragRootPoint.y-screenOrigin.y).roundToInt()-36.dp.roundToPx()) }
            .size(72.dp).testTag("wardrobeDragGhost"), shape = RoundedCornerShape(12.dp), shadowElevation = 8.dp,
            color = Cream, border = BorderStroke(2.dp, Camel)) {
            AsyncImage(fittingUri(item), "이동 중인 의류", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
    }
    }
}
