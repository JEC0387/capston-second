package com.jeiu.fashionproject

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.DeviceGarmentInput
import com.jeiu.fashionproject.data.DeviceGarmentRole
import com.jeiu.fashionproject.data.DeviceImage
import com.jeiu.fashionproject.data.DeviceModelSpec
import com.jeiu.fashionproject.data.DeviceVtonFailure
import com.jeiu.fashionproject.data.DeviceVtonSelection
import com.jeiu.fashionproject.data.OnDeviceVtonImages
import com.jeiu.fashionproject.data.deviceSha256
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.CRC32

/** Synthetic local byte/EXIF/save contracts only. No real model, sensor, SAF provider, or device QA. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class P39OnDeviceImagesTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val ownedFiles = mutableListOf<File>()
    private val roles = DeviceGarmentRole.entries.toSet()
    private val colors = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
    private val model get() = DeviceModelSpec("synthetic-contract", "r1", "a".repeat(64), roles, 1024)

    @After fun removeOnlySyntheticFixtures() {
        ownedFiles.forEach { it.delete() }
    }

    private fun file(suffix: String = ".png"): File =
        File.createTempFile("p39-synthetic-", suffix, app.cacheDir).also { ownedFiles += it }

    private fun png(color: Int = Color.RED, width: Int = 100, height: Int = 80): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(color)
            ByteArrayOutputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally { bitmap.recycle() }
    }

    private fun pngFile(color: Int = Color.RED, width: Int = 100, height: Int = 80) =
        file().apply { writeBytes(png(color, width, height)) }

    private fun source(file: File) = Uri.fromFile(file).toString()
    private fun selection(person: String = source(pngFile())) = DeviceVtonSelection(person, 4,
        DeviceGarmentRole.entries.mapIndexed { index, role ->
            DeviceGarmentInput("synthetic-$role", role, source(pngFile(colors[index])), "placement-$index")
        })

    private fun prepare(selection: DeviceVtonSelection, spec: DeviceModelSpec = model, jobId: String = "job-1") =
        runBlocking { OnDeviceVtonImages.prepare(app, jobId, selection, spec) }

    private fun rejected(action: () -> Unit) {
        try { action(); fail("Invalid synthetic input was accepted") }
        catch (_: DeviceVtonFailure) { /* Explicit safe rejection; not a model success. */ }
    }

    private fun jpeg(orientation: Int, width: Int = 400, height: Int = 240): File {
        val file = file(".jpg")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            for (index in 0..3) {
                val left = index % 2 * width / 2f
                val top = index / 2 * height / 2f
                canvas.drawRect(left, top, left + width / 2f, top + height / 2f,
                    Paint().apply { color = colors[index] })
            }
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
            ExifInterface(file.path).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                setAttribute(ExifInterface.TAG_USER_COMMENT, "synthetic metadata is not retained")
                saveAttributes()
            }
        } finally { bitmap.recycle() }
        return file
    }

    private fun assertCorners(image: DeviceImage, order: IntArray) {
        val bytes = image.bytes
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        try {
            for (index in 0..3) {
                val x = ((if (index % 2 == 0) .15f else .85f) * bitmap.width).toInt()
                val y = ((if (index / 2 == 0) .15f else .85f) * bitmap.height).toInt()
                val actual = bitmap.getPixel(x, y)
                for (shift in listOf(16, 8, 0)) {
                    assertTrue("EXIF corner $index channel $shift", kotlin.math.abs(
                        ((actual shr shift) and 255) - ((colors[order[index]] shr shift) and 255)) <= 12)
                }
            }
        } finally { bitmap.recycle() }
    }

    @Test fun allEightExifOrientationsAreFrozenAsIndependentExpectedPixels() {
        // These corner orders are the EXIF specification's display transformations, not production math.
        val expected = mapOf(1 to intArrayOf(0, 1, 2, 3), 2 to intArrayOf(1, 0, 3, 2),
            3 to intArrayOf(3, 2, 1, 0), 4 to intArrayOf(2, 3, 0, 1), 5 to intArrayOf(0, 2, 1, 3),
            6 to intArrayOf(2, 0, 3, 1), 7 to intArrayOf(3, 1, 2, 0), 8 to intArrayOf(1, 3, 0, 2))
        for (orientation in 1..8) {
            val job = prepare(selection(source(jpeg(orientation))))
            assertEquals(if (orientation >= 5) 240 else 400, job.person.width)
            assertEquals(if (orientation >= 5) 400 else 240, job.person.height)
            assertCorners(job.person, expected.getValue(orientation))
            job.person.bytes.inputStream().use {
                assertNull(ExifInterface(it).getAttribute(ExifInterface.TAG_USER_COMMENT))
            }
        }
    }

    @Test fun largePhotoIsDownsampledBeforeDecodeAndKeepsDisplayAspect() {
        val job = prepare(selection(source(jpeg(6, 3000, 1500))))
        assertEquals(375, job.person.width)
        assertEquals(750, job.person.height)
        assertCorners(job.person, intArrayOf(2, 0, 3, 1))
        assertTrue(job.person.bytes.size <= OnDeviceVtonImages.MAX_ENCODED_BYTES)
        assertEquals(roles, job.garments.keys)
    }

    @Test fun networkUnknownRemoteFileAndUnselectedBarePathsAreRejectedBeforeReading() {
        val base = selection()
        for (bad in listOf("https://invalid.example/person.png", "http://127.0.0.1/p.png",
            "ftp://invalid.example/p.png", "file://remote-host/photo.png", "data:image/png;base64,AAA=",
            "/sdcard/not-selected.png", "content:///missing-authority", "")) {
            rejected { prepare(base.copy(person = bad)) }
            rejected { prepare(base.copy(garments = base.garments.map {
                if (it.role == DeviceGarmentRole.SHOES) it.copy(source = bad) else it
            })) }
        }
    }

    @Test fun invalidOrDuplicateRolesIdsAndUnsupportedModelsAreRejected() {
        val base = selection()
        rejected { prepare(base.copy(garments = base.garments.take(2))) }
        rejected { prepare(base.copy(garments = base.garments.map { it.copy(role = DeviceGarmentRole.TOP) })) }
        rejected { prepare(base.copy(garments = base.garments.map { it.copy(id = "same") })) }
        rejected { prepare(base.copy(revision = -1)) }
        rejected { prepare(base, DeviceModelSpec("m", "r", "bad-checksum", roles)) }
        rejected { prepare(base, DeviceModelSpec("m", "r", "a".repeat(64), setOf(DeviceGarmentRole.TOP))) }
        rejected { prepare(base, DeviceModelSpec("m", "r", "a".repeat(64), roles, 0)) }
        rejected { prepare(base, DeviceModelSpec("m", "r", "a".repeat(64), roles, 2049)) }
    }

    @Test fun emptyCorruptTruncatedAndUnsupportedImagesCannotReachAnEngine() {
        val base = selection()
        val valid = png()
        for (bytes in listOf(byteArrayOf(), "not an image".toByteArray(),
            "GIF89a-not-supported".toByteArray(), valid.copyOf(valid.size - 5),
            valid.copyOf().apply { this[45] = (this[45].toInt() xor 1).toByte() },
            png(width = 1, height = 80))) {
            val invalid = file().apply { writeBytes(bytes) }
            rejected { prepare(base.copy(person = source(invalid))) }
        }
        val truncatedJpeg = file(".jpg").apply { writeBytes(jpeg(1).readBytes().dropLast(2).toByteArray()) }
        rejected { prepare(base.copy(person = source(truncatedJpeg))) }
    }

    @Test fun encodedByteLimitAndDeclaredPixelBombAreRejected() {
        val oversized = file().apply {
            outputStream().use { output ->
                val block = ByteArray(16 * 1024)
                repeat(OnDeviceVtonImages.MAX_ENCODED_BYTES / block.size) { output.write(block) }
                output.write(0)
            }
        }
        rejected { prepare(selection(source(oversized))) }
        // Alter only synthetic IHDR + its CRC. Bounds must reject 25M pixels before decoding pixels.
        val bomb = png().apply {
            for (offset in listOf(16, 20)) {
                this[offset] = 0; this[offset + 1] = 0
                this[offset + 2] = 0x13; this[offset + 3] = 0x88.toByte() // 5000
            }
            val imageBytes = this
            val crc = CRC32().apply { update(imageBytes, 12, 17) }.value
            for (index in 0..3) this[29 + index] = (crc shr (24 - 8 * index)).toByte()
        }
        rejected { prepare(selection(source(file().apply { writeBytes(bomb) }))) }
    }

    @Test fun preparedBytesCannotChangeWhenSourceOrReturnedArrayChanges() {
        val path = pngFile(Color.RED)
        val job = prepare(selection(source(path)))
        val before = job.person.sha256
        path.writeBytes(png(Color.BLUE))
        val exposedCopy = job.person.bytes
        exposedCopy.fill(0)
        assertEquals(before, deviceSha256(job.person.bytes))
        assertNotEquals(deviceSha256(path.readBytes()), before)
        val constructorInput = png()
        val image = DeviceImage(constructorInput, 100, 80)
        constructorInput.fill(0)
        assertEquals(image.sha256, deviceSha256(image.bytes))
    }

    @Test fun explicitlySelectedContentStreamIsClosedOnSuccessAndCancellation() {
        val bytes = png()
        val selected = Uri.parse("content://p39-synthetic-input/selected.png")
        var closed = 0
        shadowOf(app.contentResolver).registerInputStreamSupplier(selected) {
            object : ByteArrayInputStream(bytes) {
                override fun close() { closed++; super.close() }
            }
        }
        val prepared = prepare(selection(selected.toString()))
        assertEquals(100, prepared.person.width)
        assertEquals(1, closed)
        shadowOf(app.contentResolver).registerInputStreamSupplier(selected) {
            object : ByteArrayInputStream(bytes) {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                    throw CancellationException("synthetic cancellation")
                override fun close() { closed++; super.close() }
            }
        }
        try { prepare(selection(selected.toString())); fail("Cancellation became success") }
        catch (_: CancellationException) { /* Cancellation must remain distinct from invalid input. */ }
        assertEquals(2, closed)
    }

    @Test fun eachGarmentPixelChangeChangesReceiptWhileListOrderDoesNot() {
        val base = selection()
        val first = prepare(base)
        assertEquals(first.inputDigest, prepare(base.copy(garments = base.garments.reversed())).inputDigest)
        for (role in DeviceGarmentRole.entries) {
            val changed = base.copy(garments = base.garments.map {
                if (it.role == role) it.copy(source = source(pngFile(Color.MAGENTA))) else it
            })
            assertNotEquals(first.inputDigest, prepare(changed).inputDigest)
        }
        // Same URI and IDs, replaced image bytes: immutable preparation must detect the new content.
        val top = base.garments.single { it.role == DeviceGarmentRole.TOP }
        File(Uri.parse(top.source).path!!).writeBytes(png(Color.CYAN))
        assertNotEquals(first.inputDigest, prepare(base).inputDigest)
    }

    @Test fun receiptBindsPersonRevisionPlacementAndExactModelWithoutAmbiguousConcatenation() {
        val base = selection()
        val first = prepare(base)
        assertNotEquals(first.inputDigest, prepare(base.copy(revision = 5)).inputDigest)
        assertNotEquals(first.inputDigest, prepare(base.copy(person = source(pngFile(Color.BLACK)))).inputDigest)
        assertNotEquals(first.inputDigest, prepare(base.copy(garments = base.garments.map {
            if (it.role == DeviceGarmentRole.BOTTOM) it.copy(placementKey = "moved") else it
        })).inputDigest)
        assertNotEquals(first.inputDigest, prepare(base,
            DeviceModelSpec("synthetic-contract", "r2", "b".repeat(64), roles)).inputDigest)
        val ab = DeviceModelSpec("ab", "c", "a".repeat(64), roles)
        val bc = DeviceModelSpec("a", "bc", "a".repeat(64), roles)
        assertNotEquals(prepare(base, ab).inputDigest, prepare(base, bc).inputDigest)
        assertNotEquals(first.inputDigest, prepare(base, jobId = "job-2").inputDigest)
    }

    @Test fun exactPngSaveAndReopenPreserveHashAndPixelsWithoutReencoding() = runBlocking {
        val input = png(Color.MAGENTA, 200, 120)
        val destination = file()
        OnDeviceVtonImages.saveExact(app, input, Uri.fromFile(destination))
        val reopened = destination.readBytes()
        assertArrayEquals(input, reopened)
        assertEquals(deviceSha256(input), deviceSha256(reopened))
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(reopened, 0, reopened.size))
        try {
            assertEquals(200, bitmap.width)
            assertEquals(120, bitmap.height)
            assertEquals(Color.MAGENTA, bitmap.getPixel(100, 60))
        } finally { bitmap.recycle() }
    }

    @Test fun invalidPngOrUnsafeDestinationDoesNotOverwriteExistingFile() {
        val destination = file().apply { writeBytes(byteArrayOf(1, 2, 3)) }
        rejected { runBlocking { OnDeviceVtonImages.saveExact(app, byteArrayOf(1, 2, 3), Uri.fromFile(destination)) } }
        assertArrayEquals(byteArrayOf(1, 2, 3), destination.readBytes())
        val corrupt = png().apply { this[45] = (this[45].toInt() xor 1).toByte() }
        rejected { runBlocking { OnDeviceVtonImages.saveExact(app, corrupt, Uri.fromFile(destination)) } }
        assertArrayEquals(byteArrayOf(1, 2, 3), destination.readBytes())
        for (target in listOf(Uri.parse("https://invalid.example/save.png"),
            Uri.parse("file://remote-host/save.png"), Uri.parse("content:///missing"),
            Uri.fromFile(File(app.filesDir.parentFile, "outside-private-directories.png")))) {
            rejected { runBlocking { OnDeviceVtonImages.saveExact(app, png(), target) } }
        }
    }
}
