package com.jeiu.fashionproject

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.BodyAnalysisSessionStore
import com.jeiu.fashionproject.data.ManualBodyMeasurements
import com.jeiu.fashionproject.data.ManualBodyStore
import com.jeiu.fashionproject.ui.components.ManualBodyEntryCard
import com.jeiu.fashionproject.ui.viewmodel.BodyMeasurements
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Actual Compose Apply/close actions with synthetic local preferences. No device or server data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w500dp-h900dp")
class P32ManualBodyDraftUiTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val preferences get() = app.getSharedPreferences("manual_body_measurements", 0)
    private val previous = ManualBodyMeasurements(170f, 40f, 90f, 80f, 95f, 75f, 50f, 1700000000000L)
    private val photo = BodyMeasurements(170f, 40f, 30f, 40f, 75f, 50f, .1f, 1700f, 1700000000001L)
    private val heightApplications = mutableListOf<String>()
    private lateinit var before: Map<String, *>

    @Before fun showStoredMeasurements() {
        ManualBodyStore.clear(app)
        BodyAnalysisSessionStore.clear()
        assertTrue(ManualBodyStore.update(app, previous, true))
        BodyAnalysisSessionStore.update(photo)
        before = preferences.all.toMap()
        compose.setContent {
            MaterialTheme {
                Box(Modifier.height(600.dp).verticalScroll(rememberScrollState())) {
                    ManualBodyEntryCard(photo) { heightApplications.add(it) }
                }
            }
        }
        compose.onNodeWithTag("manualBodyEdit").performScrollTo().performClick()
    }

    @After fun cleanSyntheticFixture() {
        compose.runOnIdle {
            ManualBodyStore.clear(app)
            BodyAnalysisSessionStore.clear()
        }
    }

    private fun changeWaist(text: String) {
        compose.onNodeWithTag("manualBodyField3").performScrollTo().performTextReplacement(text)
    }

    private fun assertPreviousRecordsRemain() {
        compose.runOnIdle {
            assertEquals(previous, ManualBodyStore.current.value)
            assertTrue(ManualBodyStore.saved.value)
            assertEquals(before, preferences.all)
            ManualBodyStore.load(app)
            assertEquals(previous, ManualBodyStore.current.value)
            assertEquals(photo, BodyAnalysisSessionStore.measurements.value)
            assertTrue(heightApplications.isEmpty())
        }
    }

    @Test fun incompleteOptionalDecimalCannotEraseSavedMeasurementOnApply() {
        // '.' is an editable numeric draft, not an intentional empty optional field.
        changeWaist(".")
        compose.onNodeWithTag("manualBodyApply").performScrollTo().performClick()
        assertPreviousRecordsRemain()
        compose.onNodeWithTag("manualBodyField3").assertExists().assertTextContains(".")
        compose.onNodeWithText("입력 범위를 확인해주세요.", substring = true).assertExists()
    }

    @Test fun closingAnUnappliedDraftKeepsStoredNumbersAndPhotoEstimate() {
        changeWaist(".")
        compose.onNodeWithTag("manualBodyEdit").performScrollTo().performClick()
        compose.onNodeWithTag("manualBodyField3").assertDoesNotExist()
        assertPreviousRecordsRemain()
    }

    @Test fun explicitlyEmptyOptionalFieldRemainsAnAllowedUserEdit() {
        changeWaist("")
        compose.onNodeWithTag("manualBodyApply").performScrollTo().performClick()
        compose.runOnIdle {
            val applied = requireNotNull(ManualBodyStore.current.value)
            assertNull(applied.waistCircumferenceCm)
            assertEquals(95f, applied.hipCircumferenceCm!!, .001f)
            assertEquals(40f, applied.shoulderWidthCm!!, .001f)
            assertTrue(ManualBodyStore.saved.value)
            ManualBodyStore.load(app)
            assertEquals(applied, ManualBodyStore.current.value)
            assertEquals(photo, BodyAnalysisSessionStore.measurements.value)
            assertEquals(1, heightApplications.size)
        }
        compose.onNodeWithTag("manualBodyField3").assertDoesNotExist()
    }
}
