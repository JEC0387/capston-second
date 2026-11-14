package com.jeiu.fashionproject

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.recommend.*
import com.jeiu.fashionproject.ui.viewmodel.BodyMeasurements
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AvatarBodyAnalysisTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Before @After fun clearFixtures() {
        app.getSharedPreferences("auth", 0).edit().clear().commit()
        AuthStore.load(app)
        AvatarProfileStore.clear(app)
        ManualBodyStore.clear(app)
        BodyAnalysisSessionStore.clear()
    }

    @Test fun measuredReferencesDriveClassificationAndSizeComparison() {
        val result = AvatarBodyAnalysis.analyze(avatar(chest = 96f, waist = 68f, hip = 99f))
        assertEquals(CircumferenceShape.DEFINED_WAIST, result.shape)
        assertEquals(-3f, result.chestHipDifference!!, 0f)
        assertEquals(41.2f, result.measurements[BodyDimension.SHOULDER_WIDTH]!!.cm, 0f)
        val resized = avatar(chest = 96f, waist = 88f, hip = 112f, shoulder = 44f)
        val changed = AvatarBodyAnalysis.analyze(resized)
        assertEquals(CircumferenceShape.HIP_DOMINANT, changed.shape)
        assertEquals(88f, changed.measurements[BodyDimension.WAIST_CIRCUMFERENCE]!!.cm, 0f)
        assertEquals(44f, changed.measurements[BodyDimension.SHOULDER_WIDTH]!!.cm, 0f)
        assertEquals(112f, changed.measurements[BodyDimension.HIP_CIRCUMFERENCE]!!.cm, 0f)
        val product = GarmentSizeRow(brand = "fixture", category = "하의", sizeLabel = "M", shoulderCm = null,
            measureType = "둘레", sourceUrl = "https://example.test/chart", waistCm = 92f, hipCm = 118f)
        assertEquals(listOf(88f, 112f), MeasuredSizeRecommendation
            .suggest(changed.measurements, listOf(product), FitPreference.REGULAR)
            .suggestions.single().evidence.map { it.bodyCm })
        assertNotNull(BodyProfileRepository.current(null, null, resized))
    }

    @Test fun missingAndInvalidInputsNeverBecomeATypeOrAFallbackMeasurement() {
        val inferred = checkNotNull(AvatarCentimeters.generate(170f, 65f, 10))
        val reference = inferred.copy(references = mapOf(
            AvatarPart.SHOULDER to AvatarCm(42f, AvatarValueOrigin.PHOTO),
            AvatarPart.CHEST to AvatarCm(89f, AvatarValueOrigin.MANUAL),
            AvatarPart.WAIST to AvatarCm(76f, AvatarValueOrigin.ADJUSTED)),
            photoFrontWidths = mapOf("hip" to 32f))
        val profile = AvatarProfile(170f, 65f, centimeters = reference)
        val result = AvatarBodyAnalysis.analyze(profile)
        assertNull(result.shape)
        assertNull(result.measurements[BodyDimension.SHOULDER_WIDTH])
        assertNull(result.measurements[BodyDimension.HIP_CIRCUMFERENCE])
        assertNull(result.measurements[BodyDimension.WAIST_CIRCUMFERENCE])
        assertEquals(listOf(BodyDimension.WAIST_CIRCUMFERENCE, BodyDimension.HIP_CIRCUMFERENCE), result.missing)
        assertTrue(AvatarBodyAnalysis.analyze(profile.copy(heightCm = Float.NaN)).measurements.values.isEmpty())
        assertTrue(AvatarBodyAnalysis.analyze(AvatarProfile(170f, 65f, centimeters = inferred))
            .measurements.values.isEmpty())
        assertNull(AvatarBodyAnalysis.analyze(avatar(100f, 65f, 108f)).shape)
        assertNull(AvatarBodyAnalysis.analyze(avatar(100f, 80f, 100f)).shape)
    }

    @Test fun persistedAvatarRecomputesAndDoesNotWritePhotoOrOriginalMeasurements() {
        val photo = BodyMeasurements(170f, 40f, 28f, 37f, 80f, 46f, .1f, 1700f, 1L)
        BodyAnalysisSessionStore.update(photo)
        val original = ManualBodyMeasurements(170f, 40f, 94f, 72f, 99f, null, null, 2)
        assertTrue(ManualBodyStore.update(app, original, true))
        val initial = avatar(96f, 68f, 99f)
        assertTrue(AvatarProfileStore.save(app, initial))
        AvatarProfileStore.load(app)
        assertEquals(CircumferenceShape.DEFINED_WAIST, AvatarBodyAnalysis.analyze(AvatarProfileStore.current.value!!).shape)
        assertTrue(AvatarProfileStore.save(app, avatar(96f, 88f, 112f, 44f)))
        AvatarProfileStore.load(app)
        assertEquals(CircumferenceShape.HIP_DOMINANT, AvatarBodyAnalysis.analyze(AvatarProfileStore.current.value!!).shape)
        assertEquals(original, ManualBodyStore.current.value)
        assertEquals(photo, BodyAnalysisSessionStore.measurements.value)
    }

    @Test fun unverifiedLoginCannotReadOrOverwriteGuestMeasurements() {
        val guest = avatar(96f, 68f, 99f)
        assertTrue(AvatarProfileStore.save(app, guest))
        val prefs = app.getSharedPreferences("auth", 0)
        prefs.edit().putString("mode", "SIGNED_IN").putBoolean("bearer_v2", true).commit()
        AuthStore.load(app)
        AvatarProfileStore.load(app)
        assertNull(AvatarProfileStore.current.value)
        assertFalse(AvatarProfileStore.save(app, avatar(96f, 88f, 112f)))
        assertNotEquals(BodyMeasurementScope.resolve(AuthStore.Mode.SIGNED_IN, false, "fixture-A", "https://test"),
            BodyMeasurementScope.resolve(AuthStore.Mode.SIGNED_IN, false, "fixture-B", "https://test"))
        assertNull(BodyMeasurementScope.resolve(AuthStore.Mode.SIGNED_IN, true, "fixture-A", "https://test"))
        assertTrue(AuthStore.continueAsGuest(app))
        AvatarProfileStore.load(app)
        assertEquals(guest, AvatarProfileStore.current.value)
    }

    private fun avatar(chest: Float, waist: Float, hip: Float, shoulder: Float = 41.2f): AvatarProfile {
        val cm = checkNotNull(AvatarCentimeters.generate(170f, 65f, 10))
        return AvatarProfile(170f, 65f, centimeters = cm.copy(references = mapOf(
            AvatarPart.SHOULDER to AvatarCm(shoulder, AvatarValueOrigin.MANUAL),
            AvatarPart.CHEST to AvatarCm(chest, AvatarValueOrigin.MANUAL),
            AvatarPart.WAIST to AvatarCm(waist, AvatarValueOrigin.MANUAL),
            AvatarPart.HIP to AvatarCm(hip, AvatarValueOrigin.MANUAL),
            AvatarPart.LEG to AvatarCm(80f, AvatarValueOrigin.MANUAL),
            AvatarPart.TORSO to AvatarCm(46f, AvatarValueOrigin.MANUAL))))
    }
}
