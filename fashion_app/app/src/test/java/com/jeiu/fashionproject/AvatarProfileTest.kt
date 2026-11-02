package com.jeiu.fashionproject

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.AvatarProfile
import com.jeiu.fashionproject.data.AvatarProfileStore
import com.jeiu.fashionproject.data.BodyAvatarMesh
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AvatarProfileTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val prefs get() = app.getSharedPreferences("avatar_visual_profile_v1", 0)

    @Before @After fun reset() { prefs.edit().clear().commit(); AvatarProfileStore.load(app) }

    @Test fun validInputsProduceExplicitVisualEstimateAndInvalidInputsAreRejected() {
        val slim = AvatarProfile(170f, 55f).parameters()
        val fuller = AvatarProfile(170f, 95f).parameters()
        assertTrue(slim.isValid())
        assertTrue(fuller.waistWidthCm > slim.waistWidthCm)
        assertTrue(fuller.notes.any { it.contains("추정") })
        assertFalse(AvatarProfile(99f, 65f).valid())
        assertFalse(AvatarProfile(170f, Float.NaN).valid())
        assertFalse(AvatarProfile(170f, 65f, thigh = 5f).valid())
    }

    @Test fun eachAdjustmentChangesTheRenderedMeshGeometry() {
        val basic = AvatarProfile(170f, 65f)
        val mesh = BodyAvatarMesh.build(basic.parameters(), thighScale = basic.thigh)
        listOf(
            basic.copy(shoulder = 1.2f), basic.copy(waist = 1.2f),
            basic.copy(hip = 1.2f), basic.copy(thigh = 1.2f), basic.copy(leg = 1.2f)
        ).forEach { adjusted ->
            val changed = BodyAvatarMesh.build(adjusted.parameters(), thighScale = adjusted.thigh)
            assertTrue("${adjusted} did not change mesh", mesh.indices.any { mesh[it] != changed[it] })
        }
    }

    @Test fun savesAndReloadsOnlyTheAvatarProfile() {
        val profile = AvatarProfile(172f, 70f, 1.1f, .9f, 1.2f, 1.05f, 1.1f)
        assertTrue(AvatarProfileStore.save(app, profile))
        AvatarProfileStore.load(app)
        assertEquals(profile, AvatarProfileStore.current.value)
        assertNull(AvatarProfileStore.issue.value)
    }

    @Test fun corruptedProfileRemainsOnDiskUntilExplicitClear() {
        prefs.edit().putString("profile", "{broken").commit()
        AvatarProfileStore.load(app)
        assertNull(AvatarProfileStore.current.value)
        assertNotNull(AvatarProfileStore.issue.value)
        assertFalse(AvatarProfileStore.save(app, AvatarProfile(170f, 65f)))
        assertEquals("{broken", prefs.getString("profile", null))
        assertTrue(AvatarProfileStore.clear(app))
        assertNull(prefs.getString("profile", null))
    }
}
