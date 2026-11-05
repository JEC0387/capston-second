package com.jeiu.fashionproject.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.jeiu.fashionproject.ui.viewmodel.FittingPoseDetector
import java.io.ByteArrayOutputStream
import kotlin.math.*

internal class LocalDrapeFailure(message: String) : IllegalArgumentException(message)
internal data class DrapeRenderResult(val png: ByteArray, val reports: List<String>)

/** Local, mask/landmark guided deformation. No model inference, inpainting or semantic parsing.
 * Every requested garment is validated before output; unsupported inputs never silently become a
 * successful rectangular overlay. Alpha topology/landmarks are heuristics, not measured body shape.
 */
internal object LocalDrapeCompositor {
    private fun reject(message: String): Nothing = throw LocalDrapeFailure(message)
    private const val GRID_X = 28
    private const val GRID_Y = 36

    internal data class Mask(val width: Int, val height: Int, val pixels: IntArray,
        val left: Int, val top: Int, val right: Int, val bottom: Int, val removedBorder: Boolean) {
        val spanX get() = (right-left+1).toFloat()
        val spanY get() = (bottom-top+1).toFloat()
        fun point(x: Float, y: Float) = Offset(left+x*spanX, top+y*spanY)
        fun alpha(x: Int, y: Int) = if(x in 0 until width && y in 0 until height) Color.alpha(pixels[y*width+x]) else 0
        fun runs(y: Int): List<IntRange> {
            val result=mutableListOf<IntRange>();var start=-1
            for(x in left..right) {
                if(alpha(x,y)>=96 && start<0) start=x
                if(start>=0 && (alpha(x,y)<96 || x==right)) {
                    val end=if(alpha(x,y)<96) x-1 else x
                    if(end-start+1>=maxOf(2,(spanX*.025f).toInt())) result+=start..end
                    start=-1
                }
            }
            return result
        }
    }

    /** Opaque input: only a near-uniform border connected background is removable. */
    internal fun mask(bitmap: Bitmap): Mask {
        if(bitmap.isRecycled || bitmap.width<12 || bitmap.height<12 || bitmap.width.toLong()*bitmap.height>4_200_000)
            reject("의류 이미지의 크기를 확인해주세요.")
        val w=bitmap.width;val h=bitmap.height;val pixels=IntArray(w*h)
        bitmap.getPixels(pixels,0,w,0,0,w,h)
        var removed=false
        if(pixels.count { Color.alpha(it)<32 } < pixels.size*.03) {
            val border=mutableListOf<Int>()
            for(x in 0 until w) {border+=pixels[x];border+=pixels[(h-1)*w+x]}
            for(y in 1 until h-1) {border+=pixels[y*w];border+=pixels[y*w+w-1]}
            fun median(channel:(Int)->Int)=border.map(channel).sorted()[border.size/2]
            val r=median(Color::red);val g=median(Color::green);val b=median(Color::blue)
            fun background(c:Int)=abs(Color.red(c)-r)<=14 && abs(Color.green(c)-g)<=14 && abs(Color.blue(c)-b)<=14
            if(border.count(::background)<border.size*.98) reject("의류 배경이 복잡해요. 배경을 제거한 투명 PNG를 선택해주세요.")
            val queue=IntArray(w*h);val seen=BooleanArray(w*h);var tail=0;var head=0
            fun add(i:Int) {if(!seen[i] && background(pixels[i])) {seen[i]=true;queue[tail++]=i}}
            for(x in 0 until w) {add(x);add((h-1)*w+x)}
            for(y in 0 until h) {add(y*w);add(y*w+w-1)}
            while(head<tail) {
                val i=queue[head++];val x=i%w;val y=i/w;pixels[i]=0
                if(x>0)add(i-1);if(x<w-1)add(i+1);if(y>0)add(i-w);if(y<h-1)add(i+w)
            }
            if(tail<pixels.size*.08 || tail>pixels.size*.94) reject("배경과 의류 경계를 구분하지 못했어요. 투명 의류 PNG가 필요해요.")
            removed=true
        }
        var l=w;var t=h;var r=-1;var b=-1;var count=0
        for(y in 0 until h)for(x in 0 until w)if(Color.alpha(pixels[y*w+x])>=96) {
            count++;l=min(l,x);r=max(r,x);t=min(t,y);b=max(b,y)
        }
        if(count<pixels.size*.04 || count>pixels.size*.94 || r-l<8 || b-t<8)
            reject("의류 윤곽이 불분명해요. 옷 전체가 보이는 투명 이미지를 선택해주세요.")
        return Mask(w,h,pixels,l,t,r,b,removed)
    }

    private data class Anchor(val source: Offset, val target: Offset)
    private class Warp(anchors: List<Anchor>, width: Int, height: Int) {
        private val controls=anchors.map {Offset(it.source.x/width,it.source.y/height)}
        private val n=controls.size
        private fun basis(r:Double)=if(r<1e-12)0.0 else r*ln(r)
        private fun solve(values:List<Double>):DoubleArray {
            val a=Array(n+3) {DoubleArray(n+4)}
            for(i in 0 until n) {
                for(j in 0 until n) {val d=controls[i]-controls[j];a[i][j]=basis((d.x*d.x+d.y*d.y).toDouble())}
                a[i][i]+=1e-6
                a[i][n]=1.0;a[i][n+1]=controls[i].x.toDouble();a[i][n+2]=controls[i].y.toDouble()
                a[n][i]=1.0;a[n+1][i]=a[i][n+1];a[n+2][i]=a[i][n+2];a[i][n+3]=values[i]
            }
            for(c in 0 until n+3) {
                val pivot=(c until n+3).maxBy {abs(a[it][c])}
                if(abs(a[pivot][c])<1e-10) reject("의류 기준점이 겹쳐 자동 변형할 수 없어요.")
                val row=a[c];a[c]=a[pivot];a[pivot]=row
                val divisor=a[c][c];for(j in c until n+4)a[c][j]/=divisor
                for(i in 0 until n+3)if(i!=c) {val factor=a[i][c];for(j in c until n+4)a[i][j]-=factor*a[c][j]}
            }
            return DoubleArray(n+3) {a[it][n+3]}
        }
        private val x=solve(anchors.map {it.target.x.toDouble()})
        private val y=solve(anchors.map {it.target.y.toDouble()})
        fun at(u:Float,v:Float):Offset {
            fun value(c:DoubleArray):Float {
                var value=c[n]+c[n+1]*u+c[n+2]*v
                for(i in 0 until n) {val dx=u-controls[i].x;val dy=v-controls[i].y;value+=c[i]*basis((dx*dx+dy*dy).toDouble())}
                return value.toFloat()
            }
            return Offset(value(x),value(y))
        }
    }

    /** Visible clothing outline on a near-uniform backdrop, NOT semantic body segmentation.
     * Joint centres are not silhouette edges. Only bounded, contiguous, stable evidence may
     * enlarge a fit; reaching a search cap or image edge is uncertainty, never a measured edge. */
    private class PhotoOutline private constructor(val photo:Bitmap,val scale:Float,val offset:Offset,val background:Int) {
        val step=max(.25f,scale)
        var supportedSections=0;private set
        private fun foreground(point:Offset):Boolean? {
            val x=((point.x-offset.x)/scale).roundToInt();val y=((point.y-offset.y)/scale).roundToInt()
            if(x !in 0 until photo.width || y !in 0 until photo.height)return null
            val color=photo.getPixel(x,y)
            return Color.alpha(color)>=240 && maxOf(abs(Color.red(color)-Color.red(background)),
                abs(Color.green(color)-Color.green(background)),abs(Color.blue(color)-Color.blue(background)))>24
        }
        private fun edge(seed:Offset,direction:Offset,limit:Float):Float? {
            if(foreground(seed)!=true)return null
            var distance=step
            while(distance<=limit) {
                val present=foreground(seed+direction*distance) ?: return null
                if(!present)return distance
                distance+=step
            }
            return null
        }
        fun span(center:Offset,direction:Offset,maxWidth:Float):Pair<Offset,Offset>? {
            if(!maxWidth.isFinite() || maxWidth<step*4)return null
            val across=Offset(-direction.y,direction.x)
            val before=mutableListOf<Float>();val after=mutableListOf<Float>()
            for(shift in listOf(-step,0f,step)) {
                val seed=center+across*shift
                before+=edge(seed,direction*-1f,maxWidth*.75f) ?: return null
                after+=edge(seed,direction,maxWidth*.75f) ?: return null
            }
            val low=before.sorted()[1];val high=after.sorted()[1];val width=low+high
            if(width !in step*4..maxWidth || max(low,high)>min(low,high)*3f ||
                before.max()-before.min()>max(step*3,width*.18f) ||
                after.max()-after.min()>max(step*3,width*.18f))return null
            supportedSections++
            return center-direction*low to center+direction*high
        }
        fun toeBoundary(toe:Offset,direction:Offset,maxExtension:Float):Offset? {
            val across=Offset(-direction.y,direction.x)
            val distances=listOf(-step,0f,step).map {
                edge(toe+across*it,direction,maxExtension) ?: return null
            }
            if(distances.max()-distances.min()>step*3)return null
            supportedSections++
            return toe+direction*distances.sorted()[1]
        }
        companion object {
            fun create(photo:Bitmap,size:Size):PhotoOutline? {
                if(photo.width<8 || photo.height<8)return null
                val border=mutableListOf<Int>()
                for(i in 0..31) {
                    val x=i*(photo.width-1)/31;val y=i*(photo.height-1)/31
                    border+=photo.getPixel(x,0);border+=photo.getPixel(x,photo.height-1)
                    border+=photo.getPixel(0,y);border+=photo.getPixel(photo.width-1,y)
                }
                fun median(channel:(Int)->Int)=border.map(channel).sorted()[border.size/2]
                val r=median(Color::red);val g=median(Color::green);val b=median(Color::blue)
                if(border.count {Color.alpha(it)>=240 && abs(Color.red(it)-r)<=12 &&
                        abs(Color.green(it)-g)<=12 && abs(Color.blue(it)-b)<=12}<border.size*.98)return null
                val scale=min(size.width/photo.width,size.height/photo.height)
                return PhotoOutline(photo,scale,Offset((size.width-photo.width*scale)/2,(size.height-photo.height*scale)/2),Color.rgb(r,g,b))
            }
        }
    }
    private data class Person(val left:FittingPoseDetector.SidePoints, val right:FittingPoseDetector.SidePoints,
        val map:(Offset)->Offset, val shoulderWidth:Float, val hipWidth:Float,val outline:PhotoOutline?)
    private fun person(body:FittingPoseDetector.BodyPoints,size:Size,photo:Bitmap):Person {
        if(body.imageWidth<=0 || body.imageHeight<=0)reject("사진 크기와 인체 좌표를 확인할 수 없어요.")
        val one=body.left ?: reject("좌우 인체 기준점이 부족해요. 정면 전신 사진을 선택해주세요.")
        val two=body.right ?: reject("좌우 인체 기준점이 부족해요. 정면 전신 사진을 선택해주세요.")
        val s1=one.shoulder ?: reject("양쪽 어깨가 보이는 사진이 필요해요.")
        val s2=two.shoulder ?: reject("양쪽 어깨가 보이는 사진이 필요해요.")
        val h1=one.hip ?: reject("양쪽 골반이 보이는 사진이 필요해요.")
        val h2=two.hip ?: reject("양쪽 골반이 보이는 사진이 필요해요.")
        fun valid(p:Offset)=p.x.isFinite()&&p.y.isFinite()&&p.x in 0f..body.imageWidth.toFloat()&&p.y in 0f..body.imageHeight.toFloat()
        listOf(one,two).forEach { side -> listOfNotNull(side.shoulder,side.elbow,side.wrist,side.hip,side.knee,side.ankle,side.heel,side.toe).forEach {if(!valid(it))reject("인체 좌표가 사진 범위를 벗어났어요.")} }
        if(body.faceCenter==null || body.faceWidth==null)
            reject("얼굴 보존 기준점이 부족해 자동 합성을 적용하지 않았어요. 수동 배치를 사용해주세요.")
        if(body.faceCenter?.let { !valid(it) }==true || body.faceWidth?.let {
                !it.isFinite() || it<=0 || it>min(body.imageWidth,body.imageHeight) || it>(s1-s2).getDistance()*1.2f
            }==true)reject("얼굴 보존 기준점이 올바르지 않아 자동 합성을 중단했어요.")
        val left=if(s1.x<s2.x)one else two;val right=if(left===one)two else one
        if(left.hip!!.x>=right.hip!!.x || abs(s1.y-s2.y)>(s1-s2).getDistance()*.4f ||
            (h1.y+h2.y-s1.y-s2.y)/2 < (s1-s2).getDistance()*.45f)
            reject("몸이 겹치거나 크게 기울어 자동 변형하기 어려워요. 정면 사진을 사용해주세요.")
        val scale=min(size.width/body.imageWidth,size.height/body.imageHeight)
        val offset=Offset((size.width-body.imageWidth*scale)/2,(size.height-body.imageHeight*scale)/2)
        return Person(left,right,{offset+it*scale},(s1-s2).getDistance()*scale,(h1-h2).getDistance()*scale,PhotoOutline.create(photo,size))
    }
    private fun requirePoint(point:Offset?,name:String)=point ?: reject("${name} 기준점이 부족해 자동 착장을 적용하지 않았어요.")
    private fun mix(a:Offset,b:Offset,t:Float)=a+(b-a)*t
    private fun normal(direction:Offset):Offset {val length=direction.getDistance();if(length<1f)reject("인체 기준점이 너무 가까워요.");return Offset(-direction.y/length,direction.x/length)}
    private fun torsoSection(p:Person,left:Offset,right:Offset,sampleY:Float=(left.y+right.y)/2):Pair<Offset,Offset> {
        val measured=p.outline?.span(Offset((left.x+right.x)/2,sampleY),Offset(1f,0f),p.shoulderWidth*1.7f)
            ?: return left to right
        return Offset(min(left.x,measured.first.x),left.y) to Offset(max(right.x,measured.second.x),right.y)
    }
    private fun waistSection(p:Person):Pair<Offset,Offset> {
        val sl=p.map(p.left.shoulder!!);val sr=p.map(p.right.shoulder!!)
        val hl=p.map(p.left.hip!!);val hr=p.map(p.right.hip!!)
        // Hip joints can lie below the existing top or inside separated thighs. Sample the
        // visible torso just above that level; do not mistake the empty crotch for a body edge.
        val sampleY=mix(mix(sl,sr,.5f),mix(hl,hr,.5f),.90f).y
        return torsoSection(p,hl-Offset(p.hipWidth*.12f,0f),hr+Offset(p.hipWidth*.12f,0f),sampleY)
    }
    private fun legSection(p:Person,center:Offset,half:Float):Pair<Offset,Offset> {
        val measured=p.outline?.span(center,Offset(1f,0f),p.shoulderWidth*.60f)
        return Offset(min(center.x-half,measured?.first?.x ?: center.x-half),center.y) to
            Offset(max(center.x+half,measured?.second?.x ?: center.x+half),center.y)
    }
    private fun torsoAnchors(mask:Mask,p:Person,skirt:Boolean=false):MutableList<Anchor> {
        val sl=p.map(p.left.shoulder!!);val sr=p.map(p.right.shoulder!!)
        val hl=p.map(p.left.hip!!);val hr=p.map(p.right.hip!!)
        val sectionsBefore=p.outline?.supportedSections ?: 0
        val waistSection=waistSection(p)
        val a=mutableListOf<Anchor>()
        if(skirt) {
            val kl=p.map(requirePoint(p.left.knee,"양쪽 무릎"));val kr=p.map(requirePoint(p.right.knee,"양쪽 무릎"))
            val bottomY=max(kl.y,kr.y);val center=(hl.x+hr.x)/2
            val topY=(mask.top+mask.spanY*.04f).toInt()
            val waist=mask.runs(topY)
            if(waist.size!=1 || bottomY<max(hl.y,hr.y)+p.hipWidth*.4f)reject("치마 허리 윤곽이나 무릎 길이를 확인하지 못했어요.")
            val leftLeg=legSection(p,kl,p.hipWidth*.27f);val rightLeg=legSection(p,kr,p.hipWidth*.27f)
            val outlineAvailable=(p.outline?.supportedSections ?: 0)>sectionsBefore
            val baselineHalf=p.hipWidth*.85f
            val half=if(outlineAvailable)max(baselineHalf,maxOf((waistSection.second.x-waistSection.first.x)*.68f,
                center-leftLeg.first.x,rightLeg.second.x-center).coerceAtMost(p.shoulderWidth*1.1f))else baselineHalf
            a+=Anchor(Offset(waist.single().first.toFloat(),topY.toFloat()),waistSection.first-Offset(0f,p.hipWidth*.12f))
            a+=Anchor(Offset(waist.single().last.toFloat(),topY.toFloat()),waistSection.second-Offset(0f,p.hipWidth*.12f))
            a+=Anchor(mask.point(0f,1f),Offset(center-half,bottomY))
            a+=Anchor(mask.point(1f,1f),Offset(center+half,bottomY))
            a+=Anchor(mask.point(.5f,.5f),Offset(center,(hl.y+hr.y+2*bottomY)/4))
        } else {
            val bottom=mask.runs((mask.top+mask.spanY*.87f).toInt())
            if(bottom.size!=1)reject("상의 몸통 윤곽을 구분하지 못했어요. 펼친 상의 이미지를 사용해주세요.")
            val row=bottom.single();val x1=row.first.toFloat();val x2=row.last.toFloat()
            if(x2-x1<mask.spanX*.28f || x2-x1>mask.spanX*.92f)reject("상의 소매와 몸통을 구분하기 어려워요. 투명한 펼친 상의를 사용해주세요.")
            val shoulderY=mask.top+mask.spanY*.16f
            a+=Anchor(Offset(x1,shoulderY),sl-Offset(p.shoulderWidth*.08f,0f))
            a+=Anchor(Offset(x2,shoulderY),sr+Offset(p.shoulderWidth*.08f,0f))
            val middle=torsoSection(p,mix(sl,hl,.58f)-Offset(p.shoulderWidth*.06f,0f),mix(sr,hr,.58f)+Offset(p.shoulderWidth*.06f,0f))
            a+=Anchor(Offset(x1,mask.top+mask.spanY*.58f),middle.first)
            a+=Anchor(Offset(x2,mask.top+mask.spanY*.58f),middle.second)
            a+=Anchor(Offset(x1,mask.bottom.toFloat()),waistSection.first)
            a+=Anchor(Offset(x2,mask.bottom.toFloat()),waistSection.second)
            a+=Anchor(mask.point(.5f,0f),mix(sl,sr,.5f)-Offset(0f,p.shoulderWidth*.15f))
        }
        return a
    }
    private fun topAnchors(mask:Mask,p:Person,longSleeve:Boolean):List<Anchor> {
        val a=torsoAnchors(mask,p)
        for((isLeft,side) in listOf(true to p.left,false to p.right)) {
            val shoulder=p.map(side.shoulder!!)
            val elbow=p.map(requirePoint(side.elbow,"양쪽 팔꿈치"))
            val wrist=p.map(requirePoint(side.wrist,"양쪽 손목"))
            if(elbow.y<shoulder.y-p.shoulderWidth*.15f || (elbow-shoulder).getDistance()<p.shoulderWidth*.25f ||
                (wrist-elbow).getDistance()<p.shoulderWidth*.20f ||
                (isLeft && elbow.x>p.map(p.right.shoulder!!).x) || (!isLeft && elbow.x<p.map(p.left.shoulder!!).x))
                reject("팔이 크게 교차하거나 접혀 소매를 안전하게 변형하기 어려워요.")
            val x=if(isLeft)mask.left+mask.spanX*.055f else mask.right-mask.spanX*.055f
            val ys=(mask.top..mask.bottom).filter {mask.alpha(x.toInt(),it)>=96}
            if(ys.size<3)reject("소매 끝 윤곽을 확인하지 못했어요.")
            val end=if(longSleeve)wrist else mix(shoulder,elbow,.62f)
            val direction=end-shoulder
            val perpendicular=normal(direction)
            val half=p.shoulderWidth*(if(longSleeve).10f else .16f)
            // Normal has opposite image-y order on left/right descending arms.
            val low=if(perpendicular.y>=0)perpendicular else perpendicular*-1f
            a+=Anchor(Offset(x,ys.first().toFloat()),end-low*half)
            a+=Anchor(Offset(x,ys.last().toFloat()),end+low*half)
            if(longSleeve) {
                val hem=mask.runs((mask.top+mask.spanY*.87f).toInt()).single()
                val sourceElbowX=(x+(if(isLeft)hem.first else hem.last))/2f
                val elbowRows=(mask.top..mask.bottom).filter {mask.alpha(sourceElbowX.toInt(),it)>=96}
                if(elbowRows.size<3)reject("긴소매의 중간 윤곽을 확인하지 못했어요.")
                val bendNormal=normal(wrist-shoulder).let {if(it.y<0)it*-1f else it}
                a+=Anchor(Offset(sourceElbowX,elbowRows.first().toFloat()),elbow-bendNormal*p.shoulderWidth*.115f)
                a+=Anchor(Offset(sourceElbowX,elbowRows.last().toFloat()),elbow+bendNormal*p.shoulderWidth*.115f)
            }
        }
        return a
    }
    /** Measured source cross-sections are mapped monotonically. A global TPS can fold the
     * outer legs when the source crotch gap is much narrower than the target knee separation. */
    private fun pantsMapping(mask:Mask,p:Person):(Offset)->Offset {
        data class Row(val y:Float,val source:List<Float>,val target:List<Offset>)
        if(mask.spanY/mask.spanX<1.25f)reject("짧거나 폭이 넓어 긴바지로 확인되지 않는 윤곽이에요. 수동 합성을 사용해주세요.")
        val hl=p.map(p.left.hip!!);val hr=p.map(p.right.hip!!)
        val kl=p.map(requirePoint(p.left.knee,"양쪽 무릎"));val kr=p.map(requirePoint(p.right.knee,"양쪽 무릎"))
        val al=p.map(requirePoint(p.left.ankle,"양쪽 발목"));val ar=p.map(requirePoint(p.right.ankle,"양쪽 발목"))
        if(kl.x>=kr.x || al.x>=ar.x || kl.y<hl.y+p.hipWidth*.3f || kr.y<hr.y+p.hipWidth*.3f ||
            al.y<kl.y+p.hipWidth*.3f || ar.y<kr.y+p.hipWidth*.3f)
            reject("다리가 교차하거나 접혀 바지를 안전하게 정렬할 수 없어요.")
        val waistY=(mask.top+mask.spanY*.04f).toInt();val waist=mask.runs(waistY)
        if(waist.size!=1)reject("바지 허리 윤곽을 확인하지 못했어요.")
        val kneeY=(mask.top+mask.spanY*.56f).toInt();val ankleY=(mask.top+mask.spanY*.96f).toInt()
        // First persistent separated row, not an invented normalized crotch coordinate.
        val splitY=(waistY+2 until kneeY-3).firstOrNull { y ->
            (y..y+2).all { row -> val runs=mask.runs(row);runs.size==2 && runs[1].first-runs[0].last>=mask.spanX*.025f }
        } ?: reject("바지 가랑이와 좌우 다리 윤곽을 확인하지 못했어요.")
        fun source(y:Int):List<Float> {
            val runs=mask.runs(y)
            if(runs.size!=2)reject("바지의 좌우 다리를 구분하지 못했어요.")
            return listOf(runs[0].first,runs[0].last,runs[1].first,runs[1].last).map(Int::toFloat)
        }
        val visibleWaist=waistSection(p)
        val waistLeft=Offset(min(hl.x-p.hipWidth*.15f,visibleWaist.first.x),hl.y-p.hipWidth*.12f)
        val waistRight=Offset(max(hr.x+p.hipWidth*.15f,visibleWaist.second.x),hr.y-p.hipWidth*.12f)
        val leftKnee=legSection(p,kl,p.hipWidth*.27f);val rightKnee=legSection(p,kr,p.hipWidth*.27f)
        val leftAnkle=legSection(p,al,p.hipWidth*.20f);val rightAnkle=legSection(p,ar,p.hipWidth*.20f)
        val mid=mix(hl,hr,.5f);val crotchY=max(hl.y,hr.y)+p.hipWidth*.26f
        if(crotchY>=min(kl.y,kr.y)-p.hipWidth*.2f)reject("골반과 무릎 간격이 부족해요.")
        val rows=listOf(
            Row(waistY.toFloat(),listOf(0f,1f/3,2f/3,1f).map {waist[0].first+(waist[0].last-waist[0].first)*it},
                listOf(0f,1f/3,2f/3,1f).map {mix(waistLeft,waistRight,it)}),
            Row(splitY.toFloat(),source(splitY),listOf(Offset(min(hl.x-p.hipWidth*.22f,visibleWaist.first.x),crotchY),
                Offset(mid.x-p.hipWidth*.08f,crotchY),Offset(mid.x+p.hipWidth*.08f,crotchY),Offset(max(hr.x+p.hipWidth*.22f,visibleWaist.second.x),crotchY))),
            Row(kneeY.toFloat(),source(kneeY),listOf(leftKnee.first,leftKnee.second,rightKnee.first,rightKnee.second)),
            Row(ankleY.toFloat(),source(ankleY),listOf(leftAnkle.first,leftAnkle.second,rightAnkle.first,rightAnkle.second))
        )
        for(row in rows)if(row.target.zipWithNext().any {(a,b)->a.x>=b.x})
            reject("다리 사이 간격이 부족해 바지 윤곽이 겹쳐요.")
        return { point ->
            val i=(rows.indexOfLast {it.y<=point.y}).coerceIn(0,rows.size-2)
            val a=rows[i];val b=rows[i+1];val t=(point.y-a.y)/(b.y-a.y)
            val xs=a.source.indices.map {a.source[it]+(b.source[it]-a.source[it])*t}
            val targets=a.target.indices.map {mix(a.target[it],b.target[it],t)}
            val j=xs.indexOfLast {it<=point.x}.coerceIn(0,2)
            val span=xs[j+1]-xs[j]
            if(span<=.1f)reject("바지 단면이 겹쳐 안전하게 변형할 수 없어요.")
            mix(targets[j],targets[j+1],(point.x-xs[j])/span)
        }
    }

    private fun vertices(mask:Mask,anchors:List<Anchor>,garment:WornGarment,body:FittingPoseDetector.BodyPoints,size:Size,slot:FitSlot,sourceMap:((Offset)->Offset)?=null):FloatArray {
        if(listOf(garment.baseWidth,garment.scale,garment.aspect).any { !it.isFinite() || it<=0f } ||
            !garment.center.x.isFinite() || !garment.center.y.isFinite())reject("의류 배치의 크기·중심 좌표가 올바르지 않아요.")
        val reference=photoAutoPlacement(slot,garment.item,garment.aspect,size,body) ?: reject("자동 배치 기준점을 확인하지 못했어요.")
        val factor=garment.baseWidth/reference.width*garment.scale
        if(!factor.isFinite() || factor !in .35f..2.5f || !garment.rotation.isFinite())reject("수동 배율이 너무 커서 자동 변형을 중단했어요.")
        val angle=Math.toRadians((garment.rotation-reference.rotation).toDouble());val c=cos(angle).toFloat();val s=sin(angle).toFloat()
        fun transform(point:Offset):Offset {val d=(point-reference.center)*factor;return garment.center+Offset(d.x*c-d.y*s,d.x*s+d.y*c)}
        val warp=if(sourceMap==null)Warp(anchors,mask.width,mask.height)else null
        val result=FloatArray((GRID_X+1)*(GRID_Y+1)*2)
        for(y in 0..GRID_Y)for(x in 0..GRID_X) {
            val point=transform(sourceMap?.invoke(Offset(x.toFloat()*mask.width/GRID_X,y.toFloat()*mask.height/GRID_Y)) ?: warp!!.at(x.toFloat()/GRID_X,y.toFloat()/GRID_Y));val i=(y*(GRID_X+1)+x)*2
            if(!point.x.isFinite() || !point.y.isFinite() || abs(point.x)>size.width*4 || abs(point.y)>size.height*4)
                reject("의류 변형 범위가 너무 커서 중단했어요.")
            result[i]=point.x;result[i+1]=point.y
        }
        fun point(x:Int,y:Int):Offset {val i=(y*(GRID_X+1)+x)*2;return Offset(result[i],result[i+1])}
        var supported=0
        for(y in 0 until GRID_Y)for(x in 0 until GRID_X) {
            // Inspect every source pixel covered by a cell: a narrow visible edge can miss
            // its center even though an inverted triangle would draw it onto another body part.
            val x0=(x*mask.width/GRID_X).coerceAtLeast(0);val x1=ceil((x+1f)*mask.width/GRID_X).toInt().coerceAtMost(mask.width-1)
            val y0=(y*mask.height/GRID_Y).coerceAtLeast(0);val y1=ceil((y+1f)*mask.height/GRID_Y).toInt().coerceAtMost(mask.height-1)
            var visible=false
            for(py in y0..y1)for(px in x0..x1)if(mask.alpha(px,py)>=96)visible=true
            if(!visible)continue
            supported++
            val a=point(x,y);val b=point(x+1,y);val c1=point(x,y+1);val d=point(x+1,y+1)
            fun area(u:Offset,v:Offset)=u.x*v.y-u.y*v.x
            if(area(b-a,c1-a)<=.01f || area(d-b,c1-b)<=.01f)reject("의류 메시가 접히는 자세예요. 수동 배치로 확인해주세요.")
            val horizontal=(b-a).getDistance();val vertical=(c1-a).getDistance()
            if(horizontal/vertical !in .06f..16f)reject("의류가 지나치게 늘어나 자동 변형을 중단했어요.")
        }
        if(supported<8)reject("의류 마스크가 너무 작아요.")
        return result
    }

    private data class Piece(val mask:Mask,val mesh:FloatArray,val report:String)
    private fun longSleevePieces(mask:Mask,p:Person,g:WornGarment,body:FittingPoseDetector.BodyPoints,size:Size,slot:FitSlot):List<Piece> {
        val hem=mask.runs((mask.top+mask.spanY*.87f).toInt())
        if(hem.size!=1)reject("긴소매 몸통을 구분하지 못했어요.")
        val leftX=hem[0].first;val rightX=hem[0].last
        val torso=Warp(torsoAnchors(mask,p),mask.width,mask.height)
        fun part(keep:(Int)->Boolean):Mask {
            val pixels=mask.pixels.copyOf();for(y in 0 until mask.height)for(x in 0 until mask.width)if(!keep(x))pixels[y*mask.width+x]=0
            return mask.copy(pixels=pixels)
        }
        val result=mutableListOf<Piece>()
        val trunk=part {it in leftX..rightX}
        result+=Piece(trunk,vertices(trunk,torsoAnchors(mask,p),g,body,size,slot),"긴소매 몸통: 목선·어깨·허리 메시")
        for((left,side) in listOf(true to p.left,false to p.right)) {
            val shoulder=p.map(side.shoulder!!);val elbow=p.map(requirePoint(side.elbow,"양쪽 팔꿈치"));val wrist=p.map(requirePoint(side.wrist,"양쪽 손목"))
            if(elbow.y<shoulder.y || wrist.y<elbow.y || (elbow-shoulder).getDistance()<p.shoulderWidth*.25f ||
                (wrist-elbow).getDistance()<p.shoulderWidth*.2f ||
                (left && max(elbow.x,wrist.x)>=p.map(p.left.hip!!).x) ||
                (!left && min(elbow.x,wrist.x)<=p.map(p.right.hip!!).x))
                reject("긴소매가 몸통과 겹치는 자세예요. 팔이 분리된 정면 사진을 사용해주세요.")
            val root=if(left)leftX else rightX
            val rootColumn=if(left)root-1 else root+1
            // End at the actual reliable cuff column; an interior percentage extrapolated
            // past the wrist and produced pointed fabric below the hands.
            val candidates=if(left)(mask.left until rootColumn).toList()else (mask.right downTo rootColumn+1).toList()
            val outer=candidates.firstOrNull { column ->
                (mask.top..mask.bottom).count {mask.alpha(column,it)>=96}>=maxOf(3,(mask.spanY*.12f).toInt())
            }?.toFloat() ?: reject("긴소매 끝단을 확인하지 못했어요.")
            fun extent(column:Int):Pair<Int,Int> {
                val rows=(mask.top..mask.bottom).filter {mask.alpha(column.coerceIn(0,mask.width-1),it)>=96}
                if(rows.size<3)reject("긴소매 윤곽을 구분하지 못했어요.")
                return rows.first() to rows.last()
            }
            val rootRows=extent(rootColumn)
            val rootTop=torso.at(root.toFloat()/mask.width,rootRows.first.toFloat()/mask.height)
            val rootBottom=torso.at(root.toFloat()/mask.width,rootRows.second.toFloat()/mask.height)
            val axisSign=if(left)-1f else 1f
            fun section(center:Offset,direction:Offset,half:Float):Pair<Offset,Offset> {
                // Preserve source orientation even when the forearm bends back toward the body.
                val across=normal(direction)*axisSign
                return center-across*half to center+across*half
            }
            val elbowSection=section(elbow,wrist-shoulder,p.shoulderWidth*.115f)
            val wristSection=section(wrist,wrist-elbow,p.shoulderWidth*.10f)
            val sleeve=part {if(left)it<leftX else it>rightX}
            val mapping:(Offset)->Offset={ point ->
                val t=(point.x-root)/(outer-root)
                val column=point.x.roundToInt().coerceIn(min(rootColumn,outer.toInt()),max(rootColumn,outer.toInt()))
                val rows=extent(column);val fraction=(point.y-rows.first)/(rows.second-rows.first).toFloat()
                val upper:Offset;val lower:Offset
                if(t<=.5f) {
                    upper=mix(rootTop,elbowSection.first,t*2);lower=mix(rootBottom,elbowSection.second,t*2)
                } else {
                    upper=mix(elbowSection.first,wristSection.first,(t-.5f)*2);lower=mix(elbowSection.second,wristSection.second,(t-.5f)*2)
                }
                mix(upper,lower,fraction)
            }
            result+=Piece(sleeve,vertices(sleeve,emptyList(),g,body,size,slot,mapping),"${if(left)"화면 왼팔" else "화면 오른팔"}: 팔꿈치·손목 경로의 분리 소매 메시")
        }
        return result
    }

    private fun shoePieces(mask:Mask,p:Person,g:WornGarment,body:FittingPoseDetector.BodyPoints,size:Size):List<Piece> {
        val leftHeel=p.map(requirePoint(p.left.heel,"양쪽 발뒤꿈치"));val rightHeel=p.map(requirePoint(p.right.heel,"양쪽 발뒤꿈치"))
        val leftToe=p.map(requirePoint(p.left.toe,"양쪽 발끝"));val rightToe=p.map(requirePoint(p.right.toe,"양쪽 발끝"))
        fun cross(a:Offset,b:Offset)=a.x*b.y-a.y*b.x
        fun pointSegment(point:Offset,a:Offset,b:Offset):Float {
            val direction=b-a;val squared=direction.x*direction.x+direction.y*direction.y
            if(squared<1f)return (point-a).getDistance()
            val delta=point-a;val t=((delta.x*direction.x+delta.y*direction.y)/squared).coerceIn(0f,1f)
            return (point-(a+direction*t)).getDistance()
        }
        val boxesOverlap=min(max(leftHeel.x,leftToe.x),max(rightHeel.x,rightToe.x))>=max(min(leftHeel.x,leftToe.x),min(rightHeel.x,rightToe.x)) &&
            min(max(leftHeel.y,leftToe.y),max(rightHeel.y,rightToe.y))>=max(min(leftHeel.y,leftToe.y),min(rightHeel.y,rightToe.y))
        val intersects=boxesOverlap && cross(leftToe-leftHeel,rightHeel-leftHeel)*cross(leftToe-leftHeel,rightToe-leftHeel)<=0f &&
            cross(rightToe-rightHeel,leftHeel-rightHeel)*cross(rightToe-rightHeel,leftToe-rightHeel)<=0f
        val separation=minOf(pointSegment(leftHeel,rightHeel,rightToe),pointSegment(leftToe,rightHeel,rightToe),
            pointSegment(rightHeel,leftHeel,leftToe),pointSegment(rightToe,leftHeel,leftToe))
        val requiredSeparation=((leftToe-leftHeel).getDistance()+(rightToe-rightHeel).getDistance())*.27f
        if(leftHeel.x>=rightHeel.x || leftToe.x>=rightToe.x || intersects || separation<=maxOf(2f,requiredSeparation))
            reject("좌우 발이 겹치거나 방향이 교차해 신발을 개별 정렬할 수 없어요.")
        data class Foot(val heel:Offset,val toe:Offset,val half:Float)
        val feet=listOf(p.left,p.right).map {side ->
            val heel=p.map(requirePoint(side.heel,"양쪽 발뒤꿈치"));val toe=p.map(requirePoint(side.toe,"양쪽 발끝"))
            val ankle=p.map(requirePoint(side.ankle,"양쪽 발목"));val length=(toe-heel).getDistance()
            if(length<p.shoulderWidth*.07f || length>p.shoulderWidth*.65f || (ankle-heel).getDistance()>length*1.5f)
                reject("발끝 방향이나 발목 좌표가 불확실해 신발 자동 정렬을 중단했어요.")
            val direction=(toe-heel)/length
            val tip=p.outline?.toeBoundary(toe,direction,p.shoulderWidth*.65f-length) ?: toe
            val across=Offset(-direction.y,direction.x).let {if(it.x<0)it*-1f else it}
            val center=mix(heel,tip,.5f)
            val span=p.outline?.span(center,across,p.shoulderWidth*.45f)
            val half=max(length*.32f,span?.let {max((it.first-center).getDistance(),(it.second-center).getDistance())} ?: 0f)
            Foot(heel,tip,half)
        }
        // Extending toes and widths can make previously disjoint short landmark segments
        // collide. Recheck the expanded footprints, not only the original pose segments.
        val a=feet[0];val b=feet[1]
        val expandedSeparation=minOf(pointSegment(a.heel,b.heel,b.toe),pointSegment(a.toe,b.heel,b.toe),
            pointSegment(b.heel,a.heel,a.toe),pointSegment(b.toe,a.heel,a.toe))
        val expandedBoxes=min(max(a.heel.x,a.toe.x),max(b.heel.x,b.toe.x))>=max(min(a.heel.x,a.toe.x),min(b.heel.x,b.toe.x)) &&
            min(max(a.heel.y,a.toe.y),max(b.heel.y,b.toe.y))>=max(min(a.heel.y,a.toe.y),min(b.heel.y,b.toe.y))
        val expandedCross=expandedBoxes && cross(a.toe-a.heel,b.heel-a.heel)*cross(a.toe-a.heel,b.toe-a.heel)<=0f &&
            cross(b.toe-b.heel,a.heel-b.heel)*cross(b.toe-b.heel,a.toe-b.heel)<=0f
        if(a.toe.x>=b.toe.x || expandedCross || expandedSeparation<=a.half+b.half+max(1f,p.outline?.step ?: 1f))
            reject("확인한 신발 윤곽이 서로 겹쳐 자동 정렬을 중단했어요. 수동 배치로 확인해주세요.")
        val seen=BooleanArray(mask.width*mask.height);val components=mutableListOf<List<Int>>()
        val queue=IntArray(seen.size)
        for(seed in seen.indices)if(!seen[seed] && Color.alpha(mask.pixels[seed])>=96) {
            var head=0;var tail=1;queue[0]=seed;seen[seed]=true;val points=mutableListOf<Int>()
            while(head<tail) {
                val i=queue[head++];points+=i;val x=i%mask.width;val y=i/mask.width
                fun add(j:Int) {if(!seen[j] && Color.alpha(mask.pixels[j])>=96){seen[j]=true;queue[tail++]=j}}
                if(x>0)add(i-1);if(x<mask.width-1)add(i+1);if(y>0)add(i-mask.width);if(y<mask.height-1)add(i+mask.width)
            }
            if(points.size>mask.pixels.size*.025)components+=points
        }
        if(components.size!=2)reject(if(components.size==1)"신발 한 개의 좌우를 확정할 수 없어요. 분리된 한 쌍의 위에서 본 PNG를 사용해주세요." else "신발 두 개의 윤곽을 구분하지 못했어요.")
        val sorted=components.sortedBy {it.map {n->n%mask.width}.average()}
        return sorted.mapIndexed {index,component ->
            val (heel,toe,half)=feet[index]
            val l=component.minOf {it%mask.width};val r=component.maxOf {it%mask.width}
            val t=component.minOf {it/mask.width};val b=component.maxOf {it/mask.width}
            if((b-t).toFloat()/(r-l+1)<1.35f)reject("신발 방향이 모호해요. 발끝이 위쪽인 분리된 위에서 본 한 쌍을 사용해주세요.")
            fun rowWidth(y:Int)=component.count {it/mask.width==y}
            val topWidth=(t+(b-t)*.15f).toInt().let(::rowWidth)
            val bottomWidth=(t+(b-t)*.85f).toInt().let(::rowWidth)
            if(topWidth<bottomWidth*1.15f)reject("신발의 발끝·뒤꿈치를 구분하지 못했어요. 발끝이 위쪽인 이미지를 사용해주세요.")
            val pixels=IntArray(mask.pixels.size);component.forEach {pixels[it]=mask.pixels[it]}
            val part=Mask(mask.width,mask.height,pixels,l,t,r,b,mask.removedBorder)
            val direction=toe-heel;val n=normal(direction).let {if(it.x<0)it*-1f else it}
            val anchors=listOf(Anchor(Offset(l.toFloat(),t.toFloat()),toe-n*half),Anchor(Offset(r.toFloat(),t.toFloat()),toe+n*half),
                Anchor(Offset(l.toFloat(),b.toFloat()),heel-n*half*.72f),Anchor(Offset(r.toFloat(),b.toFloat()),heel+n*half*.72f))
            // Source image y runs toe -> heel. If target toe is below heel, avoid reflection by
            // orienting source lateral axes consistently (a genuine rotation, not a mirrored shoe).
            val oriented=if((toe-heel).y>0)anchors.mapIndexed {i,a->a.copy(target=anchors[if(i%2==0)i+1 else i-1].target)} else anchors
            Piece(part,vertices(part,oriented,g,body,size,FitSlot.SHOES),"${if(index==0)"화면 왼발" else "화면 오른발"}: 발뒤꿈치·발끝 기준 개별 회전/배율 적용")
        }
    }

    private fun prepare(photo:Bitmap,piece:Piece,size:Size):Bitmap {
        val m=piece.mask;val pixels=m.pixels.copyOf()
        // Bounded global luminance correction; hue/channel ratios remain near the supplied garment.
        val fit=min(size.width/photo.width,size.height/photo.height);val ox=(size.width-photo.width*fit)/2;val oy=(size.height-photo.height*fit)/2
        var clothing=0.0;var person=0.0;var samples=0
        for(y in 1 until GRID_Y step 3)for(x in 1 until GRID_X step 3) {
            val sx=x*m.width/GRID_X;val sy=y*m.height/GRID_Y
            if(m.alpha(sx,sy)<200)continue
            val i=(y*(GRID_X+1)+x)*2;val px=((piece.mesh[i]-ox)/fit).toInt();val py=((piece.mesh[i+1]-oy)/fit).toInt()
            if(px !in 0 until photo.width || py !in 0 until photo.height)continue
            fun luminance(c:Int)=.2126*Color.red(c)+.7152*Color.green(c)+.0722*Color.blue(c)
            clothing+=luminance(m.pixels[sy*m.width+sx]);person+=luminance(photo.getPixel(px,py));samples++
        }
        val gain=if(samples>=4 && clothing>1)(person/clothing).coerceIn(.94,1.06) else 1.0
        for(y in 0 until m.height)for(x in 0 until m.width) {
            val i=y*m.width+x;val c=pixels[i];val alpha=Color.alpha(c)
            if(alpha==0)continue
            val edge=minOf(m.alpha(x-1,y),m.alpha(x+1,y),m.alpha(x,y-1),m.alpha(x,y+1))<32
            val a=if(edge)(alpha*.65).roundToInt() else alpha
            pixels[i]=Color.argb(a,(Color.red(c)*gain).roundToInt().coerceIn(0,255),(Color.green(c)*gain).roundToInt().coerceIn(0,255),(Color.blue(c)*gain).roundToInt().coerceIn(0,255))
        }
        return Bitmap.createBitmap(pixels,m.width,m.height,Bitmap.Config.ARGB_8888)
    }

    private fun preserveFaceAndHands(output:Bitmap,original:Bitmap,p:Person,body:FittingPoseDetector.BodyPoints,scale:Float):Int {
        val face=body.faceCenter;val faceWidth=body.faceWidth
        val masks=mutableListOf<Pair<RectF,Boolean>>()
        if(face!=null && faceWidth!=null && faceWidth>0) {
            val center=p.map(face)*scale;val radius=faceWidth*min(output.width.toFloat()/body.imageWidth,output.height.toFloat()/body.imageHeight)
            val shoulderY=min(p.map(p.left.shoulder!!).y,p.map(p.right.shoulder!!).y)*scale
            masks+=RectF(center.x-radius*.8f,center.y-radius*1.15f,center.x+radius*.8f,min(center.y+radius*.7f,shoulderY-p.shoulderWidth*scale*.05f)) to false
        }
        for(side in listOf(p.left,p.right)) {
            val wrist=side.wrist ?: continue;val elbow=side.elbow ?: continue
            val w=p.map(wrist)*scale;val e=p.map(elbow)*scale;val direction=w-e
            val center=w+direction*.12f;val radius=min(direction.getDistance()*.15f,p.shoulderWidth*scale*.09f)
            if(radius>=2)masks+=RectF(center.x-radius,center.y-radius,center.x+radius,center.y+radius) to true
        }
        var restored=0
        for((rect,skinOnly) in masks) {
            if(rect.width()<=0 || rect.height()<=0)continue
            for(y in max(0,rect.top.toInt())..min(output.height-1,rect.bottom.toInt()))for(x in max(0,rect.left.toInt())..min(output.width-1,rect.right.toInt())) {
                val dx=(x-rect.centerX())/(rect.width()/2);val dy=(y-rect.centerY())/(rect.height()/2)
                if(dx*dx+dy*dy>1)continue
                val color=original.getPixel(x,y);val r=Color.red(color);val g=Color.green(color);val b=Color.blue(color)
                // A conservative skin-like test only; hair, sleeves, gloves and dark/ambiguous
                // pixels are not guessed as foreground. This is explicitly reported as limited.
                val skin=r>55 && r>g && g>=b && r-b>15 && r-g<100
                if((!skinOnly || skin) && output.getPixel(x,y)!=color) {output.setPixel(x,y,color);restored++}
            }
        }
        return restored
    }

    fun render(photo:Bitmap,layers:List<Pair<WornGarment,Bitmap>>,size:Size,alpha:Float,body:FittingPoseDetector.BodyPoints):DrapeRenderResult {
        if(photo.isRecycled || !size.isUsableFittingSize() || layers.isEmpty() || layers.size>5 || !alpha.isFinite() || alpha !in .1f..1f)
            reject("사진·의류·캔버스·투명도를 확인해주세요.")
        val photoAspect=photo.width.toDouble()/photo.height;val poseAspect=body.imageWidth.toDouble()/body.imageHeight
        // A few decoded edge pixels may differ through sampling rounding; not a 7% portrait stretch.
        val aspectTolerance=minOf(.015,maxOf(.005,2.0/minOf(photo.width,photo.height,body.imageWidth,body.imageHeight).coerceAtLeast(1)))
        if(!poseAspect.isFinite() || poseAspect<=0 || abs(photoAspect/poseAspect-1)>aspectTolerance)
            reject("사진 방향과 인체 분석 좌표가 달라요. 사진을 다시 분석해주세요.")
        val p=person(body,size,photo);val reports=mutableListOf<String>()
        val pieces=layers.sortedBy {when(it.first.item.category){"하의"->0;"신발"->1;"상의"->2;else->3}}.flatMap { (g,image) ->
            val mask=mask(image)
            if(mask.removedBorder)reports+="균일 배경의 연결 영역만 제거했어요. 복잡한 배경·비슷한 색의 의류는 지원하지 않아요."
            when(g.item.category) {
                "상의" -> {
                    val longSleeve=g.item.subCategory?.contains("긴")==true
                    val slot=FitSlot.TOP
                    if(longSleeve)longSleevePieces(mask,p,g,body,size,slot)
                    else listOf(Piece(mask,vertices(mask,topAnchors(mask,p,false),g,body,size,slot),"상의: 어깨·몸통·짧은 소매 추정 기준점의 비강체 메시 변형"))
                }
                "하의" -> {
                    val skirt=g.item.subCategory=="치마"
                    if(g.item.subCategory?.let {it.contains("반바지")||it.contains("쇼츠")||it.contains("숏")}==true)
                        reject("반바지를 발목까지 늘리지 않아요. 이번 자동 합성은 긴바지·치마만 지원해요.")
                    val anchors=if(skirt)torsoAnchors(mask,p,true)else emptyList()
                    val mapping=if(skirt)null else pantsMapping(mask,p)
                    listOf(Piece(mask,vertices(mask,anchors,g,body,size,FitSlot.BOTTOM,mapping),if(skirt)"치마: 골반·무릎 길이의 별도 메시" else "바지: 허리·좌우 무릎·발목의 비강체 메시 변형"))
                }
                "아우터" -> reject("아우터·코트 길이는 이번 자동 변형에서 검증되지 않았어요. 기존 수동 합성을 사용해주세요.")
                "신발" -> shoePieces(mask,p,g,body,size)
                else -> reject("현재 자동 착장은 상의·하의·분리된 신발 한 쌍만 지원해요. 다른 종류는 수동 합성을 사용해주세요.")
            }
        }
        val fit=min(size.width/photo.width,size.height/photo.height)
        val outputScale=min(2048f/max(size.width,size.height),max(1f,1/fit))
        val output=Bitmap.createBitmap((size.width*outputScale).roundToInt().coerceAtLeast(1),(size.height*outputScale).roundToInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
        var original:Bitmap?=null
        try {
            val canvas=Canvas(output);canvas.drawColor(Color.rgb(232,224,216));canvas.scale(outputScale,outputScale)
            val w=photo.width*fit;val h=photo.height*fit
            canvas.drawBitmap(photo,null,RectF((size.width-w)/2,(size.height-h)/2,(size.width+w)/2,(size.height+h)/2),Paint(Paint.FILTER_BITMAP_FLAG))
            val preserved=output.copy(Bitmap.Config.ARGB_8888,false) ?: reject("원본 보존 버퍼를 만들지 못했어요.")
            original=preserved
            val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {this.alpha=(alpha*255).roundToInt()}
            for(piece in pieces) {
                val texture=prepare(photo,piece,size)
                try {canvas.drawBitmapMesh(texture,GRID_X,GRID_Y,piece.mesh,0,null,0,paint)}finally {texture.recycle()}
                reports+=piece.report
            }
            val restored=preserveFaceAndHands(output,preserved,p,body,outputScale)
            val wristCount=listOf(p.left,p.right).count {it.wrist!=null && it.elbow!=null}
            reports+="얼굴 영역 보존·확인 가능한 손목 ${wristCount}곳의 피부색 추정 가림 적용($restored 픽셀). 머리카락·팔 전체의 의미 분할은 미지원이에요."
            val sections=p.outline?.supportedSections ?: 0
            reports+=if(sections>0)"균일 배경의 기존 의상·발 윤곽 ${sections}곳을 제한 범위에서 반영했어요. 인체 실측이나 의미 분할 결과가 아니에요."
                else "사진 윤곽을 확인하지 못해 관절 기준 추정만 사용했어요. 기존 의상 노출·착장 품질은 직접 확인해주세요."
            reports+="경계 안쪽 블렌딩·밝기 ±6% 제한. 기존 의상 제거·새 주름·그림자 생성은 하지 않아요. 실사진 품질 미검증."
            val bytes=ByteArrayOutputStream().use {out->if(!output.compress(Bitmap.CompressFormat.PNG,100,out)||out.size()>8*1024*1024)reject("PNG 크기가 너무 커서 저장하지 않았어요.");out.toByteArray()}
            return DrapeRenderResult(bytes,reports)
        } finally {original?.recycle();output.recycle()}
    }
}
