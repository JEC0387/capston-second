package com.jeiu.fashionproject

import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.recommend.*
import com.jeiu.fashionproject.ui.viewmodel.BodyMeasurements
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.After
import org.junit.Test

class BodyDataPrivacyTest {
    private val cm = BodyMeasurements(170f, 38f, 30f, 37f, 72f, 56f, 0.5f, 340f, 1L)

    @After fun clearSession() = BodyAnalysisSessionStore.clear()

    @Test fun saveConsentKeepsSessionAndAttemptsPersistence() {
        var attempts = 0
        BodyAnalysisCompletion.complete(cm, true) { attempts++ }
        assertEquals(cm, BodyAnalysisSessionStore.measurements.value)
        assertEquals(1, attempts)
    }

    @Test fun declinedStorageStillKeepsSessionAndSkipsPersistence() {
        var attempts = 0
        BodyAnalysisCompletion.complete(cm, false) { attempts++ }
        assertEquals(cm, BodyAnalysisSessionStore.measurements.value)
        assertEquals(0, attempts)
    }

    @Test fun failedPersistenceCannotEraseSession() {
        BodyAnalysisCompletion.complete(cm, true) { error("server unavailable") }
        assertEquals(cm, BodyAnalysisSessionStore.measurements.value)
    }

    @Test fun sessionSupportsSizeRecommendationWithoutSavedRecord() {
        BodyAnalysisCompletion.complete(cm, false) { error("must not persist") }
        val rows = listOf(GarmentSizeRow("시연용", "상의", "M", 41f, "단면"))
        assertEquals("M", BodySizeRecommendation.suggest(
            BodyAnalysisSessionStore.measurements.value!!, rows).single().sizeLabel)
    }

    @Test fun sessionWinsOverMissingOrOlderSavedRecord() {
        BodyAnalysisSessionStore.update(cm)
        val fromSession = BodyProfileAnalyzer.fromMeasurements(cm)
        assertEquals(fromSession, BodyProfileRepository.selectLatest(
            BodyAnalysisSessionStore.measurements.value, null))
        assertEquals(fromSession, BodyProfileRepository.selectLatest(
            BodyAnalysisSessionStore.measurements.value, listOf(record(1L, 100f, 50f))))
    }

    @Test fun repositoryUsesSessionWithoutStorageConsentOrServerCall() = runBlocking {
        BodyAnalysisSessionStore.update(cm)
        assertEquals(BodyProfileAnalyzer.fromMeasurements(cm), BodyProfileRepository.loadLatest(false))
        BodyAnalysisSessionStore.clear()
        assertNull(BodyProfileRepository.loadLatest(false))
    }

    @Test fun savedRecordIsUsedOnlyWhenSessionIsAbsent() {
        assertEquals(BodyProfileAnalyzer.fromLengths(170f, 100f, 50f),
            BodyProfileRepository.selectLatest(null, listOf(record(1L, 100f, 50f))))
        assertNull(BodyProfileRepository.selectLatest(null, null))
        assertNull(BodyProfileRepository.selectLatest(null, emptyList()))
    }

    @Test fun clearingSessionRemovesMeasurementsFromSubsequentRecommendations() {
        BodyAnalysisSessionStore.update(cm)
        BodyAnalysisSessionStore.clear()
        assertNull(BodyAnalysisSessionStore.measurements.value)
        assertNull(BodyProfileRepository.selectLatest(BodyAnalysisSessionStore.measurements.value, null))
    }

    @Test fun invalidMeasurementDoesNotEnterSession() {
        BodyAnalysisSessionStore.update(cm.copy(legLengthCm = Float.NaN))
        assertNull(BodyAnalysisSessionStore.measurements.value)
        BodyAnalysisSessionStore.update(cm.copy(shoulderWidthCm = 0f))
        assertNull(BodyAnalysisSessionStore.measurements.value)
    }

    @Test fun sessionProfileAddsOnlyExistingSmallBodyBonus() {
        BodyAnalysisCompletion.complete(cm, false) { error("must not persist") }
        val top = item("top", "상의", "반팔 상의")
        val shorts = item("shorts", "하의", "반바지")
        val shoes = item("shoes", "신발", "운동화")
        val items = listOf(top, shorts, shoes)
        fun recommend(body: BodyProfile?) = (OutfitEngine.recommend(items, WeatherInfo("맑음", 25, 50, 800),
            emptyList(), emptyMap(), today = "2026-07-01", bodyProfile = body) as OutfitEngine.Result.Ok).outfits.single()
        val base = recommend(null)
        val withSession = recommend(BodyProfileRepository.selectLatest(BodyAnalysisSessionStore.measurements.value, null))
        assertEquals(BodyAwareOutfitScorer.MAX_BONUS, withSession.score - base.score, 0.00001)
        BodyAnalysisSessionStore.clear()
        assertEquals(base, recommend(BodyProfileRepository.selectLatest(BodyAnalysisSessionStore.measurements.value, null)))
    }

    private fun item(id: String, category: String, sub: String) = WardrobeItem(
        id = id, name = id, category = category, subCategory = sub, detailType = sub,
        colorName = "블랙", colorValue = 0xFF222222, imageUri = "", season = "사계절",
        style = "캐주얼", addedAt = WearDates.toCalendar("2026-07-01").timeInMillis
    )

    private fun record(at: Long, leg: Float, torso: Float) = BodyRecord(
        id = at.toInt(), bodyType = "", confidence = 80, shoulderToHip = null,
        waistToHip = null, legRatio = null, heightCm = 170f, shoulderCm = 40f,
        waistCm = 30f, hipCm = 37f, legCm = leg, measuredAt = at,
        torsoCm = torso
    )
}
