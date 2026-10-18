package com.jeiu.fashionproject

import com.jeiu.fashionproject.data.ServerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 마이페이지 > 서버 주소 입력 다듬기 */
class ServerSettingsTest {

    @Test
    fun normalize_addsSchemeAndDefaultPort() {
        assertEquals("http://192.168.0.10:8000", ServerSettings.normalize("192.168.0.10"))
        assertEquals("http://192.168.0.10:8000", ServerSettings.normalize(" http://192.168.0.10:8000/ "))
        assertEquals("http://192.168.0.10:9000", ServerSettings.normalize("192.168.0.10:9000"))
        assertEquals("http://my-pc.local:8000", ServerSettings.normalize("my-pc.local"))
        assertEquals("https://example.com:443", ServerSettings.normalize("https://example.com"))
        assertEquals("http://10.0.2.2:8000", ServerSettings.normalize("10.0.2.2:8000"))
    }

    @Test
    fun normalize_rejectsBadInput() {
        assertNull(ServerSettings.normalize(""))
        assertNull(ServerSettings.normalize("192.168.0.10/api"))      // 경로는 넣지 않음
        assertNull(ServerSettings.normalize("ftp://192.168.0.10"))
        assertNull(ServerSettings.normalize("192.168 .0.10"))
        assertNull(ServerSettings.normalize("http://user:pw@192.168.0.10"))
        assertNull(ServerSettings.normalize("192.168.0.10:99999"))
    }

    @Test
    fun checkMessages() {
        assertTrue(ServerSettings.Check(reachable = false).message.contains("0.0.0.0"))
        assertTrue(ServerSettings.Check(true, true, "available", listOf("saved_outfits")).message.contains("apply_migrations"))
        assertTrue(ServerSettings.Check(true, false, "not_configured").message.contains("DATABASE_URL"))
        assertFalse(ServerSettings.Check(true, true, "available").message.contains("apply_migrations"))
    }
}
