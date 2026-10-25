package com.jeiu.fashionproject

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.AppBuildInfo
import com.jeiu.fashionproject.data.ServerSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AppBuildInfoTest {
    @Test fun versionComesFromInstalledPackageAndBuildSourceIsIdentifiable() {
        val info = AppBuildInfo.installed(ApplicationProvider.getApplicationContext())
        assertEquals(BuildConfig.APPLICATION_ID, info.packageName)
        assertEquals(BuildConfig.VERSION_NAME, info.versionName)
        assertEquals(BuildConfig.VERSION_CODE.toLong(), info.versionCode)
        assertTrue(info.versionCode > 3)
        assertTrue(info.gitSha.matches(Regex("[a-f0-9]{40}")))
        assertEquals(BuildConfig.GIT_SHA.take(8), info.shortSha)
    }

    @Test fun oldServerCannotBePresentedAsAuthAndSyncReady() {
        assertTrue(ServerSettings.Check(true, true).message.contains("SQL 005/006"))
        assertTrue(ServerSettings.Check(true, true, missing = listOf("auth_credentials")).message.contains("마이그레이션"))
        assertTrue(ServerSettings.Check(true, true, syncReady = true, authProtocol = 2).message.contains("실제 전송 결과는 별도"))
    }
}
