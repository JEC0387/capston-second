package com.jeiu.fashionproject

import android.app.Application
import com.jeiu.fashionproject.data.*
import kotlinx.coroutines.*
import okhttp3.Request
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ServerProbeDeadlineTest {
    @Test fun dbResponseAfterOldFiveSecondLimitRemainsReadyAndUiCoroutineIsFree() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"service\":\"FashionProject API\"}"))
            server.enqueue(MockResponse().setBody("{\"ready\":true,\"status\":\"available\",\"syncReady\":true,\"authProtocol\":2}")
                .setHeadersDelay(5500, TimeUnit.MILLISECONDS))
            server.start()
            val deferred=async { ServerSettings.check(server.url("/").toString().trimEnd('/')) }
            delay(150); assertFalse(deferred.isCompleted)
            val result=withTimeout(12000) { deferred.await() }
            assertTrue(result.dbReady); assertTrue(result.syncReady); assertEquals(2,result.authProtocol)
            assertEquals("/",server.takeRequest().path);assertEquals("/api/health/database",server.takeRequest().path)
            assertEquals(25000,ServerSettings.databaseClient.callTimeoutMillis)
            assertEquals(8000,ServerSettings.rootClient.callTimeoutMillis)
        }
    }
    @Test fun stalledRequestHasTotalDeadlineAndNoAutomaticRetry() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));server.start()
            val client=SafeAccountHttp.client(1)
            val began=System.nanoTime()
            assertTrue(runCatching { SafeAccountHttp.send(client,Request.Builder().url(server.url("/")).build()) }.isFailure)
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-began)<5000)
            assertEquals(1,server.requestCount)
        }
    }
    @Test fun cancellationAndRedirectCannotContinueOrLeakAuthorization() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));server.start()
            val call=launch { SafeAccountHttp.send(SafeAccountHttp.client(25),Request.Builder().url(server.url("/wait")).build()) }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(3,TimeUnit.SECONDS)) }
            withTimeout(1500) { call.cancelAndJoin() };assertEquals(1,server.requestCount)
        }
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location","https://other.invalid/private"));server.start()
            val reply=SafeAccountHttp.send(SafeAccountHttp.client(2),Request.Builder().url(server.url("/")).build())
            assertEquals(302,reply.code);assertEquals(1,server.requestCount)
        }
    }
    @Test fun oversizedHealthResponseIsRejected() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("x".repeat(32769)));server.start()
            assertTrue(runCatching { SafeAccountHttp.send(SafeAccountHttp.client(2),Request.Builder().url(server.url("/")).build()) }.isFailure)
        }
    }
}
