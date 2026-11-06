package com.jeiu.fashionproject

import android.app.Application
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.ui.screens.OfficialDemoScreen
import com.jeiu.fashionproject.ui.viewmodel.*
import kotlinx.coroutines.cancel
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Explicit opt-in only. Runs the production Compose screen/adapter on Android, ONE official-example POST.
 * A test registry supplies a private save destination; the system document-picker interaction is not certified.
 * Never run in ordinary CI, never erase a previous journal to obtain another inference allowance. */
@RunWith(AndroidJUnit4::class)
class OfficialDemoLiveTest {
    @get:Rule val compose = createComposeRule()
    private var model: OfficialDemoViewModel? = null
    @After fun cleanup() { model?.viewModelScope?.cancel() }
    @Test fun officialExampleNewRequestCompareAndPrivateSave() {
        Assume.assumeTrue(BuildConfig.PREVIEW_BUILD &&
            InstrumentationRegistry.getArguments().getString("officialDemoLive") == "true")
        val app = ApplicationProvider.getApplicationContext<Application>()
        val journal = PrivateDemoJournal(app)
        check(journal.read() == null) { "Existing demo journal retained; do not repeat the live POST" }
        val output = File(app.noBackupFilesDir, "p15-official-demo-result.png")
        check(!output.exists()) { "Existing result retained" }
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                    check(contract is ActivityResultContracts.CreateDocument)
                    dispatchResult(requestCode, Uri.fromFile(output))
                }
            }
        }
        val current = OfficialDemoViewModel(OfficialDemoRepository(), journal)
        model = current
        compose.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
            OfficialDemoScreen({}, current)
        } }
        compose.onNodeWithTag("officialDemoExample").performScrollTo().performClick()
        compose.onNodeWithTag("officialDemoCheck").performScrollTo().performClick()
        compose.waitUntil(60_000) { !current.state.working }
        assertTrue("Public contract unavailable; no POST performed", current.canStart)
        compose.onNodeWithTag("officialDemoGenerate").performClick()
        compose.onNodeWithTag("officialDemoConsent").performClick()
        compose.waitUntil(420_000) { !current.state.working }
        assertNotNull("Live request failed; inspect journal without retrying POST", current.state.result)
        compose.onNodeWithTag("officialDemoSuccess").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("officialDemoCompare").performScrollTo().performClick()
        compose.onNodeWithContentDescription("공식 예제 원본").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("officialDemoCompare").performScrollTo().performClick()
        compose.onNodeWithContentDescription("새 요청 생성 결과").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("officialDemoSave").performScrollTo().performClick()
        compose.waitUntil(30_000) { current.state.savedAt != 0L || !current.state.working }
        assertTrue(current.state.savedAt != 0L)
        assertEquals(current.state.result!!.sha256, OfficialDemoContract.sha(output.readBytes()))
        compose.onNodeWithTag("officialDemoSaved").performScrollTo().assertIsDisplayed()
    }
}
