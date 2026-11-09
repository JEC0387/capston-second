package com.jeiu.fashionproject

import android.app.Application
import com.jeiu.fashionproject.data.ManualBodyMeasurements
import com.jeiu.fashionproject.data.ManualBodyStore
import com.jeiu.fashionproject.recommend.BodyProfileAnalyzer
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ManualBodyMeasurementsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val valid = ManualBodyMeasurements(170f, 40f, 90f, 75f, 95f, 88f, 52f, 1L)

    @Before fun storageFixtureMustNotStartProductionNetworkWorkers() {
        assertEquals(Application::class.java, context.javaClass)
    }

    @After fun cleanup() { ManualBodyStore.clear(context) }

    @Test fun rejectsInvalidNumbersAndMissingMeasurements() {
        assertTrue(valid.isValid())
        assertFalse(valid.copy(waistCircumferenceCm = Float.NaN).isValid())
        assertFalse(valid.copy(heightCm = 0f).isValid())
        assertFalse(valid.copy(legLengthCm = 120f).isValid())
        assertFalse(valid.copy(shoulderWidthCm = null, chestCircumferenceCm = null,
            waistCircumferenceCm = null, hipCircumferenceCm = null,
            legLengthCm = null, torsoLengthCm = null).isValid())
    }

    @Test fun optInPersistsNumbersButOptOutOnlyKeepsSession() {
        assertTrue(ManualBodyStore.update(context, valid, true))
        assertTrue(ManualBodyStore.saved.value)
        assertEquals(valid, ManualBodyStore.current.value)
        ManualBodyStore.load(context)
        assertEquals(valid, ManualBodyStore.current.value)
        assertTrue(ManualBodyStore.update(context, valid, false))
        assertFalse(ManualBodyStore.saved.value)
        assertFalse(context.getSharedPreferences("manual_body_measurements", 0).contains("height"))
        assertEquals(valid, ManualBodyStore.current.value)
        ManualBodyStore.clear(context)
        assertNull(ManualBodyStore.current.value)
    }

    @Test fun manualLengthsUseExistingProfileDefinitionWithoutCircumferenceConversion() {
        assertEquals(BodyProfileAnalyzer.fromLengths(170f, 88f, 52f),
            BodyProfileAnalyzer.fromLengths(valid.heightCm, valid.legLengthCm, valid.torsoLengthCm))
        assertNull(BodyProfileAnalyzer.fromLengths(valid.heightCm, null, valid.torsoLengthCm))
    }

    @Test fun invalidSavedMeasurementsArePreservedForExplicitCorrection() {
        val prefs=context.getSharedPreferences("manual_body_measurements",0)
        prefs.edit().putBoolean("save",true).putFloat("height",Float.NaN).putLong("entered_at",1).commit()
        ManualBodyStore.load(context)
        assertNull(ManualBodyStore.current.value)
        assertNotNull(ManualBodyStore.issue.value)
        assertTrue(prefs.contains("height"))
        assertTrue(prefs.getFloat("height",0f).isNaN())
        assertTrue(ManualBodyStore.update(context,valid,true))
        assertNull(ManualBodyStore.issue.value)
    }
}
