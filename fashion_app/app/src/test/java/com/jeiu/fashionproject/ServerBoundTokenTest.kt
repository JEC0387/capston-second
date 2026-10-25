package com.jeiu.fashionproject

import com.jeiu.fashionproject.data.ServerBoundToken
import java.net.URL
import org.junit.Assert.*
import org.junit.Test

class ServerBoundTokenTest {
    private val credential = ServerBoundToken("private-token", "https://original.test:443")
    @Test fun onlyTheIssuingServerReceivesTheToken() {
        assertEquals("private-token", credential.forDestination("https://original.test:443", URL("https://original.test:443/api/profile")))
        assertNull(credential.forDestination("https://other.test:443", URL("https://other.test:443/api/profile")))
        assertNull(credential.forDestination("https://other.test:443", URL("https://original.test:443/api/profile")))
    }
    @Test fun insecureAndLookalikeDestinationsAreRejected() {
        assertNull(credential.forDestination(credential.server, URL("http://original.test:443/api/profile")))
        assertNull(credential.forDestination(credential.server, URL("https://original.test.evil:443/api/profile")))
        assertNull(credential.forDestination(credential.server, URL("https://original.test:443@evil.test/api/profile")))
    }
}
