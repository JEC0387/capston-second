package com.jeiu.fashionproject

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class P61ReadinessTest {
    @Test fun reachableDatabaseDoesNotImplyAuthenticatedAccountOrProviderReadiness() {
        val lines = OutfitReadiness.lines(ServerSettings.Check(true, true, "available", emptyList(), true, 2),
            "registration_required", false, true, null)
        assertEquals("서버 연결: 응답 확인", lines[0])
        assertEquals("DB·인증 API: 준비 확인", lines[1])
        assertTrue(lines[2].contains("승인된 서버 계정"))
        assertTrue(lines[3].contains("확인할 수 없어요"))
    }
    @Test fun noProbeAndNoServerApprovalCannotClaimReadiness() {
        val lines = OutfitReadiness.lines(null, "uninitialized", false, false, true)
        assertEquals("서버 연결: 미확인", lines[0])
        assertTrue(lines[3].contains("승인되지 않았어요"))
    }
    @Test fun installtestBackgroundSessionValidationNeverRegistersOrUploads() = runBlocking {
        assumeTrue(BuildConfig.INSTALL_TEST_BUILD)
        val app = ApplicationProvider.getApplicationContext<Application>()
        MockWebServer().use { server ->
            server.start()
            val previous = BackendApi.BASE_URL
            val previousState = DataAuth.state.value
            val authState = ReflectionHelpers.getStaticField<kotlinx.coroutines.flow.MutableStateFlow<String>>(DataAuth::class.java, "_state")
            val fields = listOf("context", "binding", "storageLocked").associateWith {
                ReflectionHelpers.getStaticField<Any?>(DataAuth::class.java, it)
            }
            try {
                BackendApi.BASE_URL = server.url("/").toString().trimEnd('/')
                ReflectionHelpers.setStaticField(DataAuth::class.java, "context", app)
                ReflectionHelpers.setStaticField(DataAuth::class.java, "binding", null)
                ReflectionHelpers.setStaticField(DataAuth::class.java, "storageLocked", false)
                assertFalse(DataAuth.ensureReady())
                assertFalse(DataAuth.validateSession())
                assertEquals("registration_required", DataAuth.state.value)
                assertEquals(0, server.requestCount)
            } finally {
                BackendApi.BASE_URL = previous
                fields.forEach { (name, value) -> ReflectionHelpers.setStaticField(DataAuth::class.java, name, value) }
                authState.value = previousState
            }
        }
    }
}
