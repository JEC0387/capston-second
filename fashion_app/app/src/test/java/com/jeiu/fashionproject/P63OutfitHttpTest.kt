package com.jeiu.fashionproject

import android.app.Application
import com.jeiu.fashionproject.data.ServerOutfitVtonRepository as Repo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class)
class P63OutfitHttpTest {
    @Test fun authenticationTransportAndProviderFailuresStayDistinct() {
        assertEquals("authentication_required",Repo.httpFailure("/config",401,null).code)
        assertEquals("https_required",Repo.httpFailure("/config",403,"{\"detail\":\"https_required\"}").code)
        assertEquals("consent_or_transport_required",Repo.httpFailure("/config",403,null).code)
        assertEquals("engine_unavailable",Repo.httpFailure("/config",503,null).code)
        assertTrue(Repo.friendlyFailure(Repo.httpFailure("/config",503,null)).contains("HTTP 503"))
    }
    @Test fun arbitraryResponseBodiesNeverReachUiOrException() {
        val body="{\"detail\":\"secret-token-signed-url\"}"
        for(status in listOf(401,403,422,503,500)) {
            val failure=Repo.httpFailure("/jobs",status,body)
            assertFalse(Repo.friendlyFailure(failure).contains("secret"))
            assertFalse(failure.message.orEmpty().contains("secret"))
            assertEquals(status,failure.httpStatus)
        }
        assertEquals("consent_or_transport_required",Repo.httpFailure("/config",403," ".repeat(1025)+"{\"detail\":\"https_required\"}").code)
    }
}
