package com.jeiu.fashionproject

import android.app.Application
import com.jeiu.fashionproject.data.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import kotlin.math.*
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class P10MannequinTest {
    @Test fun diverseProfilesKeepSectionsNormalsAndConnectedLimbs() {
        val metrics=JSONArray()
        for((name,body) in samples) {
            val data=PremiumAvatarMesh.build(body);val shape=PremiumAvatarMesh.Shape(body)
            assertTrue(name,data.all {it.isFinite()});assertTrue(data.size<1_500_000)
            for(i in data.indices.step(9))assertEquals(name,1f,sqrt(data[i+3].pow(2)+data[i+4].pow(2)+data[i+5].pow(2)),.002f)
            for(part in girths) {
                val section=shape.section(part)
                val measured=P9GeometryTest.sectionPerimeter(data,(section.y-.5f)*body.heightCm/100,
                    if(part==AvatarPart.THIGH)shape.legX*body.heightCm/100 else 0f)
                assertEquals("$name $part",body[part],measured,1.3f)
                metrics.put(JSONObject().put("profile",name).put("part",part.name).put("requested",body[part]).put("meshCm",measured).put("meshFloats",data.size))
            }
            assertSingleSurface(name,data)
        }
        File("build/p10-visual/section-metrics.json").also {it.parentFile.mkdirs()}.writeText(metrics.toString(2))
    }
    @Test fun deterministicCombinedAdjustmentsKeepAContinuousSurface() {
        val random=Random(101103)
        val metrics=JSONArray()
        repeat(20) {index->
            var body=AvatarCentimeters.generate(140f+random.nextInt(61),40f+random.nextInt(101),1)!!
            AvatarPart.entries.shuffled(random).forEach {part->
                val range=CmRulerMath.range(body,part)
                val v=AvatarCentimeters.snap(range.start+random.nextFloat()*(range.endInclusive-range.start))
                body=body.adjusted(part,v)!!
            }
            val data=PremiumAvatarMesh.build(body)
            assertSingleSurface("combined-$index ${body.values}",data)
            val shape=PremiumAvatarMesh.Shape(body)
            for(part in girths) {
                val section=shape.section(part)
                val actual=runCatching {P9GeometryTest.sectionPerimeter(data,(section.y-.5f)*body.heightCm/100,
                    if(part==AvatarPart.THIGH)shape.legX*body.heightCm/100 else 0f)}.getOrElse {
                        throw AssertionError("Synthetic section $part height=${body.heightCm} values=${body.values}",it)
                    }
                assertEquals("combined-$index $part",body[part],actual,1.3f)
                metrics.put(JSONObject().put("case",index).put("part",part.name).put("requested",body[part]).put("meshCm",actual).put("meshFloats",data.size))
            }
            // Neither thigh may cross the sagittal plane at its requested measurement band.
            for(y in listOf(.08f,.18f,shape.thigh)) {
                assertTrue("combined-$index leg gap at $y",shape.field(0f,y,0f)>0f)
            }
        }
        File("build/p10-visual/combined-metrics.json").also {it.parentFile!!.mkdirs()}.writeText(metrics.toString(2))
    }
    @Test fun originalP9DataIsUnchangedAndEveryCmControlStillDeformsTheMesh() {
        val b=samples.first().second;val before=b.toJson().toString();val mesh=PremiumAvatarMesh.build(b,48)
        assertFalse(mesh.contentEquals(P9MannequinReference.build(b,48)))
        for(part in AvatarPart.entries) {
            assertFalse(part.name,mesh.contentEquals(PremiumAvatarMesh.build(b.adjusted(part,b[part]+.5f)!!,48)))
        }
        assertEquals(before,b.toJson().toString())
        assertArrayEquals(mesh,PremiumAvatarMesh.build(b.resetAll(),48),0f)
    }
    @Test fun supportedEndpointCombinationsHaveNoDetachedLimbs() {
        val base=samples.first().second
        val ranges=AvatarPart.entries.map {CmRulerMath.range(base,it)}
        var checked=0
        val metrics=JSONArray()
        for(mask in 0 until (1 shl AvatarPart.entries.size)) {
            val values=AvatarPart.entries.mapIndexed {i,part->part to AvatarCm(
                if(mask and (1 shl i)==0)ranges[i].start else ranges[i].endInclusive,AvatarValueOrigin.ADJUSTED)}.toMap()
            val body=base.copy(values=values)
            if(body.issue()!=null)continue // Out-of-contract data is not silently clamped into another body.
            val data=runCatching {PremiumAvatarMesh.build(body)}.getOrElse {throw AssertionError("endpoint-$mask synthetic ${body.values}",it)}
            assertSingleSurface("endpoint-$mask",data);checked++
            assertTrue(data.size<PremiumAvatarMesh.MAX_FLOATS)
            val shape=PremiumAvatarMesh.Shape(body)
            for(part in girths) {
                val section=shape.section(part)
                val actual=runCatching {P9GeometryTest.sectionPerimeter(data,(section.y-.5f)*body.heightCm/100,
                    if(part==AvatarPart.THIGH)shape.legX*body.heightCm/100 else 0f)}.getOrElse {
                        throw AssertionError("Synthetic section $part height=${body.heightCm} values=${body.values}",it)
                    }
                assertEquals("endpoint-$mask $part",body[part],actual,1.3f)
                metrics.put(JSONObject().put("case",mask).put("part",part.name).put("requested",body[part]).put("meshCm",actual).put("meshFloats",data.size))
            }
        }
        assertTrue(checked>0)
        File("build/p10-visual/endpoint-count.txt").also {it.parentFile!!.mkdirs()}.writeText("$checked supported combinations of 128 candidates\n")
        File("build/p10-visual/endpoint-metrics.json").writeText(metrics.toString(2))
    }
    @Test fun cancelledOrDisconnectedGenerationCannotBePublishedAsSuccessful() {
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            PremiumAvatarMesh.build(samples.first().second,cancelled={true})
        }
        val tetra=floatArrayOf(0f,0f,0f, 1f,0f,0f, 0f,1f,0f)
        fun triangle(offset:Float)=FloatArray(27) {i->when(i%9) {
            0->tetra[(i/9)*3]+offset;1->tetra[(i/9)*3+1];2->tetra[(i/9)*3+2];else->0f}}
        assertFalse(MannequinTopology.connected(triangle(0f)+triangle(2f),{false}))
    }
    @Test fun sectionMeasurementAtATriangulatedSeamKeepsTheKnownPerimeter() {
        val vertices=ArrayList<Float>()
        val corners=listOf(-.2f to -.15f,.2f to -.15f,.2f to .15f,-.2f to .15f)
        fun point(index:Int,y:Float) {val (x,z)=corners[index%4];vertices.addAll(listOf(x,y,z,1f,0f,0f,1f,1f,1f))}
        for((low,high) in listOf(-.5f to 0f,0f to .5f))for(i in 0..3) {
            point(i,low);point(i+1,low);point(i+1,high)
            point(i,low);point(i+1,high);point(i,high)
        }
        val mesh=vertices.toFloatArray()
        for(y in listOf(-.25f,0f,.25f))assertEquals(140f,P9GeometryTest.sectionPerimeter(mesh,y,0f),.001f)
    }
    @Test fun exportMatchedP9P10GeometryForFrontSideAndBackReview() {
        val dir=File("build/p10-visual").also {it.mkdirs()};val manifest=JSONArray()
        for((name,b) in samples) {
            fun write(suffix:String,data:FloatArray) {DataOutputStream(File(dir,"$name-$suffix.bin").outputStream().buffered()).use {out->data.forEach {out.writeFloat(it)}}}
            write("p9",P9MannequinReference.build(b));write("p10",PremiumAvatarMesh.build(b))
            manifest.put(JSONObject().put("id",name).put("heightCm",b.heightCm).put("weightKg",b.weightKg).put("body",b.toJson()))
        }
        File(dir,"profiles.json").writeText(manifest.toString(2))
    }
    companion object {
        private val girths=listOf(AvatarPart.CHEST,AvatarPart.WAIST,AvatarPart.HIP,AvatarPart.THIGH)
        private val samples by lazy {
            listOf("standard" to (170f to 65f),"slender" to (158f to 45f),"broad" to (185f to 105f),
                "short" to (145f to 55f),"tall" to (198f to 90f),"minimum-height" to (100f to 25f),"maximum-height" to (230f to 300f))
                .map {(name,hw)->name to AvatarCentimeters.generate(hw.first,hw.second,1)!!}+
                listOf("wide-shoulders" to (AvatarPart.SHOULDER to 47f),
                    "narrow-waist" to (AvatarPart.WAIST to 65f),
                    "wide-hips" to (AvatarPart.HIP to 115f),
                    "thick-thighs" to (AvatarPart.THIGH to 58f))
                    .map {(name,edit)->name to requireNotNull(AvatarCentimeters.generate(170f,65f,1)!!.adjusted(edit.first,edit.second)) {"Invalid synthetic profile $name"}}+
                listOf("adjusted" to AvatarCentimeters.generate(170f,65f,1)!!.let {base->
                    mapOf(AvatarPart.SHOULDER to 38f,AvatarPart.CHEST to 100f,AvatarPart.WAIST to 90f,AvatarPart.HIP to 110f,AvatarPart.THIGH to 58f)
                        .entries.fold(base) {b,(p,v)->b.adjusted(p,v)!!}})
        }
        private fun assertSingleSurface(name:String,data:FloatArray) {
            // Weld duplicate triangle-soup vertices at 0.01mm; detect detached heads/hands/feet.
            val ids=HashMap<String,Int>();val parent=ArrayList<Int>()
            fun vertex(i:Int):Int {
                val key="${round(data[i]*100000).toInt()},${round(data[i+1]*100000).toInt()},${round(data[i+2]*100000).toInt()}"
                return ids.getOrPut(key) {parent.add(parent.size);parent.lastIndex}
            }
            fun root(v:Int):Int {var x=v;while(parent[x]!=x){parent[x]=parent[parent[x]];x=parent[x]};return x}
            fun union(a:Int,b:Int) {val x=root(a);val y=root(b);if(x!=y)parent[x]=y}
            for(i in data.indices.step(27)) {val a=vertex(i);val b=vertex(i+9);val c=vertex(i+18);union(a,b);union(a,c)}
            val groups=parent.indices.map {root(it)}.distinct()
            val detail=if(groups.size==1)"" else groups.map {g->
                val points=ids.filterValues {root(it)==g}.keys.map {it.split(',').map(String::toInt)}
                "${points.size} vertices y=${points.minOf {it[1]}}..${points.maxOf {it[1]}} x=${points.minOf {it[0]}}..${points.maxOf {it[0]}}"
            }.joinToString()
            assertEquals("$name detached surface components $detail",1,groups.size)
        }
    }
}
