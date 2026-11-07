package com.jeiu.fashionproject

import android.app.Application
import com.jeiu.fashionproject.data.ServerOutfitQaConnection
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Pure validation and RAM state. Never a backend connection or credential registration. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class P52QaConnectionTest {
    private val token = "synthetic_" + "x".repeat(40)
    private val production = "https://fashion-backend-production-aba0.up.railway.app"
    private fun validate(base: String, value: String = token, approved: Boolean = true, eligible: Boolean = true) =
        ServerOutfitQaConnection.validate(base, value, approved, eligible, production)

    @Test fun requiresExplicitQaApprovalAndHttpsOrigin() {
        assertNull(validate("https://qa.example", approved = false))
        assertNull(validate("https://qa.example", eligible = false))
        listOf("http://127.0.0.1:8000", production, production.uppercase(), production + ".", "https://api.genlook.app",
            "https://name:secret@qa.example", "https://qa.example/path", "https://qa.example?q=1",
            "https://qa.example#fragment", "https://qa.example:0").forEach { assertNull(it, validate(it)) }
    }

    @Test fun rejectsVendorKeysAndMalformedBearerWithoutFallback() {
        listOf("gk_" + "a".repeat(50), "sk_" + "b".repeat(50), "short", "a".repeat(40) + "\n" + "b",
            "Bearer bad token", "Bearer " + "x".repeat(101)).forEach { assertNull(validate("https://qa.example", it)) }
    }

    @Test fun explicitServerAndTokenBindTheJournalIdentity() {
        val a = validate("https://qa.example/", "Bearer $token")!!
        val same = validate("https://qa.example", token)!!
        val otherHost = validate("https://another-qa.example", token)!!
        val otherUser = validate("https://qa.example", token + "y")!!
        assertEquals("https://qa.example", a.base)
        assertEquals(token, a.token) // Transport adds exactly one Bearer prefix.
        assertEquals(token, same.token)
        assertEquals(a.accountKey, same.accountKey)
        assertNotEquals(a.accountKey, otherHost.accountKey)
        assertNotEquals(a.accountKey, otherUser.accountKey)
        assertFalse(a.toString().contains(token))
    }

    @Test fun connectionStartsUnsetAndCanBeClearedWithoutTouchingRecords() {
        ServerOutfitQaConnection.clear()
        try {
            assertNull(ServerOutfitQaConnection.sessionOrNull())
            if (ServerOutfitQaConnection.supported) {
                assertTrue(ServerOutfitQaConnection.configure("https://qa.example", token, true))
                assertNotNull(ServerOutfitQaConnection.sessionOrNull())
                val before = ServerOutfitQaConnection.revision
                assertFalse(ServerOutfitQaConnection.configure(production, token, true))
                assertEquals(before, ServerOutfitQaConnection.revision)
                assertEquals("https://qa.example", ServerOutfitQaConnection.sessionOrNull()!!.base)
            }
        } finally { ServerOutfitQaConnection.clear() }
        assertNull(ServerOutfitQaConnection.sessionOrNull())
    }
}
