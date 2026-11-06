package com.jeiu.fashionproject

import com.jeiu.fashionproject.data.BackendApi
import com.jeiu.fashionproject.data.DbSync
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class PreviewIsolationTest {
    @Test fun previewCannotReadWriteOrDeleteProductionPersonalRecords() = runBlocking {
        if (!BuildConfig.PREVIEW_BUILD) {
            assertEquals("com.jeiu.fashionproject", BuildConfig.APPLICATION_ID)
            return@runBlocking
        }
        assertEquals("com.jeiu.fashionproject.preview", BuildConfig.APPLICATION_ID)
        assertFalse(DbSync.isEnabled)
        val server = MockWebServer()
        val previous = BackendApi.BASE_URL
        server.start()
        try {
            BackendApi.BASE_URL = server.url("/").toString().trimEnd('/')
            assertNull(BackendApi.getJson("/api/profile"))
            assertNull(BackendApi.postJson("/api/wardrobe/sync", "{}"))
            assertFalse(BackendApi.delete("/api/me"))
            assertEquals(0, server.requestCount)
        } finally { BackendApi.BASE_URL = previous; server.shutdown() }
    }
}
