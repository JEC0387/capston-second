package com.jeiu.fashionproject

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Process
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.ui.components.PremiumAvatarEditor
import com.jeiu.fashionproject.ui.screens.VirtualFittingScreen
import com.jeiu.fashionproject.ui.viewmodel.BodySegmenter
import com.jeiu.fashionproject.ui.viewmodel.SilhouetteIssue
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import kotlin.math.abs

/**
 * Opt-in on a separately installed QA package ONLY; never run on the user's Preview data.
 * Invoke each method separately, with a QA-only process stop between them. No clear/reset,
 * no camera/gallery permission changes, no HTTP or AI-generation requests, no real photos.
 * The renderer, editor, fitting controls and SharedPreferences stores are production code.
 */
@RunWith(AndroidJUnit4::class)
class P16DeviceQaTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val qaIds = listOf("p16-qa-top", "p16-qa-bottom", "p16-qa-asymmetric", "p16-qa-opaque", "p16-qa-tall")
    private val evidence get() = File(app.noBackupFilesDir, "p16-qa-${BuildConfig.GIT_SHA.take(8)}.json")
    private val ownership get() = File(app.noBackupFilesDir, "p16-qa-fixture-owner")

    @Before fun requireIsolatedOptIn() {
        assumeTrue("Explicit p16DeviceQa=true required", InstrumentationRegistry.getArguments().getString("p16DeviceQa") == "true")
        check(BuildConfig.PREVIEW_BUILD && BuildConfig.DEVICE_QA_BUILD) { "QA + Preview flags required before any write" }
        check(app.packageName == "com.jeiu.fashionproject.preview.qa" && BuildConfig.APPLICATION_ID == app.packageName) {
            "Refusing to use production/Preview/user package"
        }
    }

    private fun record(stage: String, data: JSONObject = JSONObject()) {
        val root = if (evidence.exists()) JSONObject(evidence.readText()) else JSONObject()
            .put("codeSha", BuildConfig.GIT_SHA).put("firstPid", Process.myPid())
            .put("syntheticInputsOnly", true).put("externalInferenceRequests", 0).put("stages", JSONArray())
        root.getJSONArray("stages").put(JSONObject().put("stage", stage).put("at", System.currentTimeMillis()).put("data", data))
        evidence.writeText(root.toString(2))
    }

    private fun fixture(id: String, category: String, width: Int, height: Int, paint: (Canvas, Paint) -> Unit): WardrobeItem {
        val folder = File(app.filesDir, "p16-qa-images").apply { mkdirs() }
        check(folder.canonicalFile.parentFile == app.filesDir.canonicalFile)
        val file = File(folder, "$id.png")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            paint(Canvas(bitmap), Paint().apply { color = Color.rgb(165, 103, 70) })
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
        BitmapFactory.decodeFile(file.path).also { decoded ->
            assertNotNull(decoded); assertEquals(width, decoded.width); assertEquals(height, decoded.height); decoded.recycle()
        }
        val uri = Uri.fromFile(file).toString()
        return WardrobeItem(id = id, name = id, category = category, colorName = "QA", colorValue = 0xffa56746,
            imageUri = uri, originalUri = uri, cutoutUri = if (id.endsWith("opaque")) null else uri,
            isCutout = !id.endsWith("opaque"), addedAt = 1L)
    }

    private fun seedOwnedSyntheticData(): AvatarProfile {
        val wardrobePrefs = app.getSharedPreferences("wardrobe_store", 0)
        val profilePrefs = app.getSharedPreferences("avatar_visual_profile_v1", 0)
        if (!ownership.exists()) {
            check(!wardrobePrefs.contains("items") && profilePrefs.all.isEmpty() && AvatarFittingStore.load(app).isEmpty()) {
                "Existing QA data has no fixture ownership marker; preserve it"
            }
            ownership.writeText("FashionAI P16 synthetic instrumentation fixtures only")
        } else {
            check(ownership.readText() == "FashionAI P16 synthetic instrumentation fixtures only")
            val old = JSONArray(wardrobePrefs.getString("items", "[]"))
            check((0 until old.length()).all { old.getJSONObject(it).getString("id") in qaIds }) { "Foreign QA wardrobe data retained" }
            check(AvatarFittingStore.load(app).all { it.itemId in qaIds }) { "Foreign QA placements retained" }
        }
        check(!evidence.exists()) { "Retain previous evidence; do not repeat this successful/partial run automatically" }
        val items = listOf(
            fixture(qaIds[0], "상의", 160, 200) { c, p -> c.drawRect(28f, 35f, 132f, 165f, p) },
            fixture(qaIds[1], "하의", 160, 260) { c, p ->
                c.drawRect(35f, 20f, 125f, 80f, p); c.drawRect(35f, 80f, 72f, 240f, p); c.drawRect(88f, 80f, 125f, 240f, p)
            },
            fixture(qaIds[2], "상의", 220, 240) { c, p -> c.drawRect(85f, 20f, 195f, 130f, p) },
            fixture(qaIds[3], "상의", 160, 200) { c, _ -> c.drawColor(Color.LTGRAY) },
            fixture(qaIds[4], "상의", 32, 2048) { c, p -> c.drawRect(8f, 16f, 24f, 2032f, p) }
        )
        check(WardrobeStore.items.value.isEmpty()) { "Run fixture setup in a fresh QA process" }
        WardrobeStore.writeForRestore(app, items)
        WardrobeStore.load(app)
        assertEquals(qaIds.toSet(), WardrobeStore.items.value.map { it.id }.toSet())
        val profile = AvatarProfile(170f, 65f, centimeters = AvatarCentimeters.generate(170f, 65f, 1L)!!)
        assertTrue(AvatarProfileStore.save(app, profile))
        assertTrue(AvatarFittingStore.save(app, emptyList()))
        record("fixture_seeded", JSONObject().put("garmentCount", items.size))
        return profile
    }

    private fun ruler() = compose.onNodeWithTag("avatarCmRuler").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
    private fun editorClick(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()
    private fun selectPart(part: AvatarPart) = editorClick("cmPart-${part.name}")
    private fun selectGarment(id: String) {
        compose.onNodeWithTag("manualWardrobe").performScrollTo().performScrollToNode(hasTestTag("manualGarment-$id"))
        compose.onNodeWithTag("manualGarment-$id").performClick()
    }
    private fun awaitGarment(id: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithTag("fittingOverlay-$id").fetchSemanticsNodes().isNotEmpty()
    }
    private fun savePlacements(): List<AvatarOverlay> {
        compose.onNodeWithTag("avatarSaveFitting").performClick()
        compose.onNodeWithTag("fittingInputNotice").assertTextContains("저장했어요", substring = true)
        return AvatarFittingStore.load(app)
    }
    private fun samePlacements(expected: List<AvatarOverlay>, actual: List<AvatarOverlay>) {
        assertEquals(expected.map { it.itemId }.toSet(), actual.map { it.itemId }.toSet())
        expected.forEach { a ->
            val b = actual.single { it.itemId == a.itemId }
            assertEquals(a.centerX, b.centerX, .002f); assertEquals(a.centerY, b.centerY, .002f)
            assertEquals(a.width, b.width, .002f); assertEquals(a.scale, b.scale, .001f); assertEquals(a.rotation, b.rotation, .001f)
        }
    }
    private fun json(entries: List<AvatarOverlay>) = JSONArray().also { array -> entries.forEach { e ->
        array.put(JSONObject().put("id", e.itemId).put("x", e.centerX).put("y", e.centerY)
            .put("width", e.width).put("scale", e.scale).put("rotation", e.rotation))
    } }
    private fun entries(array: JSONArray) = (0 until array.length()).map { index -> array.getJSONObject(index).let { e ->
        AvatarOverlay(e.getString("id"), e.getDouble("x").toFloat(), e.getDouble("y").toFloat(),
            e.getDouble("width").toFloat(), e.getDouble("scale").toFloat(), e.getDouble("rotation").toFloat())
    } }

    @Test fun editManipulateAndSaveSyntheticFixtures() {
        val initial = seedOwnedSyntheticData()
        // Executes the bundled model locally. A blank synthetic bitmap must not become a measured body.
        val segmenter = BodySegmenter(app)
        val blank = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.LTGRAY) }
        try {
            assertTrue("Bundled MediaPipe native/model initialization failed", segmenter.isAvailable)
            val measured = segmenter.inspectWidths(blank, emptyMap())
            assertNull(measured.widths); assertEquals(SilhouetteIssue.LANDMARKS, measured.issue)
            record("mediapipe_synthetic_inference", JSONObject().put("bodyMeasurementProduced", false))
        } finally { segmenter.close(); blank.recycle() }

        val screen = mutableStateOf("editor")
        val editorInstance = mutableStateOf(0)
        val widthFraction = mutableStateOf(1f)
        compose.setContent { MaterialTheme {
            if (screen.value == "editor") key(editorInstance.value) {
                PremiumAvatarEditor(initial, initial, onDismiss = { screen.value = "closed" },
                    onSave = { AvatarProfileStore.save(app, it) })
            } else if (screen.value == "fitting") {
                Box(Modifier.fillMaxWidth(widthFraction.value).fillMaxHeight()) {
                    VirtualFittingScreen(startAvatar = true, onBack = {})
                }
            }
        } }
        compose.onNodeWithTag("cmAvatarViewport").assertIsDisplayed()
        editorClick("rulerPlus")
        compose.onNodeWithContentDescription("3D 조절 닫기").performClick()
        compose.onNodeWithTag("cmDiscardDraft").performClick()
        assertEquals(initial, AvatarProfileStore.current.value)
        record("draft_discard_preserves_saved_profile")
        compose.runOnIdle { editorInstance.value++; screen.value = "editor" }

        val expected = initial.centimeters!!.values.mapValues { it.value.cm }.toMutableMap()
        editorClick("rulerPlus"); expected[AvatarPart.WAIST] = expected.getValue(AvatarPart.WAIST) + .5f
        selectPart(AvatarPart.SHOULDER)
        editorClick("rulerDirectInput")
        expected[AvatarPart.SHOULDER] = expected.getValue(AvatarPart.SHOULDER) + .5f
        compose.onNodeWithTag("rulerInputField").performTextReplacement(String.format(Locale.US, "%.1f", expected.getValue(AvatarPart.SHOULDER)))
        compose.onNodeWithTag("rulerInputConfirm").performClick()
        selectPart(AvatarPart.CHEST)
        val chestBefore = ruler()
        compose.onNodeWithTag("avatarCmRuler").performScrollTo().performTouchInput {
            swipe(Offset(center.x + 60f, center.y), Offset(center.x - 60f, center.y), 600)
        }
        expected[AvatarPart.CHEST] = ruler()
        assertTrue(ruler() > chestBefore); assertEquals(0f, ruler() * 2f % 1f, .001f)
        for (part in listOf(AvatarPart.HIP, AvatarPart.THIGH, AvatarPart.LEG, AvatarPart.TORSO)) {
            selectPart(part); editorClick("rulerPlus"); expected[part] = expected.getValue(part) + .5f
        }
        for (part in listOf(AvatarPart.WAIST, AvatarPart.SHOULDER, AvatarPart.CHEST, AvatarPart.HIP, AvatarPart.THIGH, AvatarPart.LEG, AvatarPart.TORSO)) {
            selectPart(part); assertEquals("Independent $part draft", expected.getValue(part), ruler(), .001f)
        }
        editorClick("cmEditorSave"); compose.onNodeWithTag("avatarApplyCancel").performClick()
        assertEquals(initial, AvatarProfileStore.current.value)
        editorClick("cmEditorSave"); compose.onNodeWithTag("avatarApplyApprove").performClick()
        val savedProfile = AvatarProfileStore.current.value!!
        expected.forEach { (part, value) -> assertEquals(value, savedProfile.centimeters!![part], .001f) }
        record("seven_parts_saved_after_explicit_approval", JSONObject().put("profile", AvatarProfileStore.encode(savedProfile)))

        compose.runOnIdle { screen.value = "fitting" }
        compose.onNodeWithTag("fittingAvatarViewport").assertIsDisplayed()
        selectGarment(qaIds[3]); awaitGarment(qaIds[3])
        val opaque = savePlacements()
        compose.onNodeWithTag("avatarAutoFit").performClick()
        compose.onNodeWithTag("fittingInputNotice").assertTextContains("투명 의류 영역을 확인하지 못했어요", substring = true)
        samePlacements(opaque, savePlacements())
        record("opaque_manual_preserved_no_false_autofit")

        selectGarment(qaIds[2]); awaitGarment(qaIds[2])
        compose.onNodeWithTag("avatarAutoFit").performClick()
        val asymmetric = savePlacements()
        assertEquals(1, asymmetric.size); assertTrue(asymmetric.single().valid())
        record("asymmetric_transparent_margin", JSONObject().put("placements", json(asymmetric)))
        selectGarment(qaIds[4])
        compose.waitUntil(10_000) { runCatching {
            compose.onNodeWithTag("fittingInputNotice").assertTextContains("의류 비율", substring = true)
        }.isSuccess }
        compose.onNodeWithTag("fittingOverlay-${qaIds[4]}").assertDoesNotExist()
        samePlacements(asymmetric, savePlacements())
        record("extreme_aspect_rejected_previous_garment_preserved")

        for (id in qaIds.take(2)) { selectGarment(id); awaitGarment(id) }
        val before = savePlacements()
        val top = before.single { it.itemId == qaIds[0] }
        compose.onNodeWithTag("fittingGestureLayer").performTouchInput {
            val point = Offset(top.centerX * width, top.centerY * height)
            click(point)
            swipe(point, point + Offset(45f, 24f), 600)
        }
        compose.onNodeWithTag("fittingOverlay-${qaIds[0]}").assertIsSelected()
        val dragged = savePlacements()
        assertTrue(dragged.single { it.itemId == qaIds[0] }.centerX > top.centerX)
        samePlacements(before.filter { it.itemId == qaIds[1] }, dragged.filter { it.itemId == qaIds[1] })
        val moving = dragged.single { it.itemId == qaIds[0] }
        compose.onNodeWithTag("fittingGestureLayer").performTouchInput {
            val point = Offset(moving.centerX * width, moving.centerY * height)
            down(0, point + Offset(-20f, 0f)); down(1, point + Offset(20f, 0f))
            moveTo(0, point + Offset(-45f, -20f)); moveTo(1, point + Offset(45f, 20f))
            up(0); up(1)
        }
        val transformed = savePlacements()
        val transformedTop = transformed.single { it.itemId == qaIds[0] }
        assertTrue(transformedTop.scale > moving.scale)
        assertTrue(abs(transformedTop.rotation - moving.rotation) > 1f)
        samePlacements(dragged.filter { it.itemId == qaIds[1] }, transformed.filter { it.itemId == qaIds[1] })
        val bottom = transformed.single { it.itemId == qaIds[1] }
        compose.onNodeWithTag("fittingGestureLayer").performTouchInput {
            val point = Offset(bottom.centerX * width, bottom.centerY * height)
            click(point); swipe(point, point + Offset(-42f, 20f), 600)
        }
        compose.onNodeWithTag("fittingOverlay-${qaIds[1]}").assertIsSelected()
        val manual = savePlacements()
        samePlacements(transformed.filter { it.itemId == qaIds[0] }, manual.filter { it.itemId == qaIds[0] })
        assertTrue(manual.single { it.itemId == qaIds[1] }.centerX < bottom.centerX)
        record("single_drag_multi_touch_rotation_independent_selection", JSONObject().put("placements", json(manual)))

        compose.onNodeWithTag("avatarAutoFit").performClick()
        val auto = savePlacements()
        assertTrue(auto.all { it.scale == 1f && abs(it.rotation) < .001f })
        compose.onNodeWithTag("avatarAutoFit").performClick()
        compose.onNodeWithTag("fittingInputNotice").assertTextContains("이미 자동 정렬된 위치예요", substring = true)
        compose.onNodeWithTag("avatarUndoFit").performScrollTo().performClick()
        samePlacements(manual, savePlacements())
        record("autofit_noop_then_undo_restores_original_manual_state")
        compose.onNodeWithTag("fittingInspectModel").performScrollTo().performClick()
        compose.onNodeWithTag("fittingGestureLayer").assertDoesNotExist()
        qaIds.take(2).forEach { compose.onNodeWithTag("fittingOverlay-$it").assertDoesNotExist() }
        compose.onNodeWithTag("fittingAvatarViewport").assertIsDisplayed()
        compose.onNodeWithTag("fittingInspectModel").performScrollTo().performClick()
        samePlacements(manual, savePlacements())
        record("inspect_mode_hides_flat_overlays_then_preserves_placement")
        compose.runOnIdle { widthFraction.value = .85f }
        samePlacements(manual, savePlacements())
        compose.runOnIdle { widthFraction.value = 1f }
        samePlacements(manual, savePlacements())
        record("canvas_resize_preserves_normalized_placement")
        val root = JSONObject(evidence.readText()).put("firstPhaseComplete", true)
            .put("expectedProfile", AvatarProfileStore.encode(AvatarProfileStore.current.value!!))
            .put("expectedPlacements", json(AvatarFittingStore.load(app)))
        evidence.writeText(root.toString(2))
    }

    @Test fun restoreSavedFixturesAfterSeparateProcessRestart() {
        check(evidence.exists() && ownership.exists()) { "Run the first method successfully before restart verification" }
        val proof = JSONObject(evidence.readText())
        check(proof.getBoolean("firstPhaseComplete") && proof.getString("codeSha") == BuildConfig.GIT_SHA)
        assertNotEquals("A different process is required; stop ONLY the separate QA app", proof.getInt("firstPid"), Process.myPid())
        AvatarProfileStore.load(app)
        assertEquals(proof.getString("expectedProfile"), AvatarProfileStore.encode(AvatarProfileStore.current.value!!))
        val expected = entries(proof.getJSONArray("expectedPlacements"))
        samePlacements(expected, AvatarFittingStore.load(app))
        compose.setContent { MaterialTheme { VirtualFittingScreen(startAvatar = true, onBack = {}) } }
        expected.forEach { awaitGarment(it.itemId) }
        compose.onNodeWithTag("fittingAvatarViewport").assertIsDisplayed()
        // Saving the re-rendered production state verifies it was restored without silently auto-fitting.
        samePlacements(expected, savePlacements())
        assertEquals(proof.getString("expectedProfile"), AvatarProfileStore.encode(AvatarProfileStore.current.value!!))
        record("process_restart_restored_profile_and_exact_manual_placements",
            JSONObject().put("firstPid", proof.getInt("firstPid")).put("secondPid", Process.myPid()))
        val updated = JSONObject(evidence.readText()).put("secondPhaseComplete", true)
        evidence.writeText(updated.toString(2))
    }
}
