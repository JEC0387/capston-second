package com.jeiu.fashionproject

import com.jeiu.fashionproject.ui.viewmodel.*
import org.junit.Assert.*
import org.junit.Test

class BodyPhotoEntryFlowTest {
    @Test fun emptyHeightExplainsInsteadOfDisablingCamera() {
        assertEquals(BodyPhotoEntryRequest(BodyPhotoSource.CAMERA, BodyPhotoEntryStep.HEIGHT),
            BodyPhotoEntryFlow().request(BodyPhotoSource.CAMERA, "", false))
    }
    @Test fun cameraResumesOnceAfterHeightAndConsent() {
        val flow = BodyPhotoEntryFlow()
        flow.request(BodyPhotoSource.CAMERA, "", false)
        assertEquals(BodyPhotoEntryStep.CONSENT, flow.advance("170.5", false)?.step)
        assertEquals(BodyPhotoEntryRequest(BodyPhotoSource.CAMERA, BodyPhotoEntryStep.LAUNCH), flow.advance("170.5", true))
        assertNull(flow.advance("170.5", true))
        assertNull(flow.request(BodyPhotoSource.CAMERA, "170.5", true))
    }
    @Test fun galleryRemembersOriginalActionAndStorageOptOutDoesNotBlock() {
        val flow = BodyPhotoEntryFlow()
        flow.request(BodyPhotoSource.GALLERY, "170", false)
        assertEquals(BodyPhotoSource.GALLERY, flow.advance("170", true)?.source)
    }
    @Test fun invalidHeightsNeverOpenExternalPhotoActivity() {
        for (height in listOf("", "99", "231", "NaN", "Infinity", "-170", "170.55")) {
            assertEquals(height, BodyPhotoEntryStep.HEIGHT,
                BodyPhotoEntryFlow().request(BodyPhotoSource.GALLERY, height, true)?.step)
        }
    }
    @Test fun repeatedClicksDoNotReplacePendingSource() {
        val flow = BodyPhotoEntryFlow()
        flow.request(BodyPhotoSource.CAMERA, "", false)
        assertNull(flow.request(BodyPhotoSource.GALLERY, "170", true))
        assertEquals(BodyPhotoSource.CAMERA, flow.advance("170", true)?.source)
    }
    @Test fun dismissConsentDoesNotResumeOrLaunch() {
        val flow = BodyPhotoEntryFlow()
        flow.request(BodyPhotoSource.CAMERA, "170", false)
        flow.cancel()
        assertNull(flow.advance("170", true))
        assertEquals(BodyPhotoSource.GALLERY, flow.request(BodyPhotoSource.GALLERY, "170", true)?.source)
    }
    @Test fun pickerCancelAndCameraReturnPermitRetry() {
        for (source in BodyPhotoSource.entries) {
            val flow = BodyPhotoEntryFlow()
            assertEquals(BodyPhotoEntryStep.LAUNCH, flow.request(source, "170", true)?.step)
            flow.returnedFromExternal()
            assertEquals(BodyPhotoEntryStep.LAUNCH, flow.request(source, "170", true)?.step)
        }
    }
    @Test fun consentAndHeightAreRecheckedBeforeResuming() {
        val flow = BodyPhotoEntryFlow()
        flow.request(BodyPhotoSource.CAMERA, "170", false)
        assertEquals(BodyPhotoEntryStep.HEIGHT, flow.advance("", true)?.step)
        assertEquals(BodyPhotoEntryStep.CONSENT, flow.advance("170", false)?.step)
    }
}
