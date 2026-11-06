package com.jeiu.fashionproject

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.TryOnRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27], application = Application::class)
class TryOnImagePreparationTest {
    @Test fun exifRotationPreservesShapeAndRemovesLocationMetadata() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val input = File.createTempFile("mock-orientation", ".jpg", app.cacheDir)
        val bitmap = Bitmap.createBitmap(300,600,Bitmap.Config.ARGB_8888)
        try {
            input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,95,it) }
            ExifInterface(input.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                setLatLong(37.0,127.0); saveAttributes()
            }
            val encoded = TryOnRepository.uriToDataUri(app, Uri.fromFile(input)).substringAfter(',')
            val bytes = Base64.decode(encoded,Base64.NO_WRAP)
            val decoded = BitmapFactory.decodeByteArray(bytes,0,bytes.size)
            assertEquals(600,decoded.width); assertEquals(300,decoded.height)
            decoded.recycle()
            assertNull(ExifInterface(bytes.inputStream()).getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        } finally { bitmap.recycle(); input.delete() }
    }
}
