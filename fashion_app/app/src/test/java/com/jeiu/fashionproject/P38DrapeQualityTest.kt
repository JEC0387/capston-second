package com.jeiu.fashionproject

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.ui.screens.*
import com.jeiu.fashionproject.ui.viewmodel.FittingPoseDetector
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Independently drawn boundaries, with joint centres deliberately inside the visible outline.
 * Reproduces the P37 failure category, not its unrecorded ML Kit coordinates. No real photographs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class P38DrapeQualityTest {
    private val size = Size(240f, 480f)
    private val backdrop = Color.rgb(232, 229, 218)
    private fun bitmap(w: Int, h: Int, color: Int = Color.TRANSPARENT) =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
    private fun polygon(canvas: Canvas, color: Int, vararg xy: Float) {
        val path = Path().apply {
            moveTo(xy[0], xy[1])
            for (i in 2 until xy.size step 2) lineTo(xy[i], xy[i + 1])
            close()
        }
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }
    private fun person() = bitmap(240, 480, backdrop).also {
        val canvas = Canvas(it)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(190, 143, 112)
        canvas.drawOval(99f, 58f, 141f, 117f, paint)
        polygon(canvas, Color.rgb(173, 171, 162), 84f, 128f, 156f, 128f, 163f, 256f, 77f, 256f)
        paint.color = Color.rgb(117, 119, 120)
        paint.strokeWidth = 27f
        paint.strokeCap = Paint.Cap.ROUND
        for ((hip, knee, ankle) in listOf(Triple(91f, 88f, 80f), Triple(149f, 152f, 160f))) {
            canvas.drawLine(hip, 265f, knee, 340f, paint)
            canvas.drawLine(knee, 340f, ankle, 425f, paint)
        }
        paint.color = Color.rgb(50, 45, 43)
        paint.strokeWidth = 16f
        canvas.drawLine(81f, 433f, 62f, 445f, paint)
        canvas.drawLine(159f, 433f, 178f, 445f, paint)
    }
    private fun body(): FittingPoseDetector.BodyPoints {
        fun side(left: Boolean) = FittingPoseDetector.SidePoints(
            shoulder = Offset(if (left) 85f else 155f, 130f),
            elbow = Offset(if (left) 55f else 185f, 200f),
            wrist = Offset(if (left) 68f else 172f, 264f),
            hip = Offset(if (left) 104f else 136f, 260f),
            knee = Offset(if (left) 88f else 152f, 340f),
            ankle = Offset(if (left) 80f else 160f, 425f),
            heel = Offset(if (left) 81f else 159f, 433f),
            toe = Offset(if (left) 72f else 168f, 439f)
        )
        val left = side(true); val right = side(false)
        return FittingPoseDetector.BodyPoints(240, 480, left.shoulder!! to right.shoulder!!,
            left.hip!! to right.hip!!, left.ankle!! to right.ankle!!, left, right, Offset(120f, 88f), 42f)
    }
    private fun top() = bitmap(220, 200).also {
        polygon(Canvas(it), Color.rgb(40, 105, 142), 60f, 35f, 88f, 20f, 132f, 20f,
            160f, 35f, 208f, 65f, 194f, 95f, 160f, 80f, 160f, 185f, 60f, 185f,
            60f, 80f, 26f, 95f, 12f, 65f)
    }
    private fun pants() = bitmap(180, 280).also {
        polygon(Canvas(it), Color.rgb(53, 68, 96), 25f, 12f, 155f, 12f, 160f, 95f,
            145f, 265f, 103f, 265f, 90f, 110f, 77f, 265f, 35f, 265f, 20f, 95f)
    }
    private fun skirt() = bitmap(180, 220).also {
        polygon(Canvas(it), Color.rgb(132, 73, 69), 55f, 15f, 125f, 15f, 165f, 205f, 15f, 205f)
    }
    private fun shoes() = bitmap(180, 150).also {
        for (x in listOf(50f, 130f)) polygon(Canvas(it), Color.rgb(210, 180, 122),
            x - 13, 133f, x - 23, 45f, x - 18, 18f, x + 18, 18f, x + 23, 45f, x + 13, 133f)
    }
    private var lastReports: List<String> = emptyList()
    private fun render(name: String, category: String, sub: String?, garment: Bitmap,
                       source: Bitmap = person(), pose: FittingPoseDetector.BodyPoints = body(),
                       adjust: (WornGarment) -> WornGarment = { it }): Bitmap {
        val model = source
        val item = WardrobeItem(name, name, category, "synthetic", 0, "file:///owned-$name.png", subCategory = sub, isCutout = true)
        val slot = when (category) { "상의" -> FitSlot.TOP; "하의" -> FitSlot.BOTTOM; else -> FitSlot.SHOES }
        val aspect = garment.height.toFloat() / garment.width
        val placement = requireNotNull(photoAutoPlacement(slot, item, aspect, size, pose))
        val worn = WornGarment(item, item.imageUri, aspect, placement.center, placement.width, rotation = placement.rotation)
        val output = LocalDrapeCompositor.render(model, listOf(adjust(worn) to garment), size, 1f, pose)
        lastReports = output.reports
        val folder = File("build/p38-private/quality").apply { mkdirs() }
        File(folder, "$name.png").writeBytes(output.png)
        File(folder, "$name-source.png").outputStream().use { model.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(folder, "$name-garment.png").outputStream().use { garment.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val decoded = requireNotNull(BitmapFactory.decodeByteArray(output.png, 0, output.png.size))
        // These exact points are outside the garment and must not change merely to hide old clothes.
        for ((x, y) in listOf(120 to 88, 20 to 240, 220 to 240, 120 to 20)) {
            assertEquals("Preserve face/background at $x,$y", model.getPixel(x, y), decoded.getPixel(x, y))
        }
        model.recycle(); garment.recycle()
        return decoded
    }
    private fun blueAt(output: Bitmap, x: Int, y: Int) {
        val c = output.getPixel(x, y)
        assertTrue("Expected garment at independent silhouette point $x,$y, got ${Integer.toHexString(c)}",
            Color.blue(c) > Color.red(c) + 20)
    }

    @Test fun waistUsesVisibleTorsoOutlineInsteadOfNarrowInternalHipCentres() {
        val output = render("waist", "상의", "반팔 상의", top())
        // Drawn torso spans about 79..161 here; hips are only 104..136, not a body-width measurement.
        blueAt(output, 82, 235); blueAt(output, 158, 235)
        output.recycle()
    }

    @Test fun bothPantsLegsCoverTheirVisibleKneeCrossSections() {
        val output = render("pants", "하의", "긴바지", pants())
        // 27px limbs at x=88 and x=152: interior boundary probes, independent of mapping math.
        blueAt(output, 78, 340); blueAt(output, 162, 340)
        output.recycle()
    }

    @Test fun skirtHemCoversSeparatedLegOutlineWithoutCollapsingToHipJointWidth() {
        val output = render("skirt", "하의", "치마", skirt())
        for (x in listOf(80, 160)) {
            val c = output.getPixel(x, 330)
            assertTrue("Skirt must reach visible leg span at $x,330", Color.red(c) > Color.green(c) + 30)
        }
        output.recycle()
    }

    @Test fun eachShoeUsesVisibleToeExtentRatherThanShortInternalLandmarkSegment() {
        val output = render("shoes", "신발", null, shoes())
        for (x in listOf(62, 178)) {
            val c = output.getPixel(x, 444)
            assertTrue("Shoe must reach independently drawn toe $x,444", Color.red(c) > Color.blue(c) + 40 && Color.red(c) > 150)
        }
        output.recycle()
    }

    @Test fun busyBackdropAndOverCapConnectedForegroundDoNotInventMeasuredEdges() {
        val busy = person().also {
            for (x in 0 until it.width) { it.setPixel(x, 0, if (x % 2 == 0) Color.BLACK else Color.WHITE) }
        }
        val oversized = person().also {
            Canvas(it).drawRect(5f, 128f, 235f, 270f, Paint().apply { color = Color.rgb(173, 171, 162) })
        }
        for ((name, model) in listOf("busy" to busy, "over-cap" to oversized)) {
            val output = render(name, "상의", "반팔 상의", top(), source = model)
            assertTrue("Uncertain silhouette must be reported, not measured", lastReports.any { it.startsWith("사진 윤곽을 확인하지 못해") })
            val c = output.getPixel(82, 235)
            assertFalse("Do not expand to ambiguous foreground", Color.blue(c) > Color.red(c) + 20)
            output.recycle()
        }
    }

    @Test fun measuredShoeWidthsThatCollideAreRejectedAfterExpansion() {
        val model = person().also {
            val canvas = Canvas(it); val paint = Paint().apply { color = Color.BLACK }
            canvas.drawRect(100f, 423f, 120f, 458f, paint)
            canvas.drawRect(121f, 423f, 141f, 458f, paint)
        }
        val base = body()
        val close = base.copy(
            left = base.left!!.copy(ankle = Offset(110f, 425f), heel = Offset(110f, 430f), toe = Offset(110f, 440f)),
            right = base.right!!.copy(ankle = Offset(130f, 425f), heel = Offset(130f, 430f), toe = Offset(130f, 440f))
        )
        val failure = assertThrows(LocalDrapeFailure::class.java) {
            render("touching-shoes", "신발", null, shoes(), source = model, pose = close)
        }
        assertTrue(failure.message.orEmpty().contains("신발 윤곽이 서로 겹쳐"))
        model.recycle()
    }

    @Test fun manualTranslationScaleAndRotationRemainAppliedToCorrectedSilhouette() {
        val original = render("adjustment-baseline", "상의", "반팔 상의", top())
        val changed = render("adjusted", "상의", "반팔 상의", top(), adjust = {
            it.copy(center = it.center + Offset(12f, 7f), scale = 1.08f, rotation = it.rotation + 5f)
        })
        var differences = 0
        for (y in 130..260) for (x in 50..190) if (original.getPixel(x, y) != changed.getPixel(x, y)) differences++
        assertTrue("Manual adjustment must affect rendered pixels", differences > 1000)
        assertTrue(lastReports.any { it.contains("기존 의상·발 윤곽") })
        original.recycle(); changed.recycle()
    }

    @Test fun silhouetteExpansionCapCannotShrinkAnExistingWideHipSkirt() {
        val base = body()
        val left = base.left!!.copy(hip = Offset(70f, 260f))
        val right = base.right!!.copy(hip = Offset(170f, 260f))
        val wide = FittingPoseDetector.BodyPoints(240, 480, left.shoulder!! to right.shoulder!!,
            left.hip!! to right.hip!!, left.ankle!! to right.ankle!!, left, right, Offset(120f, 88f), 42f)
        val model = person().also {
            polygon(Canvas(it), Color.rgb(173, 171, 162), 84f, 128f, 156f, 128f, 185f, 256f, 55f, 256f)
        }
        val output = render("wide-hip-skirt", "하의", "치마", skirt(), source = model, pose = wide)
        // Existing hip-based hem is x=35..205. A shoulder-based expansion cap must not shrink it.
        for (x in listOf(40, 200)) {
            val c = output.getPixel(x, 335)
            assertTrue("Preserve the original wide-hip skirt envelope at $x,335", Color.red(c) > Color.green(c) + 30)
        }
        output.recycle()
    }
}
