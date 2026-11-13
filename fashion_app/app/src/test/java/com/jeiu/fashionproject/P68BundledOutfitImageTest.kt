package com.jeiu.fashionproject

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.ui.screens.ServerOutfitVtonScreen
import com.jeiu.fashionproject.ui.screens.WornGarment
import com.jeiu.fashionproject.ui.screens.onDeviceFittingSelection
import com.jeiu.fashionproject.ui.screens.userFacingTryOnError
import com.jeiu.fashionproject.ui.viewmodel.ServerOutfitVtonViewModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class P68BundledOutfitImageTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val files = mutableListOf<File>()
    private val config = OutfitServerConfig(true, "", "synthetic", "rev", "a".repeat(64), "policy", 300,
        5 * 1024 * 1024, 29 * 1024 * 1024, maxInputSide = 2048)

    @After fun removeFixtures() { files.forEach { it.delete() } }

    private fun fixture(bytes: ByteArray): String = Uri.fromFile(
        File.createTempFile("p68-image-", ".fixture", app.cacheDir).also { files += it; it.writeBytes(bytes) }
    ).toString()

    private fun image(format: Bitmap.CompressFormat, color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 96, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(color)
            ByteArrayOutputStream().use {
                assertTrue(bitmap.compress(format, 100, it))
                it.toByteArray()
            }
        } finally { bitmap.recycle() }
    }

    private fun selection(ids: List<String>, person: String = fixture(image(Bitmap.CompressFormat.PNG, Color.RED))): DeviceVtonSelection {
        val items = SampleWardrobe.items().associateBy { it.id }
        val worn = ids.mapIndexed { i, id ->
            val item = items.getValue(id)
            WornGarment(item, item.imageUri, 1f, Offset(180f, 130f + 150f * i), 125f)
        }
        return checkNotNull(onDeviceFittingSelection(person, 1, worn, Size(400f, 600f)))
    }

    private fun read(uri: String) = app.contentResolver.openInputStream(Uri.parse(uri))!!.use { it.readBytes() }

    private fun prepare(selection: DeviceVtonSelection): DevicePreparedJob = runBlocking {
        OnDeviceVtonImages.prepare(app, UUID.randomUUID().toString(), selection, config.imageSpec())
    }

    private fun verifyRequest(selection: DeviceVtonSelection, expectedIds: List<String>) {
        val inputs = selection.garments.map { read(it.source) }
        val job = prepare(selection)
        val json = JSONObject(ServerOutfitVtonRepository.requestBody(job, config).decodeToString())
        val garments = json.getJSONArray("garments")
        assertEquals(3, garments.length())
        assertTrue(json.getJSONObject("consent").getBoolean("accepted"))
        assertEquals("policy", json.getJSONObject("consent").getString("policy_version"))
        for ((index, role) in DeviceGarmentRole.entries.withIndex()) {
            val part = garments.getJSONObject(index)
            assertEquals(expectedIds[index], part.getString("id"))
            assertEquals(listOf("tops", "bottoms", "shoes")[index], part.getString("category"))
            val url = part.getString("image")
            assertTrue(url.startsWith("data:image/png;base64,"))
            val png = android.util.Base64.decode(url.substringAfter(','), android.util.Base64.DEFAULT)
            OnDeviceVtonImages.validateResultPng(png)
            assertArrayEquals(job.garments.getValue(role).bytes, png)
            val bitmap = checkNotNull(BitmapFactory.decodeByteArray(png, 0, png.size))
            val original = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(inputs[index], 0, inputs[index].size, original)
            try {
                assertEquals(original.outWidth.toDouble() / original.outHeight, bitmap.width.toDouble() / bitmap.height, .015)
            } finally { bitmap.recycle() }
            assertArrayEquals(inputs[index], read(selection.garments[index].source))
        }
    }

    @Test fun bundledWebpOriginalsAndLoaferReachRequestBody() {
        val ids = listOf("sample_10", "sample_06", "sample_12")
        val selected = selection(ids)
        assertEquals(listOf("shop_denim_shirt", "shop_officer_pants", "sample_12"),
            selected.garments.map { it.source.substringAfterLast('/') })
        assertTrue(selected.garments.take(2).all { String(read(it.source), 8, 4, Charsets.US_ASCII) == "WEBP" })
        verifyRequest(selected, ids)
    }

    @Test fun bundledCheckedShirtJeansAndSneakersStillReachRequestBody() {
        val ids = listOf("sample_04", "sample_05", "sample_11")
        val selected = selection(ids, fixture(image(Bitmap.CompressFormat.JPEG, Color.RED)))
        assertTrue(selected.garments.all { read(it.source).take(2) == listOf(0xff.toByte(), 0xd8.toByte()) })
        verifyRequest(selected, ids)
    }

    @Test fun staticLosslessWebpKeepsTransparentPixelsInCanonicalPng() {
        val pixel = Color.argb(128, 0, 128, 255)
        val webp = image(Bitmap.CompressFormat.WEBP_LOSSLESS, pixel)
        val selected = selection(listOf("sample_04", "sample_05", "sample_11"), fixture(webp))
        val canonical = prepare(selected).person.bytes
        val output = checkNotNull(BitmapFactory.decodeByteArray(canonical, 0, canonical.size))
        try { assertEquals(pixel, output.getPixel(12, 12)) } finally { output.recycle() }
    }

    @Test fun unreadableDamagedOversizeAndUnsupportedInputsGetDistinctLocalCodes() {
        val ids = listOf("sample_04", "sample_05", "sample_11")
        val webp = image(Bitmap.CompressFormat.WEBP_LOSSLESS, Color.RED)
        // VP8X advertises animation. A first-frame-capable decoder must not accept it.
        val animated = ByteBuffer.allocate(webp.size + 18).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(webp.size + 10).put("WEBPVP8X".toByteArray())
            .putInt(10).put(2.toByte()).put(ByteArray(9)).put(webp, 12, webp.size - 12).array()
        val cases = listOf(
            Uri.fromFile(File(app.cacheDir, "missing-p68-fixture")).toString() to "image_read_failed",
            fixture(webp.copyOf(webp.size - 1)) to "image_processing_failed",
            fixture(ByteArray(OnDeviceVtonImages.MAX_ENCODED_BYTES + 1)) to "image_size",
            fixture("GIF89a".toByteArray()) to "image_unsupported",
            fixture(animated) to "image_unsupported"
        )
        for ((uri, code) in cases) {
            val failure = assertThrows(DeviceVtonPreparationFailure::class.java) { prepare(selection(ids, uri)) }
            assertEquals(code, failure.safeCode)
        }
        assertEquals(4, cases.map { userFacingTryOnError(it.second, false) }.toSet().size)
    }

    @Test fun actualPreparationFailureKeepsPreviewsAndNeverSubmitsOrPersistsTicket() {
        val ids = listOf("sample_10", "sample_06", "sample_12")
        val original = selection(ids)
        val selected = original.copy(garments = original.garments.map {
            if (it.role == DeviceGarmentRole.TOP) {
                val webp = read(it.source)
                it.copy(source = fixture(webp.copyOf(webp.size - 1)))
            } else it
        })
        val session = OutfitServerSession("https://synthetic.example", "synthetic")
        var posts = 0
        var writes = 0
        val store = object : OutfitTicketStore {
            override fun read(): OutfitServerTicket? = null
            override fun write(ticket: OutfitServerTicket) { writes++ }
        }
        val gateway = object : OutfitServerGateway {
            override fun session() = session
            override suspend fun config(session: OutfitServerSession) = config
            override suspend fun submit(session: OutfitServerSession, ticket: OutfitServerTicket, body: ByteArray): OutfitServerStatus {
                posts++
                error("Local image failure unexpectedly submitted a request")
            }
            override suspend fun status(session: OutfitServerSession, ticket: OutfitServerTicket) = error("Unexpected status")
            override suspend fun cancel(session: OutfitServerSession, ticket: OutfitServerTicket) = error("Unexpected cancel")
            override suspend fun result(session: OutfitServerSession, ticket: OutfitServerTicket, status: OutfitServerStatus) = error("Unexpected result")
        }
        val model = ServerOutfitVtonViewModel(gateway, { store })
        try {
            compose.setContent { MaterialTheme { ServerOutfitVtonScreen(selected, {}, model) } }
            compose.onNodeWithTag("serverOutfitCheck").performScrollTo().performClick()
            compose.waitUntil(15000) { shadowOf(Looper.getMainLooper()).idle(); model.state.config?.ready == true }
            compose.onNodeWithTag("serverOutfitGenerate").performScrollTo().performClick()
            compose.onNodeWithTag("serverOutfitConsent").performClick()
            compose.onNodeWithTag("serverOutfitConsentConfirm").performClick()
            compose.waitUntil(15000) { shadowOf(Looper.getMainLooper()).idle(); model.state.failureCode != null && !model.state.busy }
            assertEquals("image_preparation", model.state.failureStage)
            assertEquals("image_processing_failed", model.state.failureCode)
            assertEquals(0, posts)
            assertEquals(0, writes)
            assertNull(model.state.ticket)
            compose.onNodeWithTag("serverOutfitError").performScrollTo()
                .assertTextEquals(checkNotNull(userFacingTryOnError("image_processing_failed", false)))
            compose.onNodeWithTag("serverOutfitPersonPreview").assertExists()
            DeviceGarmentRole.entries.forEach { compose.onNodeWithTag("serverOutfitGarment_${it.name}").assertExists() }
        } finally { model.viewModelScope.cancel() }
    }
}
