package com.jeiu.fashionproject

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jeiu.fashionproject.ui.screens.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** UI callbacks use explicit synthetic results; actual pixel/PNG evidence is in the renderer test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class, qualifiers="w500dp-h900dp")
class P35ManualPhotoExportUiTest {
    @get:Rule val compose=createComposeRule()
    private val first=ManualPhotoSnapshot("file:///synthetic-a.png",1,Size(100f,200f),emptyList(),1f)
    @Test fun duplicateClickBlockedAndLateResultAfterInputChangeHidden() {
        val input=mutableStateOf<ManualPhotoSnapshot?>(first)
        val deferred=CompletableDeferred<ManualPhotoResult>();var calls=0
        compose.setContent { MaterialTheme { ManualPhotoExportPanel(input.value) { calls++;deferred.await() } } }
        compose.onNodeWithTag("manualPngCreate").performClick()
        compose.onNodeWithTag("manualPngCreate").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1,calls);input.value=first.copy(generation=2) }
        compose.runOnIdle { deferred.complete(ManualPhotoResult(first,byteArrayOf(1),byteArrayOf(2))) }
        compose.waitForIdle()
        compose.onNodeWithTag("manualPngCompare").assertIsNotEnabled()
        compose.onNodeWithTag("manualPngSave").assertIsNotEnabled()
        compose.onNodeWithTag("manualPngMessage").assertTextContains("배치가 바뀌었어요",substring=true)
    }
    @Test fun cancelAndFailureAllowRetryWithoutSuccess() {
        val deferred=CompletableDeferred<ManualPhotoResult>();var failNext=false
        compose.setContent { MaterialTheme { ManualPhotoExportPanel(first) { if(failNext) error("synthetic_failure") else deferred.await() } } }
        compose.onNodeWithTag("manualPngCreate").performClick()
        compose.onNodeWithTag("manualPngCancel").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("manualPngCreate").assertIsEnabled()
        compose.onNodeWithTag("manualPngSave").assertIsNotEnabled()
        compose.runOnIdle {failNext=true}
        compose.onNodeWithTag("manualPngCreate").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("manualPngMessage").assertTextContains("합성하지 못했어요",substring=true)
        compose.onNodeWithTag("manualPngCreate").assertIsEnabled()
    }
    @Test fun noInputCannotCreateButSavedFileCanBeChosen() {
        compose.setContent { MaterialTheme { ManualPhotoExportPanel(null) } }
        compose.onNodeWithTag("manualPngCreate").assertIsNotEnabled()
        compose.onNodeWithTag("manualPngCompare").assertIsNotEnabled()
        compose.onNodeWithTag("manualPngOpen").assertIsEnabled()
        compose.onNodeWithText("기기 내 2D 합성 · 생성형 AI 아님").assertExists()
    }
}
