package com.jeiu.fashionproject

import com.jeiu.fashionproject.ui.screens.legacyFashnTryOnEntryEnabled
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P68ReleaseUserPathTest {
    @Test fun legacyFashnEntryIsRestrictedToDeveloperBuilds() {
        assertFalse(legacyFashnTryOnEntryEnabled(debugBuild = false, installTestBuild = false))
        assertFalse(legacyFashnTryOnEntryEnabled(debugBuild = true, installTestBuild = true))
        assertTrue(legacyFashnTryOnEntryEnabled(debugBuild = true, installTestBuild = false))
    }
}
