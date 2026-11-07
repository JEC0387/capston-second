package com.jeiu.fashionproject

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jeiu.fashionproject.data.ClothingPhotoConsent
import com.jeiu.fashionproject.ui.screens.ClothingPhotoUploadDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class P57PhotoConsentUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun dismissalDoesNotIssueAnUploadConsentOrMutateDraft() {
        val draft = linkedMapOf("name" to "synthetic shirt", "category" to "상의")
        val before = draft.toMap()
        var cancelled = 0
        var confirmed: ClothingPhotoConsent? = null
        compose.setContent { ClothingPhotoUploadDialog("content://synthetic/photo", "https://example.invalid",
            onCancel = { cancelled++ }, onConfirm = { confirmed = it }) }
        compose.onNodeWithTag("cancelClothingUpload").performClick()
        compose.runOnIdle { assertEquals(1, cancelled); assertNull(confirmed); assertEquals(before, draft) }
    }
    @Test fun insecureDestinationCannotIssueConsent() {
        compose.setContent { ClothingPhotoUploadDialog("content://synthetic/photo", "http://example.invalid", {}, {}) }
        compose.onNodeWithTag("confirmClothingUpload").assertIsNotEnabled()
    }
}
