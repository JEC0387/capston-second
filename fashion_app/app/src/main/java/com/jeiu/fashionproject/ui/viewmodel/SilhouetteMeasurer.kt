package com.jeiu.fashionproject.ui.viewmodel

import com.jeiu.fashionproject.ui.camera.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

enum class SilhouetteIssue(val message: String) {
    MODEL_UNAVAILABLE("기기 내 인물 분할 모델을 준비하지 못했어요. 앱을 다시 실행해주세요."),
    INVALID_MASK("인물 마스크가 올바르지 않아요. 다른 정면 전신 사진을 선택해주세요."),
    LANDMARKS("관절 또는 발끝을 확인하지 못했어요. 머리부터 발끝까지 선명하게 촬영해주세요."),
    POSTURE("정면으로 서고 팔을 몸에서 조금 떼주세요. 무릎도 펴주세요."),
    NO_BODY("배경과 몸통 외곽이 구분되지 않아요. 단순한 배경에서 다시 촬영해주세요."),
    EXTRA_FOREGROUND("다른 사람이나 큰 배경 물체가 함께 감지됐어요. 한 명만 나오게 촬영해주세요."),
    ARM_CONTOUR("팔·소매와 어깨 경계가 겹쳐요. 팔을 조금 떼고 몸에 맞는 옷으로 촬영해주세요."),
    UNSTABLE_BOUNDARY("허리·힙 외곽이 불안정해요. 옷자락과 배경을 정리해 다시 촬영해주세요."),
    FULL_BODY("머리 꼭대기나 발끝 경계를 확인하지 못했어요. 위아래에 여유를 두고 촬영해주세요.")
}
data class SilhouetteMeasurement(val widths: BodyWidths? = null, val issue: SilhouetteIssue? = null)

/** Pure mask geometry; all output lengths use the same orientation-normalized bitmap coordinates. */
object SilhouetteMeasurer {
    fun measure(confidence: FloatArray, maskWidth: Int, maskHeight: Int,
                imageWidth: Int, imageHeight: Int, points: Map<Joint, GuidePoint>): BodyWidths? =
        inspect(confidence,maskWidth,maskHeight,imageWidth,imageHeight,points).widths

    fun inspect(confidence: FloatArray, maskWidth: Int, maskHeight: Int,
                imageWidth: Int, imageHeight: Int, points: Map<Joint, GuidePoint>): SilhouetteMeasurement {
        fun fail(issue: SilhouetteIssue)=SilhouetteMeasurement(issue=issue)
        if (maskWidth<32 || maskHeight<32 || maskWidth.toLong()*maskHeight!=confidence.size.toLong() ||
            imageWidth<=0 || imageHeight<=0 || confidence.any { !it.isFinite() || it !in 0f..1f }) return fail(SilhouetteIssue.INVALID_MASK)
        when (PoseQualityRules.evaluate(points,imageWidth.toFloat(),imageHeight.toFloat())) {
            GuideStatus.READY -> Unit
            GuideStatus.FRONT, GuideStatus.ARMS -> return fail(SilhouetteIssue.POSTURE)
            else -> return fail(SilhouetteIssue.LANDMARKS)
        }
        fun p(j:Joint)=points.getValue(j)
        val sx=imageWidth.toFloat()/maskWidth; val sy=imageHeight.toFloat()/maskHeight
        val ls=p(Joint.LEFT_SHOULDER); val rs=p(Joint.RIGHT_SHOULDER)
        val lh=p(Joint.LEFT_HIP); val rh=p(Joint.RIGHT_HIP)
        val shoulderY=(ls.y+rs.y)/2f; val hipY=(lh.y+rh.y)/2f; val torso=hipY-shoulderY
        val shoulderX=(ls.x+rs.x)/2f; val hipX=(lh.x+rh.x)/2f
        // Follow a leaning/translating torso rather than a fixed X column.
        fun center(imageY:Float)=shoulderX+(hipX-shoulderX)*(imageY-shoulderY)/torso
        val seedX=(center(shoulderY+torso*.5f)/sx).roundToInt()
        val seedY=((shoulderY+torso*.5f)/sy).roundToInt()
        val foreground=BooleanArray(confidence.size){confidence[it]>=.65f}
        if(seedX !in 0 until maskWidth || seedY !in 0 until maskHeight) return fail(SilhouetteIssue.NO_BODY)
        val seed=seedY*maskWidth+seedX
        if(!foreground[seed]) return fail(SilhouetteIssue.NO_BODY)
        val component=BooleanArray(confidence.size); val queue=IntArray(confidence.size)
        var read=0; var count=1; queue[0]=seed;component[seed]=true
        var minX=seedX;var maxX=seedX;var minY=seedY;var maxY=seedY
        fun visit(i:Int){if(foreground[i]&&!component[i]){component[i]=true;queue[count++]=i}}
        while(read<count){
            val i=queue[read++];val x=i%maskWidth;val y=i/maskWidth
            minX=minOf(minX,x);maxX=maxOf(maxX,x);minY=minOf(minY,y);maxY=maxOf(maxY,y)
            if(x>0)visit(i-1);if(x+1<maskWidth)visit(i+1)
            if(y>0)visit(i-maskWidth);if(y+1<maskHeight)visit(i+maskWidth)
        }
        if(count<confidence.size*.03f) return fail(SilhouetteIssue.NO_BODY)
        if(foreground.count{it}-count>count*.05f) return fail(SilhouetteIssue.EXTRA_FOREGROUND)
        if(queue.take(count).map { confidence[it].toDouble() }.average()<.75) return fail(SilhouetteIssue.UNSTABLE_BOUNDARY)
        fun run(imageY:Float):Float? {
            val y=(imageY/sy).roundToInt();val x=(center(imageY)/sx).roundToInt()
            if(y !in 0 until maskHeight || x !in 0 until maskWidth || !component[y*maskWidth+x]) return null
            var l=x;var r=x
            while(l>0&&component[y*maskWidth+l-1])l--
            while(r+1<maskWidth&&component[y*maskWidth+r+1])r++
            return (r-l+1)*sx
        }
        fun median(values:List<Float>):Float {val a=values.sorted();val n=a.size;return if(n%2==1)a[n/2] else (a[n/2-1]+a[n/2])/2f}
        fun representative(fractions:List<Float>, reference:Float, shoulder:Boolean=false):Float? {
            val samples=fractions.mapNotNull { run(shoulderY+torso*it) }
            // Reject arm/sleeve spans; never replace them with a landmark-derived width.
            val plausible=samples.filter { it>0f && (!shoulder || it in reference*.8f..reference*1.8f) }
            if(plausible.size<5 || plausible.size<fractions.size*.5f)return null
            val middle=median(plausible)
            val stable=plausible.filter { abs(it-middle)/middle<=.12f }
            if(stable.size<5 || stable.size<plausible.size*.7f)return null
            return median(stable)
        }
        val shoulderSpan=abs(ls.x-rs.x)
        val shoulder=representative((-1..10).map { it*.025f },shoulderSpan,true) ?: return fail(SilhouetteIssue.ARM_CONTOUR)
        val waist=representative((0..8).map { .5f+it*.025f },shoulderSpan) ?: return fail(SilhouetteIssue.UNSTABLE_BOUNDARY)
        val hip=representative((0..10).map { .8f+it*.03f },shoulderSpan) ?: return fail(SilhouetteIssue.UNSTABLE_BOUNDARY)
        val top=minY*sy;val bottom=(maxY+1)*sy;val bodyHeight=bottom-top
        val marginX=max(2,(maskWidth*.01f).toInt());val marginY=max(2,(maskHeight*.01f).toInt())
        fun covered(point:GuidePoint):Boolean {
            val x=(point.x/sx).toInt();val y=(point.y/sy).toInt()
            return (-2..2).any { dy -> (-2..2).any { dx ->
                val xx=x+dx;val yy=y+dy
                xx in 0 until maskWidth && yy in 0 until maskHeight && component[yy*maskWidth+xx]
            } }
        }
        val keys=listOf(Joint.NOSE,Joint.LEFT_SHOULDER,Joint.RIGHT_SHOULDER,Joint.LEFT_HIP,Joint.RIGHT_HIP,
            Joint.LEFT_KNEE,Joint.RIGHT_KNEE,Joint.LEFT_ANKLE,Joint.RIGHT_ANKLE,Joint.LEFT_FOOT,Joint.RIGHT_FOOT)
        val feetY=maxOf(p(Joint.LEFT_FOOT).y,p(Joint.RIGHT_FOOT).y)
        val ankleY=(p(Joint.LEFT_ANKLE).y+p(Joint.RIGHT_ANKLE).y)/2f
        if(minX<marginX || maxX>=maskWidth-marginX || minY<marginY || maxY>=maskHeight-marginY ||
            bodyHeight<imageHeight*.5f || maxY-minY<80 || !keys.all { covered(p(it)) } ||
            p(Joint.NOSE).y-top !in bodyHeight*.025f..bodyHeight*.16f ||
            bottom-feetY !in -sy*2f..bodyHeight*.05f) return fail(SilhouetteIssue.FULL_BODY)
        fun straight(h:GuidePoint,k:GuidePoint,a:GuidePoint):Boolean {
            val length=a.y-h.y;if(length<=0)return false
            val t=(k.y-h.y)/length;val line=h.x+(a.x-h.x)*t
            return t in .25f.. .75f && abs(k.x-line)<=torso*.12f && abs(a.x-h.x)<=torso*.35f
        }
        if(!straight(lh,p(Joint.LEFT_KNEE),p(Joint.LEFT_ANKLE)) || !straight(rh,p(Joint.RIGHT_KNEE),p(Joint.RIGHT_ANKLE))) return fail(SilhouetteIssue.POSTURE)
        return SilhouetteMeasurement(BodyWidths(shoulder,waist,hip,bodyHeight,ankleY-hipY,torso,true))
    }
}
