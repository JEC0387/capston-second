package com.jeiu.fashionproject

import com.jeiu.fashionproject.ui.viewmodel.*
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.recommend.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Test

class BodyAnalysisIntegrationSafetyTest {
    private val cm=BodyMeasurements(170f,40f,30f,40f,72f,56f,.5f,340f,1L)
    private fun result(m:BodyMeasurements=cm) = BodyAnalysisResult(
        "모래시계형",1f,.75f,1f,m.legLengthCm?.div(m.heightCm),emptyList(),emptyList(),0,
        1f,.75f,m)
    @After fun clear()=BodyAnalysisSessionStore.clear()
    @Test fun newRequestRejectsOldSuccessAndFailure() {
        val g=AnalysisRequestGate();val old=g.begin();val latest=g.begin();var state="loading"
        assertFalse(g.publish(old){state="old success"})
        assertFalse(g.publish(old){state="old error"})
        assertTrue(g.publish(latest){state="latest success"})
        assertEquals("latest success",state)
    }
    @Test fun cancellationCannotBeReversedByLateCompletion() {
        val g=AnalysisRequestGate();val id=g.begin();g.invalidate();var stores=0
        assertFalse(g.publish(id){stores++});assertEquals(0,stores)
    }
    @Test fun onlyOneTerminalCallbackCanPublish() {
        val g=AnalysisRequestGate();val id=g.begin();var value=""
        assertTrue(g.publish(id){value="success"})
        assertFalse(g.publish(id){value="late failure"})
        assertTrue(g.owns(id));assertFalse(g.isCurrent(id));assertEquals("success",value)
    }
    @Test fun nativeDelayDoesNotAllowPreviousResultToOverwriteNewOne()=runBlocking {
        val g=AnalysisRequestGate();val first=g.begin();val release=CompletableDeferred<Unit>();var state=""
        val old=launch { release.await();g.publish(first){state="old"} }
        yield();val current=g.begin();g.publish(current){state="new"};release.complete(Unit);old.join()
        assertEquals("new",state)
    }
    @Test fun repeatedCancellationsOnlyKeepLatestIdentity() {
        val g=AnalysisRequestGate();val ids=List(50){g.begin()};var publications=0
        ids.forEach{g.publish(it){publications++}}
        assertEquals(1,publications)
    }
    @Test fun severityChecksRejectTinyDarkBrightAndEmptyContrastInputs() {
        assertEquals(ImageInputIssue.RESOLUTION,ImageInputQuality.evaluate(100,200,IntArray(64){100}))
        assertEquals(ImageInputIssue.DARK,ImageInputQuality.evaluate(800,1200,IntArray(64){2}))
        assertEquals(ImageInputIssue.BRIGHT,ImageInputQuality.evaluate(800,1200,IntArray(64){254}))
        assertEquals(ImageInputIssue.CONTRAST,ImageInputQuality.evaluate(800,1200,IntArray(64){100}))
    }
    @Test fun usableLumaAndPortraitAspectRatiosAreNotAutomaticallyRejected() {
        for(size in listOf(800 to 1200,1200 to 800,500 to 2000))
            assertNull(ImageInputQuality.evaluate(size.first,size.second,IntArray(256){it}))
    }
    @Test fun noExternalTransferWithoutExplicitRequestAndConfirmation() {
        val gate=PhotoTransferConsent()
        assertFalse(gate.consume("photo"));assertFalse(gate.confirm("photo"))
        gate.request("photo");assertFalse(gate.consume("photo"))
    }
    @Test fun approvalIsForOnePhotoAndOneBatchOnly() {
        val gate=PhotoTransferConsent();gate.request("photo");assertTrue(gate.confirm("photo"))
        assertTrue(gate.consume("photo"));assertFalse(gate.consume("photo"))
    }
    @Test fun differentPhotoCannotReuseApproval() {
        val gate=PhotoTransferConsent();gate.request("one");assertTrue(gate.confirm("one"))
        assertFalse(gate.consume("two"));assertFalse(gate.consume("one"))
    }
    @Test fun decliningTransferDoesNotEraseValidBodySession() {
        BodyAnalysisSessionStore.update(cm)
        val gate=PhotoTransferConsent();gate.request("photo");gate.cancel()
        assertFalse(gate.confirm("photo"));assertFalse(gate.consume("photo"))
        assertEquals(cm,BodyAnalysisSessionStore.measurements.value)
    }
    @Test fun replacingPhotoInvalidatesPendingApproval() {
        val gate=PhotoTransferConsent();gate.request("old");gate.request("new")
        assertFalse(gate.confirm("old"));assertTrue(gate.confirm("new"));assertTrue(gate.consume("new"))
    }
    @Test fun calibratedMeasurementAndCoherentClassificationCanBeStored() {
        assertTrue(BodyMeasurementCalculator.isValid(cm));assertTrue(BodyAnalysisPersistence.mayStore(result()))
    }
    @Test fun mismatchedTypeCannotBeStored() {
        assertFalse(BodyAnalysisPersistence.mayStore(result().copy(bodyTypeKo="역삼각형")))
        assertFalse(BodyAnalysisPersistence.mayStore(result().copy(bodyTypeKo="판정 불가")))
    }
    @Test fun missingMeasurementsAndIncorrectConfidenceCannotBeStored() {
        assertFalse(BodyAnalysisPersistence.mayStore(result().copy(measurements=null)))
        assertFalse(BodyAnalysisPersistence.mayStore(result().copy(confidence=101)))
        assertFalse(BodyAnalysisPersistence.mayStore(result().copy(confidence=-1)))
    }
    @Test fun fabricatedOrClampedRatiosCannotBeStored() {
        assertFalse(BodyAnalysisPersistence.mayStore(result().copy(legRatio=.95f)))
        assertFalse(BodyAnalysisPersistence.mayStore(result().copy(shoulderToHip=1.5f)))
        assertFalse(BodyAnalysisPersistence.mayStore(result().copy(waistRatio=Float.NaN)))
    }
    @Test fun inconsistentCalibrationNeverEntersSessionOrProfile() {
        val bad=cm.copy(cmPerPixel=.4f)
        assertFalse(BodyMeasurementCalculator.isValid(bad));BodyAnalysisSessionStore.update(bad)
        assertNull(BodyAnalysisSessionStore.measurements.value);assertNull(BodyProfileAnalyzer.fromMeasurements(bad))
        assertTrue(BodySizeRecommendation.suggest(bad,listOf(GarmentSizeRow("a","상의","M",45f,"단면"))).isEmpty())
    }
    @Test fun invalidWidthOrLengthCannotCreateCurrentRecommendations() {
        for(bad in listOf(cm.copy(waistWidthCm=Float.NaN),cm.copy(hipWidthCm=0f),cm.copy(legLengthCm=Float.POSITIVE_INFINITY))) {
            assertFalse(BodyMeasurementCalculator.isValid(bad));assertNull(BodyProfileAnalyzer.fromMeasurements(bad))
            assertFalse(BodyAnalysisPersistence.mayStore(result(bad)))
        }
    }
    @Test fun boundaryClassificationDoesNotCreateDefinitiveStoredType() {
        val borderline=cm.copy(shoulderWidthCm=43.2f)
        assertFalse(BodyAnalysisPersistence.mayStore(result(borderline).copy(bodyTypeKo="역삼각형")))
    }
    @Test fun storageDeclinedStillUsesValidSessionAndNoSaveCallback() {
        var calls=0;BodyAnalysisCompletion.complete(cm,false){calls++}
        assertEquals(0,calls);assertNotNull(BodyProfileAnalyzer.fromMeasurements(BodyAnalysisSessionStore.measurements.value))
    }
    @Test fun storageFailureIsNotAnAnalysisFailure() {
        BodyAnalysisCompletion.complete(cm,true){error("offline")}
        assertEquals(cm,BodyAnalysisSessionStore.measurements.value)
    }
    @Test fun invalidNewInputDoesNotDeletePreviousValidRecord() {
        BodyAnalysisSessionStore.update(cm);var calls=0
        BodyAnalysisCompletion.complete(cm.copy(shoulderWidthCm=Float.NaN),true){calls++}
        assertEquals(cm,BodyAnalysisSessionStore.measurements.value);assertEquals(0,calls)
    }
    @Test fun generalOutfitFallbackDoesNotRequireBodyData() {
        val all=listOf("상의","하의","신발").mapIndexed{n,c->WardrobeItem(id="$n",name=c,category=c,
            colorName="블랙",colorValue=0xFF000000,imageUri="",addedAt=WearDates.toCalendar("2026-07-01").timeInMillis)}
        val out=OutfitEngine.recommend(all,WeatherInfo("맑음",25,50,800),emptyList(),emptyMap(),
            today="2026-07-01",bodyProfile=BodyProfileAnalyzer.fromMeasurements(null))
        assertTrue(out is OutfitEngine.Result.Ok)
    }
    @Test fun legacyClampedLegRatiosAreNotPlottedAsValidNewMeasurements() {
        val record=BodyRecord(1,"직사각형",80,1f,.8f,.95f,170f,40f,30f,40f,72f,1L)
        assertNull(BodyMetric.LEG.read(record))
        assertNotNull(BodyMetric.LEG.read(record.copy(legRatio=72f/170f)))
    }
    @Test fun historyDoesNotClaimObservedDeltaIsEstablishedPhysicalChange() {
        assertFalse(BodyHistoryAnalyzer.change(BodyMetric.WAIST_CM,Float.NaN,30f).significant)
        assertFalse(BodyHistoryAnalyzer.change(BodyMetric.WAIST_HIP,0f,.8f).significant)
    }
}
