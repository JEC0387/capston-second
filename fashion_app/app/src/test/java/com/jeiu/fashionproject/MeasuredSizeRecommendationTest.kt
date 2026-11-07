package com.jeiu.fashionproject

import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.recommend.*
import com.jeiu.fashionproject.ui.viewmodel.BodyMeasurements
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.app.Application

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class MeasuredSizeRecommendationTest {
    private val body=BodyDimensions.resolve(ManualBodyMeasurements(170f,40f,90f,75f,95f,88f,52f,1),null)
    private fun row(size:String,shoulder:Float,chest:Float,source:String="https://example.test/chart")=
        GarmentSizeRow("TEST","상의",size,shoulder,"단면",source,chestCm=chest)
    @Test fun photoWidthsDoNotSubstituteForRequiredCircumferences() {
        val photo=BodyDimensions.resolve(null,BodyMeasurements(170f,38f,30f,37f,85f,50f,.5f,340f,1))
        val result=MeasuredSizeRecommendation.suggest(photo,listOf(row("M",43f,49f)),FitPreference.REGULAR)
        assertTrue(result.suggestions.isEmpty());assertTrue(BodyDimension.CHEST_CIRCUMFERENCE in result.missing)
    }
    @Test fun flatAndCircumferenceChartsAgreeAndShouldersAreNotDoubled() {
        val a=row("M",43f,49f)
        val flat=MeasuredSizeRecommendation.suggest(body,listOf(a),FitPreference.REGULAR).suggestions.single()
        val circle=MeasuredSizeRecommendation.suggest(body,listOf(a.copy(measureType="둘레",chestCm=98f)),FitPreference.REGULAR).suggestions.single()
        assertEquals(flat.evidence,circle.evidence)
        assertEquals(43f,flat.evidence.first().garmentCm)
        assertEquals(98f,flat.evidence.last().garmentCm)
    }
    @Test fun fitPreferenceChangesTheExplainedRecommendation() {
        val rows=listOf(row("S",41f,46f),row("M",43f,49f),row("L",46f,54f))
        assertEquals("S",MeasuredSizeRecommendation.suggest(body,rows,FitPreference.SNUG).suggestions.single().row.sizeLabel)
        assertEquals("M",MeasuredSizeRecommendation.suggest(body,rows,FitPreference.REGULAR).suggestions.single().row.sizeLabel)
        assertEquals("L",MeasuredSizeRecommendation.suggest(body,rows,FitPreference.RELAXED).suggestions.single().row.sizeLabel)
    }
    @Test fun sourceUnitsAndMissingMeasurementsCannotProduceFakeRecommendations() {
        for(r in listOf(row("M",43f,49f,""),row("M",43f,49f,"http://example.test"),
            row("M",43f,49f).copy(measureType="unknown"),row("M",43f,49f).copy(chestCm=null),row("M",43f,Float.NaN)))
            assertTrue(MeasuredSizeRecommendation.suggest(body,listOf(r),FitPreference.REGULAR).suggestions.isEmpty())
        assertTrue(MeasuredSizeRecommendation.suggest(body,emptyList(),FitPreference.REGULAR).suggestions.isEmpty())
    }
    @Test fun productsAndSourcesAreNeverMergedJustBecauseTheBrandMatches() {
        val rows=listOf(row("M",43f,49f).copy(productKey="a"),row("L",44f,50f).copy(productKey="b"))
        val result=MeasuredSizeRecommendation.suggest(body,rows,FitPreference.REGULAR)
        assertEquals(2,result.suggestions.size)
    }
    @Test fun bottomsCompareBothWaistAndHipAndRejectClearlyTooLargeRows() {
        val lower=GarmentSizeRow("TEST","하의","M",null,"단면","https://example.test/pants",waistCm=40f,hipCm=52f)
        val evidence=MeasuredSizeRecommendation.suggest(body,listOf(lower),FitPreference.REGULAR).suggestions.single().evidence
        assertEquals(listOf(80f,104f),evidence.map { it.garmentCm })
        assertTrue(MeasuredSizeRecommendation.suggest(body,listOf(lower.copy(waistCm=90f)),FitPreference.REGULAR).suggestions.isEmpty())
    }
    @Test fun parserPreservesRealChartFieldsWithoutInventingMissingData() {
        val rows=SizeCharts.parse("""{"rows":[{"brand":"TEST","category":"하의","sizeLabel":"M","measureType":"단면","waistCm":40,"hipCm":52,"lengthCm":100,"sourceUrl":"https://example.test/chart"}]}""")
        assertEquals(40f,rows.single().waistCm);assertEquals(100f,rows.single().lengthCm)
        assertNull(rows.single().productKey);assertNull(rows.single().chestCm)
    }
}
