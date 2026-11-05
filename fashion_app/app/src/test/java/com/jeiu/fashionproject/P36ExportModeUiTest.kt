package com.jeiu.fashionproject

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jeiu.fashionproject.ui.screens.*
import com.jeiu.fashionproject.ui.viewmodel.FittingPoseDetector
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Mode/error contract only. Engine pixels are independently checked in P36DrapeCompositorTest. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class, qualifiers="w500dp-h900dp")
class P36ExportModeUiTest {
    @get:Rule val compose=createComposeRule()
    private val body=FittingPoseDetector.BodyPoints(100,200,Offset(30f,50f) to Offset(70f,50f),
        Offset(35f,100f) to Offset(65f,100f),null)
    private val first=ManualPhotoSnapshot("file:///synthetic.png",1,Size(100f,200f),emptyList(),1f,body)

    @Test fun automaticModePassesSnapshotAndShowsRejectedCategoryWithoutSuccess() {
        var captured:ManualPhotoSnapshot?=null
        compose.setContent {MaterialTheme {ManualPhotoExportPanel(first) {
            captured=it;throw LocalDrapeFailure("발끝 기준점이 부족해 자동 착장을 적용하지 않았어요.")
        }}}
        compose.onNodeWithTag("localDrapeMode").performClick()
        compose.onNodeWithText("기기 내 자동 착장 합성 · 생성형 AI 아님").assertExists()
        compose.onNodeWithTag("manualPngCreate").performClick()
        compose.waitForIdle()
        compose.runOnIdle {assertEquals(true,captured?.automatic);assertEquals(body,captured?.body)}
        compose.onNodeWithTag("manualPngMessage").assertTextContains("발끝 기준점",substring=true)
        compose.onNodeWithTag("manualPngSave").assertIsNotEnabled()
        compose.onNodeWithTag("manualPngCreate").assertIsEnabled()
    }

    @Test fun lostBodyCannotGenerateButManualModeRemainsAvailable() {
        val input=mutableStateOf<ManualPhotoSnapshot?>(first)
        var calls=0
        compose.setContent {MaterialTheme {ManualPhotoExportPanel(input.value) {calls++;error("unexpected")}}}
        compose.onNodeWithTag("localDrapeMode").performClick()
        compose.runOnIdle {input.value=first.copy(body=null,generation=2)}
        compose.onNodeWithTag("manualPngCreate").assertIsNotEnabled()
        compose.onNodeWithTag("localDrapeMode").performClick()
        compose.onNodeWithTag("manualPngCreate").assertIsEnabled()
        compose.onNodeWithText("기기 내 2D 합성 · 생성형 AI 아님").assertExists()
        compose.runOnIdle {assertEquals(0,calls)}
    }
}
