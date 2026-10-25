package com.jeiu.fashionproject

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.recommend.GarmentSizeRow
import com.jeiu.fashionproject.ui.components.MeasuredSizeComparison
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class MeasuredSizeUiTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    @Before @After fun clear(){context.getSharedPreferences("size_feedback",0).edit().clear().commit()}
    @Test fun feedbackRequiresExplicitOptInBeforeDevicePersistence() {
        val profile=BodyDimensions.resolve(ManualBodyMeasurements(170f,40f,90f,75f,95f,88f,52f,1),null)
        val row=GarmentSizeRow("TEST","상의","M",43f,"단면","https://example.test/chart",chestCm=49f)
        compose.setContent { Column(Modifier.verticalScroll(rememberScrollState())) { MeasuredSizeComparison(profile,listOf(row)) } }
        compose.onNodeWithText("잘 맞았어요").performScrollTo().performClick()
        assertTrue(SizeFeedbackStore.load(context).isEmpty())
        compose.onNodeWithTag("sizeFeedbackSave").performScrollTo().performClick()
        compose.onNodeWithText("잘 맞았어요").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(SizeSatisfaction.FIT,SizeFeedbackStore.load(context).single().satisfaction) }
    }
}
