package com.jeiu.fashionproject

import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.recommend.*
import com.jeiu.fashionproject.ui.viewmodel.BodyMeasurements
import org.junit.Assert.*
import org.junit.Test

class BodyAwareOutfitRecommendationTest {
    private val day = "2026-07-01"
    private fun item(id: String, category: String, sub: String, detail: String = sub) = WardrobeItem(
        id = id, name = id, category = category, subCategory = sub, detailType = detail,
        colorName = "블랙", colorValue = 0xFF222222, imageUri = "", season = "사계절",
        style = "캐주얼", addedAt = WearDates.toCalendar(day).timeInMillis
    )
    private val top = item("top", "상의", "반팔 상의")
    private val shorts = item("shorts", "하의", "반바지")
    private val pants = item("pants", "하의", "긴바지")
    private val shoes = item("shoes", "신발", "운동화")
    private val profile = BodyProfileAnalyzer.fromLengths(170f, 72f, 56f)!!
    private fun outfits(items: List<WardrobeItem>, body: BodyProfile? = null,
                        taste: TasteScores = TasteScores(), situation: Situation? = null) =
        (OutfitEngine.recommend(items, WeatherInfo("맑음", 25, 50, 800), emptyList(),
            mapOf("캐주얼" to 4), situation, today = day, taste = taste,
            bodyProfile = body) as OutfitEngine.Result.Ok).outfits

    @Test fun validMeasurementsProduceLengthRatios() {
        val measurements = BodyMeasurements(170f, 40f, 30f, 37f, 72f, 56f, 0.5f, 340f, 1L)
        assertEquals(72.0 / 170.0, BodyProfileAnalyzer.fromMeasurements(measurements)!!.legToHeightRatio, 0.00001)
        assertEquals(56.0 / 72.0, BodyProfileAnalyzer.fromMeasurements(measurements)!!.torsoToLegRatio, 0.00001)
    }

    @Test fun missingLengthsOrMeasurementsGiveNoProfile() {
        assertNull(BodyProfileAnalyzer.fromMeasurements(null))
        assertNull(BodyProfileAnalyzer.fromLengths(170f, null, 55f))
        assertNull(BodyProfileAnalyzer.fromLengths(170f, 72f, null))
    }

    @Test fun nonFiniteZeroNegativeAndOutOfRangeLengthsAreRejected() {
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -5f, 2f)) {
            assertNull(BodyProfileAnalyzer.fromLengths(170f, bad, 55f))
            assertNull(BodyProfileAnalyzer.fromLengths(170f, 72f, bad))
        }
        assertNull(BodyProfileAnalyzer.fromLengths(Float.NaN, 72f, 55f))
        assertNull(BodyProfileAnalyzer.fromLengths(0f, 72f, 55f))
    }

    @Test fun serverFailureAndSampleRecordsDoNotAffectRecommendations() {
        assertNull(BodyProfileAnalyzer.latestValid(null))
        assertNull(BodyProfileAnalyzer.latestValid(emptyList()))
        assertNull(BodyProfileAnalyzer.latestValid(listOf(record(1L, sample = true))))
        assertEquals(outfits(listOf(top, shorts, shoes)), outfits(listOf(top, shorts, shoes),
            BodyProfileAnalyzer.latestValid(null)))
    }

    @Test fun latestValidRecordSkipsIncompleteAndSampleRecords() {
        val selected = BodyProfileAnalyzer.latestValid(listOf(
            record(3L, leg = null), record(2L, sample = true), record(1L)))
        assertEquals(profile, selected)
    }

    @Test fun explicitBottomLengthOnlyGetsSmallPositiveBonus() {
        assertEquals(BodyAwareOutfitScorer.MAX_BONUS,
            BodyAwareOutfitScorer.evaluate(profile, listOf(top, shorts, shoes)).score, 0.0)
        assertEquals(0.0, BodyAwareOutfitScorer.evaluate(profile, listOf(top, pants, shoes)).score, 0.0)
        assertEquals(0.0, BodyAwareOutfitScorer.evaluate(profile, listOf(top, shoes)).score, 0.0)
        assertEquals(0.0, BodyAwareOutfitScorer.evaluate(null, listOf(top, shorts, shoes)).score, 0.0)
        assertEquals(0.0, BodyAwareOutfitScorer.evaluate(profile,
            listOf(top, item("skirt", "하의", "치마"), shoes)).score, 0.0)
    }

    @Test fun shorterTorsoRatioCanPreferExplicitLongLength() {
        val shorterTorso = BodyProfileAnalyzer.fromLengths(170f, 100f, 50f)!!
        assertEquals(BodyAwareOutfitScorer.MAX_BONUS,
            BodyAwareOutfitScorer.evaluate(shorterTorso, listOf(top, pants, shoes)).score, 0.0)
    }

    @Test fun existingScoreAndReasonsArePreservedWithoutBodyData() {
        val items = listOf(top, shorts, shoes)
        assertEquals(outfits(items), outfits(items, null))
        val base = outfits(items).single()
        val adjusted = outfits(items, profile).single()
        assertEquals(BodyAwareOutfitScorer.MAX_BONUS, adjusted.score - base.score, 0.00001)
        assertEquals(base.reasons, adjusted.reasons.dropLast(1))
        assertTrue(adjusted.reasons.last().startsWith("체형 분석 반영"))
    }

    @Test fun tasteAndBodyBonusesCombineWithoutChangingFilters() {
        val items = listOf(top, shorts, shoes)
        val taste = TasteScores(colors = mapOf("블랙" to 3), ratedCount = 10)
        val baseline = outfits(items, taste = taste).single()
        val adjusted = outfits(items, profile, taste).single()
        assertEquals(BodyAwareOutfitScorer.MAX_BONUS, adjusted.score - baseline.score, 0.00001)
        val noShoe = OutfitEngine.recommend(listOf(top, shorts), WeatherInfo("맑음", 25, 50, 800),
            emptyList(), emptyMap(), today = day, bodyProfile = profile)
        assertTrue(noShoe is OutfitEngine.Result.NotEnough)
        val rainy = OutfitEngine.recommend(listOf(top, shorts, shoes.copy(subCategory = "샌들")),
            WeatherInfo("비", 25, 80, 500), emptyList(), emptyMap(), today = day, bodyProfile = profile)
        assertTrue(rainy is OutfitEngine.Result.NotEnough)
    }

    @Test fun situationAndAllCandidateOutfitsStayAvailable() {
        val items = listOf(top, shorts, pants, shoes)
        val base = outfits(items)
        val adjusted = outfits(items, profile)
        assertEquals(base.map { it.id }.toSet(), adjusted.map { it.id }.toSet())
        val interview = OutfitEngine.recommend(items, WeatherInfo("맑음", 25, 50, 800),
            emptyList(), emptyMap(), Situation.INTERVIEW, today = day, bodyProfile = profile)
        val withoutBody = OutfitEngine.recommend(items, WeatherInfo("맑음", 25, 50, 800),
            emptyList(), emptyMap(), Situation.INTERVIEW, today = day)
        assertEquals(withoutBody::class, interview::class)
    }

    private fun record(at: Long, leg: Float? = 72f, sample: Boolean = false) = BodyRecord(
        id = at.toInt(), bodyType = "", confidence = 80, shoulderToHip = null,
        waistToHip = null, legRatio = null, heightCm = 170f, shoulderCm = 40f,
        waistCm = 30f, hipCm = 37f, legCm = leg, measuredAt = at,
        torsoCm = 56f, isSample = sample
    )
}
