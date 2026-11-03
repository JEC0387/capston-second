package com.jeiu.fashionproject

import android.app.Application
import com.jeiu.fashionproject.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.DataOutputStream
import kotlin.math.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class P10NormalsTest {
    @Test fun unequalRowDerivativeIsExactForQuadraticsAndLinearFields() {
        for(left in listOf(.0001f,.002f,.012f))for(right in listOf(.0001f,.002f,.012f)) {
            for(center in listOf(-.2f,0f,.3f)) {
                fun f(y:Float)=3*y*y+2*y+.1f
                assertEquals(6*center+2,PremiumAvatarMesh.rowDerivative(f(center-left),f(center),f(center+right),left,right),.002f)
                assertEquals(2f,PremiumAvatarMesh.rowDerivative(2*(center-left),2*center,2*(center+right),left,right),.001f)
            }
        }
        // The old secant estimates at the midpoint of its unequal neighbours, not the row.
        assertTrue(abs((.012f.pow(2)-.001f.pow(2))/.013f)> .01f)
        assertEquals(0f,PremiumAvatarMesh.rowDerivative(.001f.pow(2),0f,.012f.pow(2),.001f,.012f),1e-7f)
    }

    @Test fun exportedNormalsAgreeWithIndependentSmallStepFieldGradient() {
        val body=AvatarCentimeters.generate(170f,65f,1)!!
        val shape=PremiumAvatarMesh.Shape(body);val h=body.heightCm/100
        val mesh=PremiumAvatarMesh.build(body);val reference=mesh.copyOf()
        val errors=ArrayList<Double>();val epsilon=.0002f
        for(i in mesh.indices.step(9)) {
            val x=mesh[i]/h;val y=mesh[i+1]/h+.5f;val z=mesh[i+2]/h
            val g=floatArrayOf(shape.field(x+epsilon,y,z)-shape.field(x-epsilon,y,z),
                shape.field(x,y+epsilon,z)-shape.field(x,y-epsilon,z),shape.field(x,y,z+epsilon)-shape.field(x,y,z-epsilon))
            val length=sqrt(g.sumOf {(it*it).toDouble()}).toFloat()
            if(length<1e-8f)continue
            for(c in 0..2)reference[i+3+c]=g[c]/length
            // Torso front/back at section planes: exclude CSG limb junctions/silhouette poles.
            if(abs(x)<.035f && y in shape.hip..shape.chest && abs(z)>.03f) {
                val dot=(0..2).sumOf {(mesh[i+3+it]*reference[i+3+it]).toDouble()}.coerceIn(-1.0,1.0)
                errors.add(Math.toDegrees(acos(dot)))
            }
        }
        errors.sort();assertTrue(errors.size>100)
        assertTrue("Torso mean angular error ${errors.average()}",errors.average()<.5)
        assertTrue("Torso p95 angular error ${errors[(errors.size*.95).toInt()]}",errors[(errors.size*.95).toInt()]<1.0)
        assertTrue("Torso maximum angular error ${errors.last()}",errors.last()<1.5)
        val dir=File("build/p10-visual").also {it.mkdirs()}
        DataOutputStream(File(dir,"standard-field-normal.bin").outputStream().buffered()).use {out->reference.forEach {out.writeFloat(it)}}
        File(dir,"normal-diagnostic.txt").writeText("Torso samples=${errors.size}; meanDegrees=${errors.average()}; p95Degrees=${errors[(errors.size*.95).toInt()]}; maxDegrees=${errors.last()}\n")
    }

    @Test fun sharedVertexNormalsAreContinuousUnitLengthAndTrianglesFaceOutwards() {
        val mesh=PremiumAvatarMesh.build(AvatarCentimeters.generate(170f,65f,1)!!)
        val shared=HashMap<List<Int>,FloatArray>()
        for(i in mesh.indices.step(9)) {
            assertTrue((0..8).all {mesh[i+it].isFinite()})
            val n=mesh.copyOfRange(i+3,i+6)
            assertEquals(1f,sqrt(n.sumOf {(it*it).toDouble()}).toFloat(),.002f)
            val key=(0..2).map {round(mesh[i+it]*1_000_000).toInt()}
            shared.putIfAbsent(key,n)?.let {assertArrayEquals("Shared edge must not form a shading seam",it,n,.002f)}
        }
        for(i in mesh.indices.step(27)) {
            val u=FloatArray(3) {mesh[i+9+it]-mesh[i+it]}
            val v=FloatArray(3) {mesh[i+18+it]-mesh[i+it]}
            val face=floatArrayOf(u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0])
            val dot=(0..2).sumOf {c->(face[c]*(mesh[i+3+c]+mesh[i+12+c]+mesh[i+21+c])).toDouble()}
            assertTrue("Reversed triangle at $i",dot>=-1e-12)
        }
    }
}
