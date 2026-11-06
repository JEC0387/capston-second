package com.jeiu.fashionproject

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.ManualBodyMeasurements
import com.jeiu.fashionproject.data.ManualBodyStore
import com.jeiu.fashionproject.ui.components.ManualBodyEntryCard
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w500dp-h900dp")
class P34ManualBodyPersistenceUiTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val previous = ManualBodyMeasurements(170f, 40f, 90f, 80f, 95f, 75f, 50f, 1L)
    private lateinit var faulty: P34FaultingMeasurementContext
    private var applied = 0

    @Before fun showConfirmedMeasurement() {
        ManualBodyStore.clear(app)
        assertTrue(ManualBodyStore.update(app, previous, true))
        faulty = P34FaultingMeasurementContext(app)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides faulty) {
                MaterialTheme {
                    Box(Modifier.height(600.dp).verticalScroll(rememberScrollState())) {
                        ManualBodyEntryCard(null) { applied++ }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @After fun cleanSyntheticPreferences() {
        compose.runOnIdle { ManualBodyStore.clear(app) }
    }

    @Test fun failedDeleteDoesNotReportSuccessOrRemoveConfirmedCard() {
        compose.runOnIdle { faulty.failure = P34WriteFailure.MUTATE_THEN_FALSE }
        compose.onNodeWithTag("manualBodyClear").performScrollTo().performClick()
        compose.onNodeWithText("직접 입력한 치수를 삭제했어요.").assertDoesNotExist()
        compose.onNodeWithText("기기에 저장된 입력값").assertExists()
        compose.onNodeWithText("직접 입력값을 삭제하지 못했어요. 다시 시도해주세요.").assertExists()
        compose.runOnIdle { assertEquals(previous, ManualBodyStore.current.value) }
    }

    @Test fun failedSaveKeepsDraftAndPreviousValueWithoutApplyingHeight() {
        compose.onNodeWithTag("manualBodyEdit").performScrollTo().performClick()
        compose.onNodeWithTag("manualBodyField3").performScrollTo().performTextReplacement("85")
        compose.runOnIdle { faulty.failure = P34WriteFailure.THROW }
        compose.onNodeWithTag("manualBodyApply").performScrollTo().performClick()
        compose.onNodeWithText("기기에 입력값을 저장하지 못했어요. 다시 시도해주세요.").assertExists()
        compose.onNodeWithTag("manualBodyField3").assertTextContains("85")
        compose.runOnIdle {
            assertEquals(previous, ManualBodyStore.current.value)
            assertEquals(0, applied)
            ManualBodyStore.load(faulty)
            assertEquals(previous, ManualBodyStore.current.value)
        }
    }
}
