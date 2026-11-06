package com.jeiu.fashionproject

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.LocalVtonRepository
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.ui.screens.*
import com.jeiu.fashionproject.ui.viewmodel.FittingPoseDetector
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.security.MessageDigest

/** Procedurally drawn silhouettes/textured fabric, not photographs or real model inference. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class P36DrapeCompositorTest {
    private val size=Size(240f,480f)
    private fun side(left:Boolean):FittingPoseDetector.SidePoints {
        val x=if(left)85f else 155f;val hip=if(left)90f else 150f
        return FittingPoseDetector.SidePoints(shoulder=Offset(x,130f),elbow=Offset(if(left)55f else 185f,200f),
            wrist=Offset(if(left)68f else 172f,264f),hip=Offset(hip,260f),
            knee=Offset(if(left)88f else 152f,340f),ankle=Offset(if(left)80f else 160f,425f),
            heel=Offset(if(left)81f else 159f,433f),toe=Offset(if(left)62f else 178f,445f))
    }
    private fun body():FittingPoseDetector.BodyPoints {
        val l=side(true);val r=side(false)
        return FittingPoseDetector.BodyPoints(240,480,l.shoulder!! to r.shoulder!!,l.hip!! to r.hip!!,
            l.ankle!! to r.ankle!!,l,r,Offset(120f,88f),42f)
    }
    private fun drawPolygon(canvas:Canvas,paint:Paint,vararg points:Float) {
        val path=Path();path.moveTo(points[0],points[1]);for(i in 2 until points.size step 2)path.lineTo(points[i],points[i+1]);path.close();canvas.drawPath(path,paint)
    }
    private fun image(w:Int,h:Int,color:Int=Color.TRANSPARENT)=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).apply {eraseColor(color)}
    private fun person():Bitmap=image(240,480,Color.rgb(232,229,218)).also { bitmap ->
        val canvas=Canvas(bitmap);val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color=Color.rgb(190,143,112);canvas.drawOval(99f,58f,141f,117f,paint)
        paint.color=Color.rgb(70,57,46);canvas.drawOval(99f,48f,141f,75f,paint)
        paint.color=Color.rgb(173,171,162)
        drawPolygon(canvas,paint,84f,128f,156f,128f,163f,256f,77f,256f)
        paint.strokeWidth=19f;paint.strokeCap=Paint.Cap.ROUND
        canvas.drawLine(85f,138f,55f,200f,paint);canvas.drawLine(155f,138f,185f,200f,paint)
        paint.color=Color.rgb(190,143,112);paint.strokeWidth=15f
        canvas.drawLine(55f,200f,68f,264f,paint);canvas.drawLine(185f,200f,172f,264f,paint)
        canvas.drawCircle(70f,271f,9f,paint);canvas.drawCircle(170f,271f,9f,paint)
        paint.color=Color.rgb(117,119,120);paint.strokeWidth=27f
        canvas.drawLine(91f,265f,88f,340f,paint);canvas.drawLine(88f,340f,80f,425f,paint)
        canvas.drawLine(149f,265f,152f,340f,paint);canvas.drawLine(152f,340f,160f,425f,paint)
        paint.color=Color.rgb(50,45,43);paint.strokeWidth=16f
        canvas.drawLine(81f,433f,62f,445f,paint);canvas.drawLine(159f,433f,178f,445f,paint)
    }
    private fun top(background:Boolean=false):Bitmap=image(220,200,if(background)Color.WHITE else Color.TRANSPARENT).also { bitmap ->
        val canvas=Canvas(bitmap);val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.rgb(40,105,142)}
        drawPolygon(canvas,paint,60f,35f,88f,20f,132f,20f,160f,35f,208f,65f,194f,95f,160f,80f,160f,185f,60f,185f,60f,80f,26f,95f,12f,65f)
        val clip=Path().apply {moveTo(60f,35f);lineTo(88f,20f);lineTo(132f,20f);lineTo(160f,35f);lineTo(208f,65f);lineTo(194f,95f);lineTo(160f,80f);lineTo(160f,185f);lineTo(60f,185f);lineTo(60f,80f);lineTo(26f,95f);lineTo(12f,65f);close()}
        canvas.save();canvas.clipPath(clip);paint.color=Color.rgb(90,156,183);paint.strokeWidth=3f
        for(y in 42..180 step 13)canvas.drawLine(10f,y.toFloat(),210f,y.toFloat(),paint)
        canvas.restore();paint.color=if(background)Color.WHITE else Color.TRANSPARENT
        paint.xfermode=android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC)
        canvas.drawOval(88f,5f,132f,41f,paint)
    }
    private fun longTop():Bitmap=image(360,220).also { bitmap ->
        val canvas=Canvas(bitmap);val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.rgb(45,112,135)}
        val outline=Path().apply {moveTo(130f,35f);lineTo(158f,20f);lineTo(202f,20f);lineTo(230f,35f)
            lineTo(348f,60f);lineTo(343f,91f);lineTo(230f,79f);lineTo(230f,205f)
            lineTo(130f,205f);lineTo(130f,79f);lineTo(17f,91f);lineTo(12f,60f);close()}
        canvas.drawPath(outline,paint);canvas.save();canvas.clipPath(outline)
        paint.color=Color.rgb(100,167,181);paint.strokeWidth=3f
        for(y in 40..200 step 13)canvas.drawLine(10f,y.toFloat(),350f,y.toFloat(),paint)
        canvas.restore();paint.color=Color.TRANSPARENT
        paint.xfermode=android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC)
        canvas.drawOval(158f,5f,202f,41f,paint)
    }
    private fun pants():Bitmap=image(180,280).also { bitmap ->
        val canvas=Canvas(bitmap);val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.rgb(53,68,96)}
        drawPolygon(canvas,paint,25f,12f,155f,12f,160f,95f,145f,265f,103f,265f,90f,110f,77f,265f,35f,265f,20f,95f)
        paint.color=Color.rgb(125,135,150);paint.strokeWidth=2f
        canvas.drawLine(48f,35f,52f,258f,paint);canvas.drawLine(132f,35f,128f,258f,paint)
        paint.color=Color.rgb(32,34,40);canvas.drawRect(24f,21f,156f,29f,paint)
    }
    private fun skirt():Bitmap=image(180,220).also { bitmap ->
        val canvas=Canvas(bitmap);val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.rgb(132,73,69)}
        drawPolygon(canvas,paint,55f,15f,125f,15f,165f,205f,15f,205f)
        paint.color=Color.rgb(176,112,104);paint.strokeWidth=2f
        for(x in 65..115 step 10)canvas.drawLine(x.toFloat(),25f,90f+(x-90)*2.2f,196f,paint)
    }
    private fun shoes(single:Boolean=false):Bitmap=image(180,150).also { bitmap ->
        val canvas=Canvas(bitmap);val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        for(x in if(single)listOf(50f)else listOf(50f,130f)) {
            paint.color=Color.rgb(210,180,122)
            val path=Path().apply {moveTo(x-13,133f);lineTo(x-23,45f);cubicTo(x-25,10f,x+25,10f,x+23,45f);lineTo(x+13,133f);close()}
            canvas.drawPath(path,paint);paint.color=Color.rgb(90,74,55);paint.strokeWidth=3f
            for(y in 58..106 step 12)canvas.drawLine(x-9,y.toFloat(),x+9,y.toFloat(),paint)
        }
    }
    private fun garment(id:String,category:String,image:Bitmap,sub:String?=null,body:FittingPoseDetector.BodyPoints=body()):WornGarment {
        val item=WardrobeItem(id,id,category,"synthetic",0,"file:///synthetic-$id.png",subCategory=sub,isCutout=true)
        val slot=when(category){"상의"->FitSlot.TOP;"하의"->FitSlot.BOTTOM;else->FitSlot.SHOES}
        val aspect=image.height.toFloat()/image.width
        val placement=requireNotNull(photoAutoPlacement(slot,item,aspect,size,body))
        return WornGarment(item,item.imageUri,aspect,placement.center,placement.width,rotation=placement.rotation)
    }
    private fun decode(bytes:ByteArray)=requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size))
    private fun evidence(name:String,photo:Bitmap,layers:List<Pair<WornGarment,Bitmap>>,result:ByteArray) {
        val folder=File("build/p36-synthetic").apply {mkdirs()}
        File(folder,"$name-p36.png").writeBytes(result)
        File(folder,"$name-p35.png").writeBytes(ManualPhotoExport.render(photo,layers,size,1f))
        File(folder,"$name.sha256").writeText(MessageDigest.getInstance("SHA-256").digest(result).joinToString(""){"%02x".format(it)})
        val old=decode(ManualPhotoExport.render(photo,layers,size,1f));val current=decode(result)
        val contact=image(720,480,Color.WHITE);val canvas=Canvas(contact);val paint=Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(photo,null,android.graphics.Rect(0,0,240,480),paint)
        canvas.drawBitmap(old,null,android.graphics.Rect(240,0,480,480),paint)
        canvas.drawBitmap(current,null,android.graphics.Rect(480,0,720,480),paint)
        File(folder,"$name-original-p35-p36.png").outputStream().use {contact.compress(Bitmap.CompressFormat.PNG,100,it)}
        contact.recycle();old.recycle();current.recycle()
    }

    @Test fun topPantsShoesAndCombinationsProduceDistinctDecodedLocalPng()=runBlocking {
        val photo=person();val t=top();val lt=longTop();val b=pants();val s=shoes();val sk=skirt()
        val top=garment("top","상의",t,"반팔 상의") to t
        val longTop=garment("long-top","상의",lt,"긴소매 상의") to lt
        val bottom=garment("pants","하의",b,"긴바지") to b
        val shoes=garment("shoes","신발",s) to s
        val skirt=garment("skirt","하의",sk,"치마") to sk
        val variants=linkedMapOf("top" to listOf(top),"pants" to listOf(bottom),"shoes" to listOf(shoes),
            "top-pants" to listOf(bottom,top),"all-three" to listOf(bottom,shoes,top),"skirt" to listOf(skirt),"long-top" to listOf(longTop))
        for((name,layers)in variants) {
            val result=LocalDrapeCompositor.render(photo,layers,size,1f,body())
            val decoded=decode(result.png);assertEquals(240,decoded.width);assertEquals(480,decoded.height)
            assertEquals("Background must remain exact",photo.getPixel(5,5),decoded.getPixel(5,5))
            assertEquals("Face must remain exact",photo.getPixel(120,90),decoded.getPixel(120,90))
            // Independent protected ROIs: no outfit belongs in the outer strips/top or face core.
            for(y in 0 until 480)for(x in 0 until 240)if(x<30 || x>=210 || y<36 || (x in 108..132 && y in 76..106))
                assertEquals("$name original ROI ($x,$y)",photo.getPixel(x,y),decoded.getPixel(x,y))
            var changed=0;for(y in 0 until 480)for(x in 0 until 240)if(decoded.getPixel(x,y)!=photo.getPixel(x,y))changed++
            assertTrue("$name must modify pixels",changed>60)
            fun changedIn(l:Int,t:Int,r:Int,b:Int):Int {
                var count=0;for(y in t..b)for(x in l..r)if(decoded.getPixel(x,y)!=photo.getPixel(x,y))count++
                return count
            }
            if(layers.any {it.first.item.category=="신발"}) {
                assertTrue("Left shoe must reach the independently specified left foot region",changedIn(45,420,96,461)>10)
                assertTrue("Right shoe must reach the independently specified right foot region",changedIn(144,420,195,461)>10)
            }
            if(layers.any {it.first.item.category=="하의" && it.first.item.subCategory!="치마"}) {
                assertTrue("Left pants leg must cover its knee",changedIn(80,330,97,350)>20)
                assertTrue("Right pants leg must cover its knee",changedIn(143,330,160,350)>20)
            }
            if(layers.any {it.first.item.category=="상의"})assertTrue("Torso texture must reach the chest",changedIn(105,165,135,205)>100)
            if(name=="long-top") {
                assertTrue("Long left sleeve must reach forearm",changedIn(57,225,73,253)>20)
                assertTrue("Long right sleeve must reach forearm",changedIn(167,225,183,253)>20)
                assertEquals("Left cuff must not trail below the hand",0,changedIn(50,283,78,305))
                assertEquals("Right cuff must not trail below the hand",0,changedIn(162,283,190,305))
            }
            assertFalse("Nonrigid/feet result cannot be the P35 rectangle",result.png.contentEquals(ManualPhotoExport.render(photo,layers,size,1f)))
            evidence(name,photo,layers,result.png)
            val app=ApplicationProvider.getApplicationContext<Application>();val file=File(app.cacheDir,"p36-$name-${System.nanoTime()}.png")
            try {LocalVtonRepository.save(app,result.png,Uri.fromFile(file),true);val reopened=decode(LocalVtonRepository.openSavedImage(app,Uri.fromFile(file)));assertTrue(decoded.sameAs(reopened));reopened.recycle()}finally{file.delete()}
            decoded.recycle()
        }
    }

    /** Coil dispatches to Android's main looper. Keep that looper moving while the real
     * file decoder runs on workers; blocking the test's main thread deadlocks Coil. */
    private fun <T> withAndroidMain(block:suspend ()->T):T {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val work=scope.async {block()};val deadline=System.nanoTime()+60_000_000_000L
        try {
            while(!work.isCompleted && System.nanoTime()<deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }
            assertTrue("Real Coil local-file export did not finish within 60 seconds",work.isCompleted)
            // Await only a completed deferred, never block the Android main looper on pending IO.
            return runBlocking {work.await()}
        } finally {scope.cancel()}
    }

    @Test fun actualLocalFilesLoadThroughExportCreateThenSaveAndReopen()=withAndroidMain {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val directory=File(app.cacheDir,"p36-export-${System.nanoTime()}").apply {check(mkdirs())}
        val files=mutableListOf<File>();val photo=person();val top=top();val pants=pants();val shoes=shoes()
        fun local(name:String,bitmap:Bitmap):String {
            val file=File(directory,name);files+=file
            file.outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
            return Uri.fromFile(file).toString()
        }
        try {
            val photoUri=local("synthetic-person.png",photo)
            val layers=listOf(garment("top","상의",top,"반팔 상의") to top,
                garment("pants","하의",pants,"긴바지") to pants,garment("shoes","신발",shoes) to shoes).map { (g,b) ->
                g.copy(uri=local("synthetic-${g.item.id}.png",b))
            }
            val snapshot=ManualPhotoSnapshot(photoUri,42L,size,layers,1f,body(),automatic=true)
            // Real Coil local-file decoding and real renderer; no injected loader/result or network.
            val result=ManualPhotoExport.create(app,snapshot)
            assertEquals(snapshot,result.input);assertTrue(result.reports.isNotEmpty())
            assertFalse(result.original.contentEquals(result.png))
            val original=decode(result.original);val decoded=decode(result.png)
            assertEquals(240,decoded.width);assertEquals(480,decoded.height);assertTrue(photo.sameAs(original))
            fun changes(left:Int,top:Int,right:Int,bottom:Int):Int {
                var count=0;for(y in top..bottom)for(x in left..right)if(decoded.getPixel(x,y)!=photo.getPixel(x,y))count++
                return count
            }
            assertTrue(changes(105,165,135,205)>100)
            assertTrue(changes(80,330,97,350)>20);assertTrue(changes(143,330,160,350)>20)
            assertTrue(changes(45,420,96,461)>10);assertTrue(changes(144,420,195,461)>10)
            val output=File(directory,"new-composite.png");files+=output
            LocalVtonRepository.save(app,result.png,Uri.fromFile(output),true)
            val reopened=decode(LocalVtonRepository.openSavedImage(app,Uri.fromFile(output)))
            assertTrue(decoded.sameAs(reopened))
            evidence("export-create",photo,layers.zip(listOf(top,pants,shoes)),result.png)
            original.recycle();decoded.recycle();reopened.recycle()
        } finally {
            files.forEach {it.delete()};directory.delete()
            photo.recycle();top.recycle();pants.recycle();shoes.recycle()
        }
    }

    @Test fun backgroundMaskAndReplacementAreNotOpaqueRectangles() {
        val photo=person();val opaque=top(true);val transparent=top()
        assertTrue(LocalDrapeCompositor.mask(opaque).removedBorder)
        assertFalse(LocalDrapeCompositor.mask(transparent).removedBorder)
        val first=LocalDrapeCompositor.render(photo,listOf(garment("a","상의",opaque) to opaque),size,1f,body())
        val recolored=transparent.copy(Bitmap.Config.ARGB_8888,true)
        for(y in 0 until recolored.height)for(x in 0 until recolored.width){val c=recolored.getPixel(x,y);if(Color.alpha(c)>0)recolored.setPixel(x,y,Color.argb(Color.alpha(c),160,60,70))}
        val next=LocalDrapeCompositor.render(photo,listOf(garment("b","상의",recolored) to recolored),size,1f,body())
        assertFalse(first.png.contentEquals(next.png));evidence("replacement",photo,listOf(garment("b","상의",recolored) to recolored),next.png)
        assertEquals(photo.getPixel(10,240),decode(first.png).getPixel(10,240))
    }

    @Test fun missingToeSingleShoeCrossedLegAndComplexBackgroundFailClosed() {
        val photo=person();val s=shoes();val g=garment("shoes","신발",s)
        val normal=body();val noToe=normal.copy(left=normal.left!!.copy(toe=null))
        assertThrows(LocalDrapeFailure::class.java){LocalDrapeCompositor.render(photo,listOf(g to s),size,1f,noToe)}
        val single=shoes(true)
        assertThrows(LocalDrapeFailure::class.java){LocalDrapeCompositor.render(photo,listOf(garment("single","신발",single) to single),size,1f,normal)}
        val b=pants()
        assertThrows(LocalDrapeFailure::class.java){LocalDrapeCompositor.render(photo,listOf(garment("shorts","하의",b,"반바지") to b),size,1f,normal)}
        val compact=Bitmap.createScaledBitmap(b,b.width,b.height/2,false)
        assertThrows(LocalDrapeFailure::class.java){LocalDrapeCompositor.render(photo,listOf(garment("unlabeled-short","하의",compact) to compact),size,1f,normal)}
        val outer=top();val unsupportedOuter=garment("coat","상의",outer).let {it.copy(item=it.item.copy(category="아우터",subCategory="트렌치코트"))}
        assertThrows(LocalDrapeFailure::class.java){LocalDrapeCompositor.render(photo,listOf(unsupportedOuter to outer),size,1f,normal)}
        val crossed=normal.copy(left=normal.left!!.copy(ankle=Offset(190f,425f)))
        assertThrows(LocalDrapeFailure::class.java){LocalDrapeCompositor.render(photo,listOf(garment("pants","하의",b) to b),size,1f,crossed)}
        val background=image(80,80,Color.RED);for(y in 0 until 80)for(x in 0 until 80)if((x+y)%4<2)background.setPixel(x,y,Color.BLUE)
        assertThrows(LocalDrapeFailure::class.java){LocalDrapeCompositor.mask(background)}
    }

    @Test fun userAdjustmentChangesWarpedOutputAndExtremeScaleIsBlocked() {
        val photo=person();val t=top();val base=garment("top","상의",t)
        val first=LocalDrapeCompositor.render(photo,listOf(base to t),size,1f,body())
        val moved=base.copy(center=base.center+Offset(7f,5f),scale=1.08f,rotation=base.rotation+3f)
        val second=LocalDrapeCompositor.render(photo,listOf(moved to t),size,1f,body())
        assertFalse(first.png.contentEquals(second.png))
        assertThrows(LocalDrapeFailure::class.java){LocalDrapeCompositor.render(photo,listOf(base.copy(scale=8f) to t),size,1f,body())}
    }

    @Test fun overlappingFeetCrossedKneesAndInvalidFaceOrPlacementAreRejected() {
        val photo=person();val normal=body();val s=shoes();val shoe=garment("shoes","신발",s)
        val identicalFeet=normal.copy(right=normal.right!!.copy(heel=normal.left!!.heel,toe=normal.left!!.toe,ankle=normal.left!!.ankle!!+Offset(1f,0f)))
        assertThrows(LocalDrapeFailure::class.java){LocalDrapeCompositor.render(photo,listOf(shoe to s),size,1f,identicalFeet)}
        val nearFeet=normal.copy(right=normal.right!!.copy(heel=normal.left!!.heel!!+Offset(1f,0f),toe=normal.left!!.toe!!+Offset(1f,0f),ankle=normal.left!!.ankle!!+Offset(1f,0f)))
        assertThrows(LocalDrapeFailure::class.java){LocalDrapeCompositor.render(photo,listOf(shoe to s),size,1f,nearFeet)}
        val pants=pants();val pant=garment("pants","하의",pants)
        val kneesCrossed=normal.copy(left=normal.left!!.copy(knee=Offset(175f,340f)),right=normal.right!!.copy(knee=Offset(65f,340f)))
        assertThrows(LocalDrapeFailure::class.java){LocalDrapeCompositor.render(photo,listOf(pant to pants),size,1f,kneesCrossed)}
        val top=top();val garment=garment("top","상의",top)
        for(invalid in listOf(normal.copy(faceCenter=null),normal.copy(faceWidth=null),
            normal.copy(faceCenter=Offset(Float.NaN,88f)),normal.copy(faceCenter=Offset(500f,88f)),
            normal.copy(faceWidth=Float.POSITIVE_INFINITY),normal.copy(faceWidth=-5f),normal.copy(faceWidth=400f))) {
            assertThrows(LocalDrapeFailure::class.java){LocalDrapeCompositor.render(photo,listOf(garment to top),size,1f,invalid)}
        }
        assertThrows(LocalDrapeFailure::class.java){LocalDrapeCompositor.render(photo,listOf(garment.copy(baseWidth=-garment.baseWidth,scale=-1f) to top),size,1f,normal)}
        assertThrows(LocalDrapeFailure::class.java){LocalDrapeCompositor.render(photo,listOf(garment.copy(aspect=Float.NaN) to top),size,1f,normal)}
        val wrongAspect=normal.copy(imageWidth=252) // 5% mismatch, previously below the absolute .035 gate.
        assertThrows(LocalDrapeFailure::class.java){LocalDrapeCompositor.render(photo,listOf(garment to top),size,1f,wrongAspect)}
    }
}
