package com.jeiu.fashionproject

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.jeiu.fashionproject.ui.components.InstallTestAccountPanel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w600dp-h1200dp")
class InstallTestAccountUiTest {
    @get:Rule val ui=createComposeRule()
    @Test fun registrationNeedsApprovalReferenceAndSeparateConfirmation() {
        var posts=0
        ui.setContent { Column(Modifier.verticalScroll(rememberScrollState())) { InstallTestAccountPanel("approval_required",false,false,{ approved,reference,_->
            assertTrue(approved); assertEquals("APPROVED-1",reference);posts++ },{error("verify not available")}) } }
        ui.onNodeWithTag("testAccountCreate").assertDoesNotExist()
        ui.onNodeWithTag("testAccountExpand").performClick()
        ui.onNodeWithTag("testAccountCreate").assertIsNotEnabled()
        ui.onNodeWithTag("testAccountReference").performTextInput("APPROVED-1")
        ui.onNodeWithTag("testAccountCreate").assertIsNotEnabled()
        ui.onNodeWithTag("testAccountApproval").performClick()
        ui.onNodeWithTag("testAccountCreate").assertIsEnabled().performScrollTo().performClick()
        ui.runOnIdle { assertEquals(0,posts) }
        ui.onNodeWithTag("testAccountConfirm").performClick()
        ui.runOnIdle { assertEquals(1,posts) }
    }
    @Test fun unknownOutcomeOffersNoRegistrationOrRetry() {
        ui.setContent { Column(Modifier.verticalScroll(rememberScrollState())) { InstallTestAccountPanel("outcome_unknown",true,false,{_,_,_->error("no registration")},{error("no retry")}) } }
        ui.onNodeWithTag("testAccountExpand").performClick()
        ui.onNodeWithTag("testAccountCreate").assertDoesNotExist()
        ui.onNodeWithTag("testAccountVerify").assertIsNotEnabled()
    }
}
