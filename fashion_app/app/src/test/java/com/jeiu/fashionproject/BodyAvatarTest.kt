package com.jeiu.fashionproject

import com.jeiu.fashionproject.data.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class BodyAvatarTest {
    @Test fun meshIsThreeDimensionalFiniteBoundedAndLitByUnitNormals() {
        val data=BodyAvatarMesh.build(AvatarParameters())
        assertEquals(0,data.size%(BodyAvatarMesh.STRIDE*3))
        assertTrue(data.size in 1..100_000)
        assertTrue(data.all { it.isFinite() })
        val vertices=data.toList().chunked(BodyAvatarMesh.STRIDE)
        assertTrue(vertices.any { it[2] > .05f } && vertices.any { it[2] < -.05f })
        vertices.forEach { v ->
            assertEquals(1f,sqrt(v[3]*v[3]+v[4]*v[4]+v[5]*v[5]),.001f)
            assertTrue(v[0] in -1f..1f && v[1] in -1.2f..1.2f && v[2] in -1f..1f)
        }
    }
    @Test fun shouldersAndHeightActuallyChangeTheGeometry() {
        fun span(mesh:FloatArray,index:Int)=mesh.toList().chunked(9).maxOf { abs(it[index]) }
        val normal=BodyAvatarMesh.build(AvatarParameters())
        val wider=BodyAvatarMesh.build(AvatarParameters(),shoulderScale=1.2f)
        assertTrue(span(wider,0)>span(normal,0))
        val tall=AvatarParameters.from(BodyDimensionProfile(mapOf(BodyDimension.HEIGHT to DimensionValue(220f,MeasurementOrigin.USER_ENTERED,1L)),emptyList(),emptyList()))
        assertTrue(span(BodyAvatarMesh.build(tall),1)>span(normal,1))
    }
    @Test fun manualCircumferenceAndPhotoWidthsRemainExplicitVisualAssumptions() {
        val manual=ManualBodyMeasurements(170f,40f,90f,75f,95f,88f,52f,1L)
        val profile=BodyDimensions.resolve(manual,null)
        val model=AvatarParameters.from(profile)
        assertTrue(model.isValid())
        assertTrue(model.sources.any { it.contains("허리 둘레") && it.contains("타원 가정") })
        assertEquals(75f,profile[BodyDimension.WAIST_CIRCUMFERENCE]!!.cm)
        assertTrue(model.notes.any { it.contains("깊이") })
    }
    @Test fun missingOrBadInputsUseSafeDefaultsAndNeverProduceNaN() {
        val empty=BodyDimensionProfile(emptyMap(),emptyList(),emptyList())
        assertTrue(AvatarParameters.from(empty).isValid())
        val invalid=empty.copy(values=mapOf(BodyDimension.HEIGHT to DimensionValue(Float.NaN,MeasurementOrigin.USER_ENTERED,1)))
        assertEquals(170f,AvatarParameters.from(invalid).heightCm)
        assertTrue(runCatching { BodyAvatarMesh.build(AvatarParameters(shoulderCm=Float.NaN)) }.isFailure)
        assertTrue(runCatching { BodyAvatarMesh.build(AvatarParameters(),waistScale=10f) }.isFailure)
    }
}
