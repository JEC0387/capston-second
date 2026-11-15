package com.jeiu.fashionproject

import android.Manifest
import android.app.Application
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.navigation.AppNavigation
import com.jeiu.fashionproject.ui.theme.FashionProjectTheme
import com.jeiu.fashionproject.ui.viewmodel.WeatherUiState
import com.jeiu.fashionproject.ui.viewmodel.WeatherViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Presentation screenshots of the real app (opt-in: -e presentationShots true).
 * Real AppNavigation, real screens, bundled sample wardrobe; only the current weather is fixed
 * so the home/outfit screens do not depend on the emulator's location or the live server.
 */
@RunWith(AndroidJUnit4::class)
class PresentationScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val outDir get() = File(app.filesDir, "presentation-shots").apply { mkdirs() }

    @Before fun seed() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("presentationShots") == "true")
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
            "android.permission.POST_NOTIFICATIONS").forEach {
            runCatching { inst.uiAutomation.grantRuntimePermission(app.packageName, it) }
        }
        inst.runOnMainSync {
            WardrobeStore.addDemoSamplesIfEmpty(app)
            AvatarProfileStore.save(app, AvatarProfile(172f, 66f))
            ManualBodyStore.update(app, ManualBodyMeasurements(172f, 44f, 94f, 78f, 95f, 80f, 52f,
                System.currentTimeMillis()), saveOnDevice = true)
        }
        // Same activity-scoped instance that AppNavigation's viewModel() resolves.
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge()
            val vm = ViewModelProvider(compose.activity)[WeatherViewModel::class.java]
            @Suppress("UNCHECKED_CAST")
            (WeatherViewModel::class.java.getDeclaredField("_state").apply { isAccessible = true }
                .get(vm) as MutableStateFlow<WeatherUiState>).value =
                WeatherUiState.Success(WeatherInfo("맑음", 14, 48, 800))
            WeatherViewModel::class.java.getDeclaredField("loaded").apply { isAccessible = true }.setBoolean(vm, true)
        }
    }

    private fun settle(ms: Long = 4000) {
        compose.waitForIdle()
        SystemClock.sleep(ms)
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        val bitmap = inst.uiAutomation.takeScreenshot() ?: error("no screenshot")
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun waitTag(tag: String, ms: Long = 20_000) =
        compose.waitUntil(ms) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private fun waitText(text: String, ms: Long = 20_000) =
        compose.waitUntil(ms) { compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private fun stage(name: String, block: () -> Unit) {
        try { block() } catch (t: Throwable) {
            runCatching { shot("fail_$name") }
            runCatching { compose.onRoot(useUnmergedTree = true).printToLog("SHOTS_$name") }
            throw AssertionError("stage $name failed: ${t.message}", t)
        }
    }

    @Test fun captureHomeOutfitAndBody3d() {
        compose.setContent { FashionProjectTheme { AppNavigation() } }

        stage("onboarding") {
            waitTag("continueLocalGuest")
            compose.onNodeWithTag("continueLocalGuest").performScrollTo().performClick()
        }
        stage("home") {
            waitTag("homeWeatherSummary")
            settle(6000)
            shot("home")
        }
        stage("outfit") {
            compose.onNodeWithTag("homeWeatherAction", useUnmergedTree = true).performClick()
            waitText("상황별")
            settle(6000)
            shot("outfit")
        }
        stage("body3d") {
            compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
            waitTag("homeWeatherSummary")
            compose.onNodeWithText("AI분석").performClick()
            waitTag("bodyMethodAvatar")
            compose.onNodeWithTag("bodyMethodAvatar").performClick()
            waitTag("avatarShow")
            settle(1500)
            compose.onNodeWithTag("avatarShow").performScrollTo().performClick()
            waitText("3D 닫기")
            settle(6000)
            shot("body3d")
        }
    }
}
