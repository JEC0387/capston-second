package com.jeiu.fashionproject

import android.app.Application
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.recommend.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AvatarModelAnalysisTest {
    private fun model() = checkNotNull(AvatarAnalysisInput.build(160f, 55f, emptyMap(), null, 1L))

    @Test fun heightWeightModelProducesVisualAnalysisWithoutInventingTapeValues() {
        val profile = model()
        val result = checkNotNull(AvatarModelAnalysis.analyze(profile))
        assertEquals(AvatarAnalysisPath.MODEL_ESTIMATE, AvatarModelAnalysis.path(profile))
        assertTrue(AvatarAnalysisInput.recorded(profile).isEmpty())
        assertTrue(AvatarBodyAnalysis.analyze(profile).missing.isNotEmpty())
        assertEquals(profile.centimeters!![AvatarPart.CHEST] / profile.centimeters[AvatarPart.HIP], result.chestToHip, 0f)
        assertTrue(result.usesModelCircumferences)
    }

    @Test fun eachCorePartChangesBothRealRendererGeometryAndItsAnalysis() {
        val original = model()
        val body = checkNotNull(original.centimeters)
        val before = checkNotNull(AvatarModelAnalysis.analyze(original))
        val mesh = PremiumAvatarMesh.build(body)
        for (part in listOf(AvatarPart.WAIST, AvatarPart.CHEST, AvatarPart.SHOULDER, AvatarPart.HIP)) {
            val changedBody = checkNotNull(body.adjusted(part, body[part] + 2f))
            val changed = original.copy(centimeters = changedBody)
            assertNotEquals(part.name, before, AvatarModelAnalysis.analyze(changed))
            assertFalse(part.name, mesh.contentEquals(PremiumAvatarMesh.build(changedBody)))
            assertEquals(original.centimeters.references, changedBody.references)
            val restored = changed.copy(centimeters = checkNotNull(changedBody.adjusted(part, body[part])))
            assertEquals(before, AvatarModelAnalysis.analyze(restored))
        }
    }

    @Test fun measuredClassifierStaysIndependentWhenRulerChangesTheModel() {
        val tape = mapOf(AvatarPart.CHEST to 88f, AvatarPart.WAIST to 71f, AvatarPart.HIP to 95f, AvatarPart.SHOULDER to 39f)
        val measured = checkNotNull(AvatarAnalysisInput.build(160f, 55f, tape, null, 1L))
        assertEquals(AvatarAnalysisPath.MEASURED, AvatarModelAnalysis.path(measured))
        val changed = measured.copy(centimeters = measured.centimeters!!.adjusted(AvatarPart.WAIST, 73f))
        assertEquals(AvatarAnalysisPath.MODEL_ESTIMATE, AvatarModelAnalysis.path(changed))
        assertEquals(tape, AvatarAnalysisInput.recorded(changed))
        assertEquals(71f, AvatarBodyAnalysis.analyze(changed).measurements[BodyDimension.WAIST_CIRCUMFERENCE]!!.cm, 0f)
        assertNotEquals(AvatarModelAnalysis.analyze(measured), AvatarModelAnalysis.analyze(changed))
    }

    @Test fun legacyRatiosRemainWidthsAndInvalidProfilesHaveNoAnalysis() {
        val legacy = AvatarProfile(160f, 55f)
        val result = checkNotNull(AvatarModelAnalysis.analyze(legacy))
        assertFalse(result.usesModelCircumferences)
        assertEquals(legacy.parameters().chestWidthCm / legacy.parameters().hipWidthCm, result.chestToHip, 0f)
        assertNotEquals(result, AvatarModelAnalysis.analyze(legacy.copy(waist = 1.1f)))
        assertNull(AvatarModelAnalysis.path(null))
        for (invalid in listOf(legacy.copy(heightCm = Float.NaN), legacy.copy(weightKg = 0f), legacy.copy(waist = 5f))) {
            assertNull(AvatarModelAnalysis.analyze(invalid))
            assertNull(AvatarModelAnalysis.path(invalid))
        }
    }

    @Test fun savedProfileRoundTripKeepsAdjustedAnalysisAndOrigins() {
        val profile = model().let { it.copy(centimeters = it.centimeters!!.adjusted(AvatarPart.WAIST, it.centimeters[AvatarPart.WAIST] + 2f)) }
        val restored = checkNotNull(AvatarProfileStore.decode(AvatarProfileStore.encode(profile)))
        assertEquals(profile, restored)
        assertEquals(AvatarModelAnalysis.analyze(profile), AvatarModelAnalysis.analyze(restored))
        assertTrue(AvatarAnalysisInput.recorded(restored).isEmpty())
    }
}
