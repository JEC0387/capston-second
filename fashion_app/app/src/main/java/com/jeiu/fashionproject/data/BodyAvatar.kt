package com.jeiu.fashionproject.data

import kotlin.math.*

data class AvatarParameters(
    val heightCm: Float = 170f, val shoulderCm: Float = 40f, val chestWidthCm: Float = 34f,
    val waistWidthCm: Float = 29f, val hipWidthCm: Float = 36f,
    val legCm: Float = 79f, val torsoCm: Float = 46f,
    val sources: List<String> = listOf("기본 비율"),
    val notes: List<String> = listOf("깊이·얼굴·팔·다리는 단순화한 기본 형상이에요. 신체 복원이나 실제 착용 예측이 아니에요.")
) {
    fun joints(): AvatarJoints {
        val h=heightCm/100f
        val hip=(legCm/100f+h*.04f).coerceIn(h*.42f,h*.60f)
        return AvatarJoints(hip,(hip+torsoCm/100f).coerceIn(h*.72f,h*.84f))
    }
    fun isValid(): Boolean = heightCm.isFinite() && heightCm in 100f..230f &&
        listOf(shoulderCm,chestWidthCm,waistWidthCm,hipWidthCm).all { it.isFinite() && it/heightCm in .10f.. .45f } &&
        legCm.isFinite() && legCm/heightCm in .25f.. .65f && torsoCm.isFinite() && torsoCm/heightCm in .15f.. .40f

    companion object {
        fun from(profile: BodyDimensionProfile): AvatarParameters {
            val height = profile[BodyDimension.HEIGHT]?.cm?.takeIf { it.isFinite() && it in 100f..230f } ?: 170f
            val sources = mutableListOf("키: ${profile[BodyDimension.HEIGHT]?.takeIf { it.cm.isFinite() && it.cm in 100f..230f }?.origin?.label ?: "기본 비율"}")
            val notes = mutableListOf("깊이는 기본 타원 비율을 가정해요. 둘레만으로 실제 앞뒤 두께를 알 수 없어요. 얼굴·팔은 기본 형상이에요.")
            fun value(key: BodyDimension, fallback: Float): Float {
                val item = profile[key]
                return if (item != null && item.cm.isFinite() && item.cm > 0f) {
                    sources += "${key.label}: ${item.origin.label}"; item.cm
                } else { sources += "${key.label}: 기본 비율"; fallback }
            }
            fun width(front: BodyDimension?, circumference: BodyDimension?, fallback: Float): Float {
                // Circumference -> illustrative ellipse with fixed depth ratio; never stored as a measurement.
                val circumferenceValue = circumference?.let { profile[it] }
                val raw = if (circumferenceValue != null && circumferenceValue.cm.isFinite() && circumferenceValue.cm > 0f) {
                    sources += "${circumference!!.label}: ${circumferenceValue.origin.label} · 타원 가정"
                    val ratio = .68
                    (2 * circumferenceValue.cm / (PI * (3*(1+ratio)-sqrt((3+ratio)*(1+3*ratio))))).toFloat()
                } else if (front != null) value(front,fallback) else {
                    sources+="${circumference?.label ?: "부위 치수"}: 값 없음 · 기본 비율 추정";fallback
                }
                val limited = raw.coerceIn(height*.10f,height*.45f)
                if (raw != limited) notes += "일부 너비는 화면 표시 범위로 제한했어요. 원본 치수는 바꾸지 않았어요."
                return limited
            }
            val shoulder = width(BodyDimension.SHOULDER_WIDTH,null,height*.235f)
            val chest = width(null,BodyDimension.CHEST_CIRCUMFERENCE,height*.20f)
            val waist = width(BodyDimension.WAIST_FRONT_WIDTH,BodyDimension.WAIST_CIRCUMFERENCE,height*.17f)
            val hip = width(BodyDimension.HIP_FRONT_WIDTH,BodyDimension.HIP_CIRCUMFERENCE,height*.21f)
            val leg = value(BodyDimension.LEG_LENGTH,height*.465f).coerceIn(height*.25f,height*.65f)
            val torso = value(BodyDimension.TORSO_LENGTH,height*.27f).coerceIn(height*.15f,height*.40f)
            return AvatarParameters(height,shoulder,chest,waist,hip,leg,torso,sources.distinct(),notes.distinct())
        }
    }
}

data class AvatarJoints(val hipY:Float,val shoulderY:Float)

/** Original low-poly 3D geometry: interleaved XYZ position, normal and RGB. No photo texture. */
object BodyAvatarMesh {
    const val STRIDE = 9
    fun build(parameters: AvatarParameters, shoulderScale: Float = 1f, waistScale: Float = 1f, hipScale: Float = 1f, thighScale: Float = 1f): FloatArray {
        require(parameters.isValid())
        require(listOf(shoulderScale,waistScale,hipScale,thighScale).all { it.isFinite() && it in .8f..1.2f })
        val vertices=ArrayList<Float>()
        val h=parameters.heightCm/100f
        fun vertex(x:Float,y:Float,z:Float,nx:Float,ny:Float,nz:Float,color:FloatArray) {
            val length=sqrt(nx*nx+ny*ny+nz*nz).coerceAtLeast(.0001f)
            vertices.addAll(listOf(x,y-h/2,z,nx/length,ny/length,nz/length,color[0],color[1],color[2]))
        }
        fun tube(x:Float,controls:List<Triple<Float,Float,Float>>,color:FloatArray,centerX:(Float)->Float = {x}) {
            // Smooth radii within each anatomical span; keep ring heights ordered and avoid overshoot.
            val rings=controls.zipWithNext().flatMap { (a,b) -> (0..2).map { step ->
                val t=step/3f;val blend=t*t*(3-2*t)
                Triple(a.first+(b.first-a.first)*t,a.second+(b.second-a.second)*blend,a.third+(b.third-a.third)*blend)
            } }+controls.last()
            fun point(index:Int,angle:Double) {
                val ring=rings[index]
                val c=cos(angle).toFloat(); val s=sin(angle).toFloat()
                val a=rings[(index-1).coerceAtLeast(0)];val b=rings[(index+1).coerceAtMost(rings.lastIndex)]
                val dy=(b.first-a.first).coerceAtLeast(.00001f)
                val nx=c/ring.second;val nz=s/ring.third
                val ny= -c*c*(b.second-a.second)/dy/ring.second-s*s*(b.third-a.third)/dy/ring.third-
                    nx*(centerX(b.first)-centerX(a.first))/dy
                vertex(centerX(ring.first)+c*ring.second,ring.first,s*ring.third,nx,ny,nz,color)
            }
            for (r in 0 until rings.size-1) for (i in 0 until 24) {
                val a=i*2*PI/24;val b=(i+1)*2*PI/24
                point(r,a);point(r+1,a);point(r+1,b)
                point(r,a);point(r+1,b);point(r,b)
            }
            // Close open ends, including soles/hands. These remain a simplified overlapping joint model.
            listOf(0 to -1f,rings.lastIndex to 1f).forEach { (index,normalY) ->
                val ring=rings[index]
                for(i in 0 until 24) {
                    val a=i*2*PI/24;val b=(i+1)*2*PI/24
                    vertex(centerX(ring.first),ring.first,0f,0f,normalY,0f,color)
                    listOf(a,b).forEach { angle -> vertex(centerX(ring.first)+cos(angle).toFloat()*ring.second,
                        ring.first,sin(angle).toFloat()*ring.third,0f,normalY,0f,color) }
                }
            }
        }
        val joints=parameters.joints();val hipY=joints.hipY;val shoulderY=joints.shoulderY
        val shoulder=parameters.shoulderCm/200f*shoulderScale
        val waist=parameters.waistWidthCm/200f*waistScale
        val hip=parameters.hipWidthCm/200f*hipScale
        val chest=parameters.chestWidthCm/200f
        val bodyColor=floatArrayOf(.64f,.54f,.43f);val limbColor=floatArrayOf(.76f,.66f,.55f)
        tube(0f,listOf(Triple(hipY-h*.04f,hip*.85f,hip*.65f),Triple(hipY,hip,hip*.68f),
            Triple(hipY+(shoulderY-hipY)*.35f,waist,waist*.68f),
            Triple(shoulderY-h*.06f,chest,chest*.68f),Triple(shoulderY,shoulder,shoulder*.50f),
            Triple(shoulderY+h*.02f,h*.035f,h*.03f),Triple(h*.865f,h*.035f,h*.03f)),bodyColor)
        for (side in listOf(-1f,1f)) {
            tube(side*hip*.48f,listOf(Triple(0f,h*.03f,h*.05f),Triple(h*.04f,h*.025f,h*.03f),
                Triple(h*.04f+(hipY-h*.04f)*.52f,h*.038f*thighScale,h*.04f*thighScale),
                Triple(hipY,h*.055f*thighScale,h*.055f*thighScale)),limbColor)
            tube(0f,listOf(Triple(shoulderY-h*.40f,h*.018f,h*.025f),
                Triple(shoulderY-h*.36f,h*.019f,h*.022f),Triple(shoulderY-h*.18f,h*.025f,h*.028f),
                Triple(shoulderY,h*.041f,h*.040f)),limbColor) { y ->
                val t=((shoulderY-y)/(h*.40f)).coerceIn(0f,1f)
                side*(shoulder-h*.020f+h*.055f*t)
            }
        }
        fun head(lat:Double,lon:Double) {
            val nx=(sin(lat)*cos(lon)).toFloat();val ny=cos(lat).toFloat();val nz=(sin(lat)*sin(lon)).toFloat()
            vertex(nx*h*.055f,h*.93f+ny*h*.07f,nz*h*.055f,nx/.055f,ny/.07f,nz/.055f,limbColor)
        }
        for (a in 0 until 12) for (b in 0 until 24) {
            val x=a*PI/12;val y=(a+1)*PI/12;val u=b*2*PI/24;val v=(b+1)*2*PI/24
            head(x,u);head(y,u);head(y,v);head(x,u);head(y,v);head(x,v)
        }
        return vertices.toFloatArray()
    }
}
