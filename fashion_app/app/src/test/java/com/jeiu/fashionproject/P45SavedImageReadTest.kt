package com.jeiu.fashionproject

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.LocalVtonRepository
import com.jeiu.fashionproject.data.TryOnFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** Defensive synthetic-provider checks; zero reads violate the InputStream contract.
 * No real provider/device defect, model inference, or photo-quality claim is made.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class P45SavedImageReadTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    private abstract class ObservedStream : InputStream() {
        var reads = 0
        var closes = 0
        final override fun read(): Int = throw AssertionError("Expected bounded bulk reads")
        final override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            reads++
            return readChunk(buffer, offset, length)
        }
        abstract fun readChunk(buffer: ByteArray, offset: Int, length: Int): Int
        override fun close() { closes++ }
    }

    private fun reopen(stream: InputStream): ByteArray {
        val uri = Uri.parse("content://p45-synthetic-provider/selected.png")
        shadowOf(app.contentResolver).registerInputStreamSupplier(uri) { stream }
        return runBlocking { LocalVtonRepository.openSavedImage(app, uri) }
    }

    private fun png(): ByteArray {
        val bitmap = Bitmap.createBitmap(32, 64, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(Color.MAGENTA)
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally { bitmap.recycle() }
    }

    @Test fun repeatedZeroReadIsRejectedBeforeFiniteSentinelAndStreamIsClosed() {
        val stream = object : ObservedStream() {
            override fun readChunk(buffer: ByteArray, offset: Int, length: Int): Int {
                // The sentinel keeps the pre-fix regression finite; it must never be reached.
                if (reads > 3) throw IOException("synthetic zero-read sentinel")
                return 0
            }
        }
        val failure = runCatching { reopen(stream) }.exceptionOrNull()
        assertTrue("A stalled read must fail explicitly, not reach a fourth read", failure is TryOnFailure)
        assertEquals(1, stream.reads)
        assertEquals(1, stream.closes)
    }

    @Test fun zeroReadBeforeValidPngCannotBeSilentlyAcceptedAsSuccessfulReopen() {
        val payload = ByteArrayInputStream(png())
        val stream = object : ObservedStream() {
            override fun readChunk(buffer: ByteArray, offset: Int, length: Int): Int =
                if (reads == 1) 0 else payload.read(buffer, offset, length)
        }
        val failure = runCatching { reopen(stream) }.exceptionOrNull()
        assertTrue("Zero progress is not a successful image read", failure is TryOnFailure)
        assertEquals(1, stream.reads)
        assertEquals(1, stream.closes)
    }

    @Test fun positivePartialReadsReachEofAndKeepTheExactPngBytes() {
        val expected = png()
        val payload = ByteArrayInputStream(expected)
        val stream = object : ObservedStream() {
            override fun readChunk(buffer: ByteArray, offset: Int, length: Int): Int =
                payload.read(buffer, offset, minOf(length, 7))
        }
        assertArrayEquals(expected, reopen(stream))
        assertTrue(stream.reads > 2)
        assertEquals(1, stream.closes)
    }

    @Test fun emptyEofIsRejectedAndStreamIsClosed() {
        val stream = object : ObservedStream() {
            override fun readChunk(buffer: ByteArray, offset: Int, length: Int) = -1
        }
        assertTrue(runCatching { reopen(stream) }.exceptionOrNull() is TryOnFailure)
        assertEquals(1, stream.reads)
        assertEquals(1, stream.closes)
    }

    @Test fun existingEightMiBLimitStillRejectsOversizedStream() {
        val stream = object : ObservedStream() {
            private var remaining = 8 * 1024 * 1024 + 1
            override fun readChunk(buffer: ByteArray, offset: Int, length: Int): Int {
                if (remaining == 0) return -1
                val count = minOf(length, remaining)
                buffer.fill(0, offset, offset + count)
                remaining -= count
                return count
            }
        }
        val failure = runCatching { reopen(stream) }.exceptionOrNull()
        assertTrue(failure is TryOnFailure)
        assertTrue(failure?.message.orEmpty().contains("너무 커요"))
        assertEquals(1, stream.closes)
    }

    @Test fun providerCancellationIsNotConvertedToSuccessOrDecodeFailure() {
        val stream = object : ObservedStream() {
            override fun readChunk(buffer: ByteArray, offset: Int, length: Int): Int =
                throw CancellationException("synthetic provider cancellation")
        }
        assertTrue(runCatching { reopen(stream) }.exceptionOrNull() is CancellationException)
        assertEquals(1, stream.reads)
        assertEquals(1, stream.closes)
    }
}
