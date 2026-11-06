package com.jeiu.fashionproject

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.awt.color.ICC_Profile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

/** Actual Android decoder/normalizer -> server JSON, entirely synthetic local inputs and no network. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class P57ServerImageContractTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val repo = ServerOutfitVtonRepository
    private val id = "00000000-0000-4000-8000-000000000057"
    // Same dimensions as the approved trial assets; these are test markers, never photographs.
    private val dimensions = linkedMapOf("person" to (1024 to 1353), "tops" to (930 to 1200),
        "bottoms" to (850 to 1200), "shoes" to (1200 to 704))
    private val roles = listOf("tops" to DeviceGarmentRole.TOP, "bottoms" to DeviceGarmentRole.BOTTOM,
        "shoes" to DeviceGarmentRole.SHOES)
    private fun config(side: Int = 2048) = OutfitServerConfig(true, "", "synthetic-wire-model", "synthetic-p57",
        "a".repeat(64), "synthetic-policy", 300, 5 * 1024 * 1024, 29 * 1024 * 1024, maxInputSide = side)
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun marker(x: Int, y: Int, width: Int, height: Int): Int = when {
        x < width / 2 && y < height / 2 -> 0xffd02030.toInt()
        x >= width / 2 && y < height / 2 -> 0xff30b040.toInt()
        x < width / 2 -> 0xff2040d0.toInt()
        else -> 0xffe0b030.toInt()
    }
    private fun png(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val pixels = IntArray(width * height) { index -> marker(index % width, index / width, width, height) }
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            return ByteArrayOutputStream().use { out ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)); out.toByteArray()
            }
        } finally { bitmap.recycle() }
    }
    private fun chunk(type: String, data: ByteArray): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { out ->
            val tag = type.toByteArray(Charsets.US_ASCII)
            out.writeInt(data.size); out.write(tag); out.write(data)
            out.writeInt(CRC32().apply { update(tag); update(data) }.value.toInt())
        }
        bytes.toByteArray()
    }
    private fun appendMetadata(original: ByteArray, type: String, data: ByteArray): ByteArray =
        original.copyOfRange(0, 33) + chunk(type, data) + original.copyOfRange(33, original.size)
    private fun srgbProfile(original: ByteArray): ByteArray {
        val compressed = ByteArrayOutputStream().use { out ->
            DeflaterOutputStream(out).use { it.write(ICC_Profile.getInstance(java.awt.color.ColorSpace.CS_sRGB).data) }
            out.toByteArray()
        }
        // PNG permits only one color-space authority. Keep valid image chunks and replace metadata only.
        val clean = ByteArrayOutputStream().use { out ->
            out.write(original, 0, 8)
            var offset = 8
            while (offset < original.size) {
                val length = ByteBuffer.wrap(original, offset, 4).int
                val type = String(original, offset + 4, 4, Charsets.US_ASCII)
                if (type !in setOf("iCCP", "sRGB", "gAMA", "cHRM")) out.write(original, offset, length + 12)
                offset += length + 12
            }
            out.toByteArray()
        }
        return appendMetadata(clean, "iCCP", "synthetic-sRGB".toByteArray() + byteArrayOf(0, 0) + compressed)
    }
    private fun rotatedExif(original: ByteArray): ByteArray {
        val tiff = ByteBuffer.allocate(26).order(ByteOrder.LITTLE_ENDIAN)
            .put('I'.code.toByte()).put('I'.code.toByte()).putShort(42).putInt(8)
            .putShort(1).putShort(0x0112).putShort(3).putInt(1).putShort(6).putShort(0).putInt(0).array()
        return appendMetadata(original, "eXIf", tiff)
    }
    private fun selection(directory: File): DeviceVtonSelection = DeviceVtonSelection(
        Uri.fromFile(File(directory, "person-original.png")).toString(), 57,
        roles.map { (name, role) -> DeviceGarmentInput("synthetic-$name", role,
            Uri.fromFile(File(directory, "$name-original.png")).toString(), "400,600,200,300,80,1,0,1") })
    private fun inputs(): File = File(app.cacheDir, "p57-synthetic-${UUID.randomUUID()}").apply {
        check(mkdirs())
        dimensions.forEach { (name, size) ->
            val bytes = png(size.first, size.second)
            File(this, "$name-original.png").writeBytes(if (name == "bottoms") srgbProfile(bytes) else bytes)
        }
    }
    private fun imageMap(job: DevicePreparedJob): Map<String, DeviceImage> =
        linkedMapOf("person" to job.person).apply { roles.forEach { (name, role) -> put(name, job.garments.getValue(role)) } }
    private fun rgba(bytes: ByteArray): ByteArray {
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return ByteArray(pixels.size * 4).also { out -> pixels.forEachIndexed { i, c ->
                out[4 * i] = (c ushr 16).toByte(); out[4 * i + 1] = (c ushr 8).toByte()
                out[4 * i + 2] = c.toByte(); out[4 * i + 3] = (c ushr 24).toByte()
            } }
        } finally { bitmap.recycle() }
    }
    private fun outputDirectory(): File {
        val base = generateSequence(File(System.getProperty("user.dir")).canonicalFile) { it.parentFile }
            .first { File(it, "app/src/main").isDirectory }
        return File(base, "build/p57-private/android-wire/run-${UUID.randomUUID()}").apply { check(mkdirs()) }
    }

    @Test fun oldOnDeviceDefaultReproducesWrongServerReferenceDimensions() = runBlocking {
        val source = inputs()
        val cfg = config()
        val oldSpec = DeviceModelSpec(cfg.modelId, cfg.revision, cfg.modelSha, DeviceGarmentRole.entries.toSet())
        assertEquals(1024, oldSpec.maxSide) // On-device default remains untouched.
        val old = OnDeviceVtonImages.prepare(app, id, selection(source), oldSpec)
        val expected = mapOf("person" to (512 to 676), "tops" to (465 to 600),
            "bottoms" to (425 to 600), "shoes" to (600 to 352))
        imageMap(old).forEach { (name, image) ->
            assertEquals("$name old width", expected.getValue(name).first, image.width)
            assertEquals("$name old height", expected.getValue(name).second, image.height)
            assertNotEquals(sha(rgba(File(source, "$name-original.png").readBytes())), sha(rgba(image.bytes)))
        }
    }

    @Test fun serverPreparationKeepsAllOriginalPixelsAndBindsExactWireBytes() = runBlocking {
        val source = inputs()
        val cfg = config()
        val job = OnDeviceVtonImages.prepare(app, id, selection(source), cfg.imageSpec())
        val body = repo.requestBody(job, cfg)
        val json = JSONObject(body.toString(Charsets.UTF_8))
        val out = outputDirectory()
        val manifest = JSONObject().put("synthetic_only", true).put("request_id", id)
            .put("model_revision", cfg.revision).put("model_sha256", cfg.modelSha)
            .put("max_input_side", cfg.maxInputSide).put("receipt_sha256", job.inputDigest)
            .put("body_sha256", sha(body))
        val records = JSONObject()
        imageMap(job).forEach { (name, image) ->
            val expected = dimensions.getValue(name)
            val original = File(source, "$name-original.png").readBytes()
            assertEquals(expected.first, image.width); assertEquals(expected.second, image.height)
            assertArrayEquals("$name visible RGBA pixels", rgba(original), rgba(image.bytes))
            val decoded = requireNotNull(BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size))
            try {
                listOf(10 to 10, (image.width - 10) to 10, 10 to (image.height - 10),
                    (image.width - 10) to (image.height - 10)).forEach { (x, y) ->
                    assertEquals("$name independent color marker", marker(x, y, image.width, image.height), decoded.getPixel(x, y))
                }
            } finally { decoded.recycle() }
            assertEquals(sha(image.bytes), image.sha256)
            val encoded = if (name == "person") json.getString("person") else
                json.getJSONArray("garments").getJSONObject(roles.indexOfFirst { it.first == name }).getString("image")
            assertArrayEquals(image.bytes, Base64.decode(encoded.substringAfter(','), Base64.DEFAULT))
            File(out, "$name-original.png").writeBytes(original)
            File(out, "$name-wire.png").writeBytes(image.bytes)
            records.put(name, JSONObject().put("width", image.width).put("height", image.height)
                .put("original_sha256", sha(original)).put("wire_sha256", sha(image.bytes))
                .put("rgba_sha256", sha(rgba(image.bytes))).put("pixel_equal", true))
        }
        val bottom = records.getJSONObject("bottoms")
        assertNotEquals(bottom.getString("original_sha256"), bottom.getString("wire_sha256"))
        File(out, "payload.json").writeBytes(body)
        File(out, "receipt.json").writeText(manifest.put("images", records).toString(2))
        File(out, "receipt.sha256").writeText(sha(File(out, "receipt.json").readBytes()) + "  receipt.json\n")
        println("P57_SYNTHETIC_WIRE_EVIDENCE=" + out.absolutePath)
    }

    @Test fun exifRotationUsesDisplayedSizeAndIndependentClockwisePixelMapping() = runBlocking {
        val directory = File(app.cacheDir, "p57-rotation-${UUID.randomUUID()}").apply { check(mkdirs()) }
        val width = 1200; val height = 704
        val original = rotatedExif(png(width, height))
        dimensions.keys.forEach { File(directory, "$it-original.png").writeBytes(original) }
        assertEquals(ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface(ByteArrayInputStream(original)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1))
        val prepared = OnDeviceVtonImages.prepare(app, id, selection(directory), config().imageSpec()).person
        assertEquals(704, prepared.width); assertEquals(1200, prepared.height)
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(prepared.bytes, 0, prepared.bytes.size))
        try {
            listOf(10 to 10, 690 to 10, 10 to 1190, 690 to 1190).forEach { (x, y) ->
                assertEquals(marker(y, height - 1 - x, width, height), bitmap.getPixel(x, y))
            }
        } finally { bitmap.recycle() }
        assertEquals(ExifInterface.ORIENTATION_NORMAL,
            ExifInterface(ByteArrayInputStream(prepared.bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1))
    }

    @Test fun serverConfigDeclaresBoundedPreparationSizeWithoutChangingOnDeviceDefault() {
        fun wire(side: Int) = JSONObject().put("engine", repo.ENGINE).put("ready", true)
            .put("model_id", "synthetic-wire-model").put("model_revision", "synthetic-p57")
            .put("model_sha256", "a".repeat(64)).put("policy_version", "synthetic-policy")
            .put("retention_seconds", 300).put("max_image_bytes", 5 * 1024 * 1024)
            .put("max_body_bytes", 29 * 1024 * 1024).put("max_input_side", side)
            .put("roles", JSONArray(listOf("tops", "bottoms", "shoes")))
        assertEquals(2048, repo.parseConfig(wire(2048)).imageSpec().maxSide)
        assertEquals(1536, repo.parseConfig(wire(1536)).imageSpec().maxSide)
        listOf(255, 2049).forEach { side ->
            assertThrows(OutfitServerFailure::class.java) { repo.parseConfig(wire(side)) }
        }
    }
}
