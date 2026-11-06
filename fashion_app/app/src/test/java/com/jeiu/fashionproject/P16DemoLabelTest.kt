package com.jeiu.fashionproject

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.ui.screens.OfficialDemoScreen
import com.jeiu.fashionproject.ui.viewmodel.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class)
class P16DemoLabelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun reenteringCompletedTrialLabelsItPreviousWithoutAnotherSubmission() {
        var posts = 0
        val image = DemoImage(byteArrayOf(), 32, 32, "synthetic-mock-only")
        val gateway = object : OfficialDemoGateway {
            override suspend fun check() {}
            override suspend fun example(id: String) = image
            override suspend fun submit(id: String): String { posts++; return "a".repeat(32) }
            override suspend fun awaitResult(eventId: String, progress: suspend (String) -> Unit) =
                OfficialDemoContract.ORIGIN + "/gradio_api/file=/tmp/gradio/test/result.webp"
            override suspend fun result(url: String) = image
        }
        val journal = object : DemoJournal {
            var value: String? = null
            override fun read() = value
            override fun write(value: String) { this.value = value }
        }
        val model = OfficialDemoViewModel(gateway, journal, enabled = true)
        var visible by mutableStateOf(true)
        compose.setContent { if (visible) OfficialDemoScreen({}, model) }
        compose.runOnIdle { model.check() }
        compose.waitUntil { model.canStart }
        compose.runOnIdle { model.start(OfficialDemoContract.EXAMPLE, true) }
        compose.waitUntil { model.state.result != null }
        compose.onNodeWithContentDescription("새 요청 생성 결과").assertExists()
        compose.runOnIdle { visible = false }
        compose.onNodeWithTag("officialDemoTitle").assertDoesNotExist()
        compose.runOnIdle { visible = true }
        compose.onNodeWithContentDescription("이전에 생성한 결과").assertExists()
        org.junit.Assert.assertEquals(1, posts)
    }
}
