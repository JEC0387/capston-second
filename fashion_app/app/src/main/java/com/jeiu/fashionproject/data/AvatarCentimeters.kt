package com.jeiu.fashionproject.data

import org.json.JSONObject
import kotlin.math.*

enum class AvatarPart(val label:String,val unit:String) {
    SHOULDER("어깨","너비"), CHEST("가슴","둘레"), WAIST("허리","둘레"), HIP("엉덩이","둘레"),
    THIGH("허벅지","둘레"), LEG("다리","길이 (고관절~발목)"), TORSO("상체","길이 (어깨~고관절)")
}
enum class AvatarValueOrigin(val label:String) {
    AUTO("자동 추정 · 실측 입력 권장"), MANUAL("직접 입력한 치수"), PHOTO("사진 정면 너비·길이 추정"),
    ADJUSTED("사용자 조절 · 실측 확인 안 됨"), LEGACY("이전 표시 비율 · 실측 아님")
}
data class AvatarCm(val cm:Float,val origin:AvatarValueOrigin)

/** Rendering assumptions only. No statistical population/medical/size recommendation claim. */
data class AvatarCentimeters(
    val heightCm:Float,val weightKg:Float,
    val initial:Map<AvatarPart,Float>,val values:Map<AvatarPart,AvatarCm>,
    val references:Map<AvatarPart,AvatarCm> = emptyMap(),
    val photoFrontWidths:Map<String,Float> = emptyMap(),
    val createdAt:Long,val updatedAt:Long,
    val assumption:String=ASSUMPTION
) {
    operator fun get(part:AvatarPart)=values.getValue(part).cm
    fun issue():String? {
        if(!heightCm.isFinite() || heightCm !in 100f..230f || !weightKg.isFinite() || weightKg !in 25f..300f ||
            createdAt<=0 || updatedAt<createdAt || assumption!=ASSUMPTION)return "키·몸무게와 저장 형식을 확인해주세요."
        if(initial.keys!=AvatarPart.entries.toSet() || values.keys!=AvatarPart.entries.toSet())return "필요한 부위 치수가 빠져 있어요."
        if(values.any {(p,v)->!v.cm.isFinite() || v.cm !in range(p,heightCm)} ||
            initial.any {(p,v)->!v.isFinite() || v !in range(p,heightCm)})return "이 표시 모델의 부위별 cm 범위를 벗어났어요. 원본 치수는 바꾸지 않았어요."
        if(references.any {(_,v)->!v.cm.isFinite() || v.cm !in 1f..350f} ||
            photoFrontWidths.keys.any {it !in setOf("waist","hip")} || photoFrontWidths.values.any {!it.isFinite() || it !in heightCm*.08f..heightCm*.5f})
            return "원본 치수의 단위·출처를 확인해주세요."
        val length=this[AvatarPart.LEG]+this[AvatarPart.TORSO]
        if(length !in heightCm*.64f..heightCm*.80f)return "다리와 상체 길이 합은 키의 64~80% 표시 범위가 필요해요. 실제 치수의 오류를 뜻하지 않아요."
        // Prevent intersecting thigh sections. This is a mannequin limitation, not an anatomical rule.
        if(this[AvatarPart.THIGH]>this[AvatarPart.HIP]*.64f)return "이 모델에서는 허벅지 둘레가 엉덩이 둘레의 64% 이하여야 분리돼 보여요. 원본 실측은 보존해요."
        return null
    }
    fun adjusted(part:AvatarPart,value:Float):AvatarCentimeters? {
        if(!value.isFinite())return null
        val changed=copy(values=values+(part to AvatarCm(snap(value),AvatarValueOrigin.ADJUSTED)))
        return changed.takeIf {it.issue()==null}
    }
    fun reset(part:AvatarPart)=copy(values=values+(part to AvatarCm(initial.getValue(part),AvatarValueOrigin.AUTO)))
    fun resetAll()=copy(values=initial.mapValues {AvatarCm(it.value,AvatarValueOrigin.AUTO)})
    fun radii(part:AvatarPart):Pair<Float,Float> {
        require(part in setOf(AvatarPart.CHEST,AvatarPart.WAIST,AvatarPart.HIP,AvatarPart.THIGH))
        return ellipse(this[part],depthRatio(part))
    }
    fun parameters():AvatarParameters {
        require(issue()==null)
        return AvatarParameters(heightCm,this[AvatarPart.SHOULDER],radii(AvatarPart.CHEST).first*2,
            radii(AvatarPart.WAIST).first*2,radii(AvatarPart.HIP).first*2,this[AvatarPart.LEG],this[AvatarPart.TORSO],
            sources=values.map {(p,v)->"${p.label} ${p.unit}: ${v.origin.label}"},
            notes=listOf("키·몸무게 초기값은 검증된 인구 통계가 아닌 임시 형상이에요.",
                "둘레→타원 깊이는 표시 가정이에요. 실제 3D 스캔·의복 시뮬레이션이 아니에요.",
                "사진 허리·힙 정면 너비는 둘레로 환산하지 않고 별도 참고로 보관해요."),centimeters=this)
    }
    fun toJson():JSONObject = JSONObject().put("height",heightCm).put("weight",weightKg).put("created",createdAt)
        .put("updated",updatedAt).put("assumption",assumption).put("initial",JSONObject().also {j->initial.forEach {(p,v)->j.put(p.name,v)}})
        .put("values",encode(values)).put("references",encode(references))
        .put("photoFrontWidths",JSONObject().also {j->photoFrontWidths.forEach {(k,v)->j.put(k,v)}})

    companion object {
        const val ASSUMPTION="fashionai-ellipse-v1"
        fun snap(v:Float)=round(v*2f)/2f
        fun range(part:AvatarPart,h:Float):ClosedFloatingPointRange<Float> = when(part) {
            AvatarPart.SHOULDER -> max(15f,h*.15f)..min(75f,h*.36f)
            AvatarPart.CHEST -> 40f..min(200f,h*1.15f)
            AvatarPart.WAIST -> 40f..min(200f,h*1.10f)
            AvatarPart.HIP -> 40f..min(200f,h*1.20f)
            AvatarPart.THIGH -> 20f..min(100f,h*.55f)
            AvatarPart.LEG -> h*.25f..h*.62f
            AvatarPart.TORSO -> h*.15f..h*.40f
        }
        fun depthRatio(part:AvatarPart)=when(part) {AvatarPart.CHEST->.72f;AvatarPart.WAIST->.70f;AvatarPart.HIP->.76f;else->.92f}
        fun circumference(a:Float,b:Float):Float {
            require(a.isFinite()&&b.isFinite()&&a>0&&b>0)
            val t=(a-b)*(a-b)/((a+b)*(a+b))
            return (PI*(a+b)*(1+3*t/(10+sqrt(4-3*t)))).toFloat()
        }
        fun ellipse(cm:Float,depthToWidth:Float):Pair<Float,Float> {
            require(cm.isFinite()&&cm>0&&depthToWidth in .4f..1.5f)
            val a=cm/circumference(1f,depthToWidth)
            return a to a*depthToWidth
        }
        fun generate(height:Float,weight:Float,at:Long):AvatarCentimeters? {
            if(!height.isFinite()||height !in 100f..230f||!weight.isFinite()||weight !in 25f..300f||at<=0)return null
            val bmi=weight/(height/100).pow(2)
            val fullness=((bmi-22f)*.012f).coerceIn(-.18f,.24f)
            fun girth(fraction:Float,full:Float,part:AvatarPart)=snap(circumference(height*fraction*(1+full)/2,
                height*fraction*(1+full)/2*depthRatio(part)))
            val initial=mapOf(AvatarPart.SHOULDER to snap(height*.235f),
                AvatarPart.CHEST to girth(.20f,fullness*.65f,AvatarPart.CHEST),
                AvatarPart.WAIST to girth(.17f,fullness,AvatarPart.WAIST),
                AvatarPart.HIP to girth(.21f,fullness*.65f,AvatarPart.HIP),
                AvatarPart.THIGH to girth(.10f,fullness*.45f,AvatarPart.THIGH),
                AvatarPart.LEG to snap(height*.49f),AvatarPart.TORSO to snap(height*.29f))
            return AvatarCentimeters(height,weight,initial,initial.mapValues {AvatarCm(it.value,AvatarValueOrigin.AUTO)},
                createdAt=at,updatedAt=at).takeIf {it.issue()==null}
        }
        fun fromLegacy(profile:AvatarProfile,at:Long):AvatarCentimeters? {
            profile.centimeters?.let {return it}
            val base=generate(profile.heightCm,profile.weightKg,at) ?: return null
            val references=linkedMapOf<AvatarPart,AvatarCm>()
            val fronts=linkedMapOf<String,Float>()
            profile.applied.forEach {(key,v)->
                val part=when(key) {
                    BodyDimension.SHOULDER_WIDTH->AvatarPart.SHOULDER
                    BodyDimension.CHEST_CIRCUMFERENCE->AvatarPart.CHEST
                    BodyDimension.WAIST_CIRCUMFERENCE->AvatarPart.WAIST
                    BodyDimension.HIP_CIRCUMFERENCE->AvatarPart.HIP
                    BodyDimension.LEG_LENGTH->AvatarPart.LEG
                    BodyDimension.TORSO_LENGTH->AvatarPart.TORSO
                    else->null
                }
                if(part!=null) references[part]=AvatarCm(v.cm,if(v.origin==MeasurementOrigin.USER_ENTERED)AvatarValueOrigin.MANUAL else AvatarValueOrigin.PHOTO)
                if(key==BodyDimension.WAIST_FRONT_WIDTH)fronts["waist"]=v.cm
                if(key==BodyDimension.HIP_FRONT_WIDTH)fronts["hip"]=v.cm
            }
            // Preserve legacy user display adjustments as separate rendering provenance.
            val p=profile.parameters()
            val old=mapOf(AvatarPart.SHOULDER to p.shoulderCm,AvatarPart.CHEST to circumference(p.chestWidthCm/2,p.chestWidthCm/2*.72f),
                AvatarPart.WAIST to (if(profile.source==AvatarSource.PHOTO)base[AvatarPart.WAIST]*profile.waist else circumference(p.waistWidthCm/2,p.waistWidthCm/2*.70f)),
                AvatarPart.HIP to (if(profile.source==AvatarSource.PHOTO)base[AvatarPart.HIP]*profile.hip else circumference(p.hipWidthCm/2,p.hipWidthCm/2*.76f)),
                AvatarPart.THIGH to base[AvatarPart.THIGH]*profile.thigh,AvatarPart.LEG to p.legCm,AvatarPart.TORSO to p.torsoCm)
            val tuned=profile.shoulder!=1f||profile.chest!=1f||profile.waist!=1f||profile.hip!=1f||profile.leg!=1f||profile.torso!=1f||profile.thigh!=1f
            val values=base.values.toMutableMap()
            val proposed=base.values.toMutableMap()
            if(tuned) old.forEach {(part,v)->proposed[part]=AvatarCm(snap(v),AvatarValueOrigin.LEGACY)}
            references.forEach {(part,value)->if(!tuned) proposed[part]=
                if(snap(value.cm)==value.cm)value else AvatarCm(snap(value.cm),AvatarValueOrigin.ADJUSTED)}
            // Unrepresentable old values remain in references; never clamp/delete the original.
            val together=base.copy(values=proposed,references=references,photoFrontWidths=fronts)
            if(together.issue()==null)return together
            proposed.forEach {(part,v)->
                val trial=base.copy(values=values+(part to v))
                if(trial.issue()==null)values[part]=v
            }
            return base.copy(values=values,references=references,photoFrontWidths=fronts).takeIf {it.issue()==null}
        }
        private fun encode(values:Map<AvatarPart,AvatarCm>)=JSONObject().also {j->values.forEach {(p,v)->
            j.put(p.name,JSONObject().put("cm",v.cm).put("origin",v.origin.name))}}
        fun fromJson(j:JSONObject):AvatarCentimeters {
            fun values(key:String)=j.getJSONObject(key).let {o->o.keys().asSequence().associate {name->
                val item=o.getJSONObject(name);AvatarPart.valueOf(name) to AvatarCm(item.getDouble("cm").toFloat(),AvatarValueOrigin.valueOf(item.getString("origin")))}}
            val initial=j.getJSONObject("initial").let {o->o.keys().asSequence().associate {AvatarPart.valueOf(it) to o.getDouble(it).toFloat()}}
            val fronts=j.getJSONObject("photoFrontWidths").let {o->o.keys().asSequence().associate {it to o.getDouble(it).toFloat()}}
            return AvatarCentimeters(j.getDouble("height").toFloat(),j.getDouble("weight").toFloat(),initial,values("values"),
                values("references"),fronts,j.getLong("created"),j.getLong("updated"),j.getString("assumption")).also {require(it.issue()==null)}
        }
    }
}
