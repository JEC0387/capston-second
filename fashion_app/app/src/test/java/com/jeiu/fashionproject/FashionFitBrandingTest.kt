package com.jeiu.fashionproject

import android.app.Application
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FashionFitBrandingTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Test fun manifestUsesDistinctLauncherNameAndPreservesPackage() {
        val expected = when {
            BuildConfig.INSTALL_TEST_BUILD -> "패션핏 설치검증"
            BuildConfig.DEVICE_QA_BUILD -> "패션핏 QA"
            BuildConfig.PREVIEW_BUILD -> "패션핏 Preview"
            else -> "패션핏"
        }
        assertEquals(expected, app.packageManager.getApplicationLabel(app.applicationInfo).toString())
        assertEquals(BuildConfig.APPLICATION_ID, app.packageName)
        assertEquals("FashionFit", app.getString(R.string.brand_name))
        assertEquals("패션핏", app.getString(R.string.app_name))
    }

    @Test fun normalAndRoundAdaptiveIconsInflateWithSeparateThemedSilhouette() {
        for (id in listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round)) {
            val icon = app.getDrawable(id)
            assertTrue(icon is AdaptiveIconDrawable)
            icon as AdaptiveIconDrawable
            assertNotNull(icon.background)
            assertNotNull(icon.foreground)
            assertNotNull(icon.monochrome)
            assertNotSame(icon.foreground, icon.monochrome)
        }
    }
}
