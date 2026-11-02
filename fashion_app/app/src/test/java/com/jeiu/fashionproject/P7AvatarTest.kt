package com.jeiu.fashionproject

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.ui.viewmodel.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class P7AvatarTest {
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private val prefs get()=app.getSharedPreferences("avatar_visual_profile_v1",0)
    private val photo=BodyMeasurements(170f,40f,28f,37f,80f,46f,.1f,1700f,1L)
    private val manual=ManualBodyMeasurements(170f,42f,94f,78f,98f,82f,47f,2L)
    @Before @After fun reset() {prefs.edit().clear().commit();AvatarProfileStore.load(app)}

    @Test fun photoImportRetainsWidthsWithoutInventingCircumferences() {
        val value=AvatarMeasurementImport.photo(photo)!!
        assertEquals(AvatarSource.PHOTO,value.source)
        assertEquals(28f,value.applied[BodyDimension.WAIST_FRONT_WIDTH]!!.cm)
        assertNull(value.applied[BodyDimension.WAIST_CIRCUMFERENCE])
        assertEquals(28f,value.parameters().waistWidthCm,.001f)
        assertNull(AvatarProfileStore.current.value)
    }
    @Test fun directInputAndVisualEllipseRemainSeparateAndRestoreExactly() {
        val value=AvatarMeasurementImport.manual(manual)!!.copy(chest=1.1f,torso=.9f,thigh=1.1f)
        assertTrue(AvatarProfileStore.save(app,value));AvatarProfileStore.load(app)
        assertEquals(value,AvatarProfileStore.current.value)
        assertEquals(78f,value.applied[BodyDimension.WAIST_CIRCUMFERENCE]!!.cm)
        assertTrue(value.parameters().notes.any {it.contains("깊이")})
    }
    @Test fun absentOrInvalidMeasurementsAreNotImported() {
        assertNull(AvatarMeasurementImport.photo(null))
        assertNull(AvatarMeasurementImport.photo(photo.copy(cmPerPixel=2f)))
        assertNull(AvatarMeasurementImport.manual(manual.copy(waistCircumferenceCm=Float.NaN)))
    }
    @Test fun storedSourceCannotClaimPhotoCircumferenceOrOutOfRangeManualValues() {
        val value=AvatarMeasurementImport.photo(photo)!!
        assertFalse(value.copy(applied=value.applied+(BodyDimension.WAIST_CIRCUMFERENCE to DimensionValue(78f,MeasurementOrigin.PHOTO_ESTIMATE,1))).valid())
        val entered=AvatarMeasurementImport.manual(manual)!!
        assertFalse(entered.copy(applied=entered.applied+(BodyDimension.WAIST_CIRCUMFERENCE to DimensionValue(900f,MeasurementOrigin.USER_ENTERED,1))).valid())
        assertFalse(entered.copy(heightCm=180f).valid())
    }
    @Test fun readingLegacyIsNonDestructiveAndFirstApprovedSavePreservesOriginalBytes() {
        val raw="""{"schema":1,"height":172,"weight":70,"shoulder":1.1,"waist":0.9,"hip":1,"thigh":1.05,"leg":1.1}"""
        prefs.edit().putString("profile",raw).commit();AvatarProfileStore.load(app)
        val previous=AvatarProfileStore.current.value!!
        assertEquals(AvatarSource.BMI,previous.source);assertEquals(raw,prefs.getString("profile",null))
        assertTrue(AvatarProfileStore.save(app,AvatarMeasurementImport.manual(manual,previous)!!))
        assertEquals(raw,prefs.getString("profile_v1_preserved",null))
        assertEquals(2,JSONObject(prefs.getString("profile",null)!!).getInt("schema"))
        assertTrue(AvatarProfileStore.clear(app)) // Only the user's separately confirmed reset removes both 3D copies.
        assertNull(prefs.getString("profile_v1_preserved",null))
    }
    @Test fun corruptedOrFutureSchemaCannotBeOverwrittenEvenWithoutExplicitLoad() {
        val raw="""{"schema":99}""";prefs.edit().putString("profile",raw).commit()
        assertFalse(AvatarProfileStore.save(app,AvatarProfile(170f,65f)))
        assertEquals(raw,prefs.getString("profile",null))
    }
    @Test fun eachDimensionChangesRealVerticesAndHeightIsFullSpan() {
        val base=AvatarProfile(170f,65f);val vertices=BodyAvatarMesh.build(base.parameters())
        listOf(base.copy(chest=1.15f),base.copy(torso=1.15f),base.copy(leg=1.15f),
            base.copy(shoulder=1.15f),base.copy(waist=1.15f),base.copy(hip=1.15f)).forEach { changed ->
            val mesh=BodyAvatarMesh.build(changed.parameters())
            assertTrue(vertices.indices.step(9).any { i -> (0..2).any {mesh[i+it]!=vertices[i+it]} })
        }
        val ys=vertices.indices.step(9).map {vertices[it+1]}
        assertEquals(1.70f,ys.max()-ys.min(),.001f)
    }
    @Test fun meshesStayFiniteSymmetricAndUnderMobileBudgetAcrossSafeExtremes() {
        listOf(AvatarProfile(100f,25f),AvatarProfile(230f,300f,chest=1.2f,torso=1.2f,leg=.8f),
            AvatarMeasurementImport.photo(photo)!!,AvatarMeasurementImport.manual(manual)!!).forEach { value ->
            val data=BodyAvatarMesh.build(value.parameters(),thighScale=value.thigh)
            assertTrue(data.size<110_000);assertTrue(data.all {it.isFinite()})
            val positions=data.toList().chunked(9)
            assertEquals(abs(positions.minOf {it[0]}),positions.maxOf {it[0]},.0001f)
            assertTrue(positions.all {it[1] in -1.2f..1.2f})
        }
    }
}
