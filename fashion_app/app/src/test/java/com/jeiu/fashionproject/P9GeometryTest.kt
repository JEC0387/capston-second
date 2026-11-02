package com.jeiu.fashionproject

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.DataOutputStream
import kotlin.math.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class P9GeometryTest {
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    @Before @After fun clear() {app.getSharedPreferences("avatar_visual_profile_v1",0).edit().clear().commit();AvatarProfileStore.load(app)}
    @Test fun estimatesAreDeterministicBoundedAndExplicitlyNotMeasurements() {
        assertEquals(body,AvatarCentimeters.generate(170f,65f,1L))
        for((h,w) in listOf(100f to 25f,100f to 300f,230f to 25f,230f to 300f)) {
            val b=AvatarCentimeters.generate(h,w,1)!!;assertNull(b.issue());assertTrue(b.values.values.all {it.origin==AvatarValueOrigin.AUTO})
        }
        for((h,w) in listOf(Float.NaN to 65f,99f to 65f,170f to Float.POSITIVE_INFINITY))assertNull(AvatarCentimeters.generate(h,w,1))
        assertEquals(170f,body.heightCm)
    }
    @Test fun circlesAndEllipsesMatchIndependentNumericalIntegration() {
        for(part in listOf(AvatarPart.CHEST,AvatarPart.WAIST,AvatarPart.HIP,AvatarPart.THIGH)) {
            val (a,b)=body.radii(part)
            val perimeter=(0 until 4096).sumOf {i->
                val t=i*2*PI/4096;val u=(i+1)*2*PI/4096
                hypot(a*(cos(u)-cos(t)),b*(sin(u)-sin(t)))
            }
            assertEquals(body[part].toDouble(),perimeter,.002)
        }
    }
    @Test fun generatedMeshIsFiniteOutwardAndWithinMemoryBudget() {
        assertTrue(mesh.size in 1..1_500_000);assertEquals(0,mesh.size%27)
        assertTrue(mesh.all {it.isFinite()})
        for(i in mesh.indices.step(9))assertEquals(1f,sqrt(mesh[i+3].pow(2)+mesh[i+4].pow(2)+mesh[i+5].pow(2)),.002f)
        for(i in mesh.indices.step(27)) {
            val ux=mesh[i+9]-mesh[i];val uy=mesh[i+10]-mesh[i+1];val uz=mesh[i+11]-mesh[i+2]
            val vx=mesh[i+18]-mesh[i];val vy=mesh[i+19]-mesh[i+1];val vz=mesh[i+20]-mesh[i+2]
            val dot=(uy*vz-uz*vy)*(mesh[i+3]+mesh[i+12]+mesh[i+21])+
                (uz*vx-ux*vz)*(mesh[i+4]+mesh[i+13]+mesh[i+22])+(ux*vy-uy*vx)*(mesh[i+5]+mesh[i+14]+mesh[i+23])
            assertTrue(dot>=-1e-8f)
        }
    }
    @Test fun meshSectionPerimetersMatchDisplayedCm() {
        val shape=PremiumAvatarMesh.Shape(body)
        for(part in listOf(AvatarPart.CHEST,AvatarPart.WAIST,AvatarPart.HIP,AvatarPart.THIGH)) {
            val section=shape.section(part);val y=(section.y-.5f)*body.heightCm/100
            val targetX=if(part==AvatarPart.THIGH)shape.legX*body.heightCm/100 else 0f
            val actual=sectionPerimeter(mesh,y,targetX)
            assertEquals("$part requested ${body[part]} rendered $actual",body[part],actual,1.3f)
        }
    }
    @Test fun eachCmParameterChangesGeometryAndResetIsExact() {
        val original=PremiumAvatarMesh.build(body,48)
        for(part in AvatarPart.entries) {
            val changed=body.adjusted(part,body[part]+1f)!!
            assertFalse("$part",original.contentEquals(PremiumAvatarMesh.build(changed,48)))
            assertEquals(body.values.getValue(part).cm,changed.reset(part)[part])
        }
        assertArrayEquals(original,PremiumAvatarMesh.build(body.resetAll(),48),0f)
    }
    @Test fun reflectionAndCameraRotationsPreserveFiniteGeometryAndHeight() {
        val shape=PremiumAvatarMesh.Shape(body)
        for(y in listOf(.05f,.30f,.48f,.65f,.80f,.93f))for(x in listOf(.02f,.09f,.15f,.24f))
            assertEquals(shape.field(x,y,.02f),shape.field(-x,y,.02f),.00001f)
        for(angle in listOf(0.0,PI/2,PI))for(i in mesh.indices.step(9)) {
            val x=mesh[i]*cos(angle)+mesh[i+2]*sin(angle);val z= -mesh[i]*sin(angle)+mesh[i+2]*cos(angle)
            assertTrue(x.isFinite()&&z.isFinite())
        }
        val ys=mesh.indices.step(9).map {mesh[it+1]}
        assertEquals(1.70f,ys.max()-ys.min(),.025f)
    }
    @Test fun schema2IsPreservedUntilExplicitCmSaveAndCorruptInputIsNotOverwritten() {
        val previous=AvatarProfile(170f,65f);assertTrue(AvatarProfileStore.save(app,previous))
        val prefs=app.getSharedPreferences("avatar_visual_profile_v1",0);val raw=prefs.getString("profile",null)
        val next=previous.copy(centimeters=body)
        assertEquals(raw,prefs.getString("profile",null))
        assertTrue(AvatarProfileStore.save(app,next));assertEquals(raw,prefs.getString("profile_pre_cm_preserved",null))
        AvatarProfileStore.load(app);assertEquals(next,AvatarProfileStore.current.value)
        prefs.edit().putString("profile","{damaged").commit()
        assertFalse(AvatarProfileStore.save(app,next));assertEquals("{damaged",prefs.getString("profile",null))
    }
    @Test fun photoFrontWidthsNeverTurnIntoCircumferenceSources() {
        val photo=com.jeiu.fashionproject.ui.viewmodel.BodyMeasurements(170f,40f,28f,37f,80f,46f,.1f,1700f,1)
        val legacy=AvatarMeasurementImport.photo(photo)!!.copy(waist=1.1f)
        val b=AvatarCentimeters.fromLegacy(legacy,1)!!
        assertEquals(28f,b.photoFrontWidths["waist"])
        assertFalse(b.values.getValue(AvatarPart.WAIST).origin==AvatarValueOrigin.PHOTO)
        assertFalse(b.values.getValue(AvatarPart.HIP).origin==AvatarValueOrigin.PHOTO)
        assertNull(b.references[AvatarPart.WAIST])
    }
    @Test fun exportActualGeometryForOfflineVisualReview() {
        val dir=File("build/p9-visual").also {it.mkdirs()}
        fun write(name:String,data:FloatArray) {DataOutputStream(File(dir,name).outputStream().buffered()).use {out->data.forEach {out.writeFloat(it)}}}
        write("premium.bin",mesh);write("legacy.bin",BodyAvatarMesh.build(AvatarProfile(170f,65f).parameters()))
        File(dir,"README.txt").writeText("Actual Kotlin mesh buffers XYZ/normal/RGB, big-endian float32. CPU review only; not an S25+ GLES capture.\n")
    }
    @Test fun adjustedAndDifferentHeightProfilesPreserveMeasuredSections() {
        val profiles=listOf(body.adjusted(AvatarPart.WAIST,77.5f)!!,
            AvatarCentimeters.generate(100f,25f,1)!!,AvatarCentimeters.generate(230f,300f,1)!!)
        for(b in profiles) {
            val data=PremiumAvatarMesh.build(b);val shape=PremiumAvatarMesh.Shape(b)
            for(part in listOf(AvatarPart.CHEST,AvatarPart.WAIST,AvatarPart.HIP,AvatarPart.THIGH)) {
                val section=shape.section(part);val target=if(part==AvatarPart.THIGH)shape.legX*b.heightCm/100 else 0f
                assertEquals("${b.heightCm} $part",b[part],sectionPerimeter(data,(section.y-.5f)*b.heightCm/100,target),1.3f)
            }
        }
    }
    @Test fun rulerEndpointsPreserveActualSectionCmAndDoNotInvertTriangles() {
        val metrics=org.json.JSONArray()
        for(part in AvatarPart.entries) {
            val range=CmRulerMath.range(body,part)
            for(v in listOf(range.start,range.endInclusive)) {
                val changed=body.adjusted(part,v)!!;val data=PremiumAvatarMesh.build(changed);val shape=PremiumAvatarMesh.Shape(changed)
                assertTrue(data.all {it.isFinite()})
                for(sectionPart in listOf(AvatarPart.CHEST,AvatarPart.WAIST,AvatarPart.HIP,AvatarPart.THIGH)) {
                    val section=shape.section(sectionPart);val x=if(sectionPart==AvatarPart.THIGH)shape.legX*changed.heightCm/100 else 0f
                    val actual=sectionPerimeter(data,(section.y-.5f)*changed.heightCm/100,x)
                    metrics.put(org.json.JSONObject().put("adjusted",part.name).put("value",v).put("section",sectionPart.name)
                        .put("requested",changed[sectionPart]).put("rendered",actual).put("errorCm",abs(actual-changed[sectionPart])))
                    assertEquals("$part=$v section $sectionPart",changed[sectionPart],actual,1.3f)
                }
            }
        }
        File("build/p9-visual/section-metrics.json").also {it.parentFile.mkdirs()}.writeText(metrics.toString(2))
    }
    @Test fun importedTapeValuesAndLegacyV1AssumptionsArePreserved() {
        val manual=ManualBodyMeasurements(170f,42f,94f,78f,98f,82f,47f,1)
        val legacy=AvatarMeasurementImport.manual(manual)!!
        val converted=AvatarCentimeters.fromLegacy(legacy,1)!!
        assertEquals(78f,converted[AvatarPart.WAIST]);assertEquals(AvatarValueOrigin.MANUAL,converted.values.getValue(AvatarPart.WAIST).origin)
        assertEquals(AvatarValueOrigin.AUTO,converted.values.getValue(AvatarPart.THIGH).origin)
        assertEquals(AvatarValueOrigin.ADJUSTED,converted.adjusted(AvatarPart.WAIST,77.5f)!!.values.getValue(AvatarPart.WAIST).origin)
        assertEquals(78f,converted.references.getValue(AvatarPart.WAIST).cm)
        val old=body.copy(assumption=AvatarCentimeters.LEGACY_ASSUMPTION)
        assertEquals(old,AvatarCentimeters.fromJson(old.toJson()))
        assertNotNull(converted.copy(values=converted.values+(AvatarPart.WAIST to AvatarCm(78f,AvatarValueOrigin.PHOTO))).issue())
    }
    @Test fun wrongPreferenceTypeIsReportedWithoutErasingOriginal() {
        val prefs=app.getSharedPreferences("avatar_visual_profile_v1",0);prefs.edit().putInt("profile",42).commit()
        AvatarProfileStore.load(app);assertNull(AvatarProfileStore.current.value);assertNotNull(AvatarProfileStore.issue.value)
        assertFalse(AvatarProfileStore.save(app,AvatarProfile(170f,65f)));assertEquals(42,prefs.getInt("profile",0))
    }
    companion object {
        private val body by lazy {AvatarCentimeters.generate(170f,65f,1)!!}
        private val mesh by lazy {PremiumAvatarMesh.build(body)}
        private fun sectionPerimeter(data:FloatArray,y:Float,targetX:Float):Float {
            data class P(val x:Float,val z:Float)
            fun key(p:P)="${round(p.x*100000).toInt()},${round(p.z*100000).toInt()}"
            val points=mutableMapOf<String,P>();val graph=mutableMapOf<String,MutableSet<String>>()
            for(i in data.indices.step(27)) {
                val hits=mutableListOf<P>()
                for((a,b) in listOf(0 to 9,9 to 18,18 to 0)) {
                    val ya=data[i+a+1];val yb=data[i+b+1]
                    if((ya<y&&yb>=y)||(yb<y&&ya>=y)) {
                        val t=(y-ya)/(yb-ya)
                        hits+=P(data[i+a]+(data[i+b]-data[i+a])*t,data[i+a+2]+(data[i+b+2]-data[i+a+2])*t)
                    }
                }
                if(hits.size!=2)continue
                val a=key(hits[0]);val b=key(hits[1]);if(a==b)continue
                points[a]=hits[0];points[b]=hits[1]
                graph.getOrPut(a){mutableSetOf()}.add(b);graph.getOrPut(b){mutableSetOf()}.add(a)
            }
            val visited=mutableSetOf<String>();val components=mutableListOf<Set<String>>()
            for(start in graph.keys)if(visited.add(start)) {
                val component=mutableSetOf(start);val queue=ArrayDeque<String>();queue.add(start)
                while(queue.isNotEmpty())for(next in graph.getValue(queue.removeFirst()))if(visited.add(next)){component.add(next);queue.add(next)}
                components+=component
            }
            val chosen=components.minBy {abs(it.map {k->points.getValue(k).x}.average()-targetX)}
            return (chosen.sumOf {a->graph.getValue(a).sumOf {b->
                val p=points.getValue(a);val q=points.getValue(b);hypot((p.x-q.x).toDouble(),(p.z-q.z).toDouble())
            }}*50).toFloat()
        }
    }
}
