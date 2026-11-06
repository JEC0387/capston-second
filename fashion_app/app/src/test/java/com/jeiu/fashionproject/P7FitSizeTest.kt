package com.jeiu.fashionproject

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.geometry.Size
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.recommend.*
import com.jeiu.fashionproject.ui.screens.*
import com.jeiu.fashionproject.ui.viewmodel.BodyMeasurements
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class P7FitSizeTest {
    private val manual=ManualBodyMeasurements(170f,40f,90f,75f,95f,82f,46f,1)
    private val body=BodyDimensions.resolve(manual,null)
    private val dress=GarmentSizeRow("SYNTHETIC","원피스","M",43f,"단면","https://example.test/dress",chestCm=49f,
        waistCm=40f,hipCm=52f,productKey="test-dress")
    @Test fun malformedManualStorageIsPreservedAndNotImported() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val prefs=app.getSharedPreferences("manual_body_measurements",0)
        prefs.edit().clear().putBoolean("save",true).putString("height","damaged-field-type").commit()
        try {
            ManualBodyStore.load(app)
            assertNull(ManualBodyStore.current.value);assertNotNull(ManualBodyStore.issue.value)
            assertEquals("damaged-field-type",prefs.getString("height",null))
            assertNull(AvatarMeasurementImport.manual(ManualBodyStore.current.value))
        } finally {prefs.edit().clear().commit();ManualBodyStore.clear(app)}
    }
    @Test fun dressUsesAllFourDimensionsAndTraceableUserOrigins() {
        val result=MeasuredSizeRecommendation.suggest(body,listOf(dress),FitPreference.REGULAR).suggestions.single()
        assertEquals(4,result.evidence.size)
        assertEquals(listOf(3f,8f,5f,9f),result.evidence.map {it.easeCm})
        assertTrue(result.evidence.all {it.origin==MeasurementOrigin.USER_ENTERED})
    }
    @Test fun photosAndBmiNeverSupplyMissingCircumferencesForDress() {
        val photo=BodyDimensions.resolve(null,BodyMeasurements(170f,40f,28f,37f,82f,46f,.1f,1700f,1))
        val result=MeasuredSizeRecommendation.suggest(photo,listOf(dress),FitPreference.REGULAR)
        assertTrue(result.suggestions.isEmpty())
        assertTrue(result.missing.containsAll(listOf(BodyDimension.CHEST_CIRCUMFERENCE,BodyDimension.WAIST_CIRCUMFERENCE,BodyDimension.HIP_CIRCUMFERENCE)))
        assertTrue(MeasuredSizeRecommendation.suggest(BodyDimensions.resolve(null,null),listOf(dress),FitPreference.REGULAR).suggestions.isEmpty())
    }
    @Test fun missingProductMeasurementsAreSeparatedFromUserInput() {
        val result=MeasuredSizeRecommendation.suggest(body,listOf(dress.copy(hipCm=null)),FitPreference.REGULAR)
        assertTrue(result.missing.isEmpty());assertEquals(setOf(BodyDimension.HIP_CIRCUMFERENCE),result.missingGarmentDimensions)
        assertTrue(result.suggestions.isEmpty())
    }
    @Test fun fitUsesActualSharedJointsAndChestChangesOverlayWidth() {
        val garment=WardrobeItem(id="synthetic",name="test",category="상의",colorName="",colorValue=0,imageUri="")
        val base=AvatarProfile(170f,65f,shoulder=.8f)
        val first=fitAvatar(FitSlot.TOP,garment,1.2f,Size(400f,600f),base)
        val wider=fitAvatar(FitSlot.TOP,garment,1.2f,Size(400f,600f),base.copy(chest=1.2f))
        assertTrue(wider.width>first.width)
        val measured=AvatarMeasurementImport.manual(manual)!!
        assertTrue(fitAvatar(FitSlot.TOP,garment,1.2f,Size(400f,600f),measured).width>0)
    }
    @Test fun armRootsOverlapShoulderSurfaceInsteadOfFloatingOutside() {
        val parameters=AvatarProfile(170f,65f).parameters();val h=parameters.heightCm/100f
        val data=BodyAvatarMesh.build(parameters).toList().chunked(9)
        val y=parameters.joints().shoulderY-h/2
        val root=data.filter {kotlin.math.abs(it[1]-y)<.00001f && it[6]==.76f && it[0]>0}
        val shoulder=parameters.shoulderCm/200f
        assertTrue(root.minOf {it[0]}<shoulder && root.maxOf {it[0]}>shoulder)
    }
    @Test fun everyPositionHasAMirroredCounterpart() {
        val p=AvatarMeasurementImport.manual(manual)!!.copy(chest=1.1f,torso=.9f,leg=1.1f)
        val positions=BodyAvatarMesh.build(p.parameters(),thighScale=1.15f).toList().chunked(9)
        fun key(x:Float,y:Float,z:Float)=listOf(x,y,z).map {kotlin.math.round(it*1000).toInt()}
        val set=positions.map {key(it[0],it[1],it[2])}.toSet()
        assertTrue(positions.all {key(-it[0],it[1],it[2]) in set})
    }
    @Test fun hipToAnkleAndShoulderToHipUseTheSameDefinitionsAsInputs() {
        val parameters=AvatarMeasurementImport.manual(manual)!!.parameters();val h=parameters.heightCm/100f
        val joints=parameters.joints()
        assertEquals(parameters.legCm/100f,joints.hipY-h*.04f,.0001f)
        assertEquals(parameters.torsoCm/100f,joints.shoulderY-joints.hipY,.0001f)
        val ankleY=h*.04f-h/2
        assertTrue(BodyAvatarMesh.build(parameters).toList().chunked(9).any {kotlin.math.abs(it[1]-ankleY)<.0001f})
    }
}
