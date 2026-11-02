package com.jeiu.fashionproject.data

import kotlin.math.*

object CmRulerMath {
    fun range(body:AvatarCentimeters,part:AvatarPart):ClosedFloatingPointRange<Float> {
        val base=AvatarCentimeters.range(part,body.heightCm)
        var low=base.start;var high=base.endInclusive
        when(part) {
            AvatarPart.LEG -> {low=max(low,body.heightCm*.64f-body[AvatarPart.TORSO]);high=min(high,body.heightCm*.80f-body[AvatarPart.TORSO])}
            AvatarPart.TORSO -> {low=max(low,body.heightCm*.64f-body[AvatarPart.LEG]);high=min(high,body.heightCm*.80f-body[AvatarPart.LEG])}
            AvatarPart.THIGH -> high=min(high,body[AvatarPart.HIP]*.64f)
            AvatarPart.HIP -> low=max(low,body[AvatarPart.THIGH]/.64f)
            else->Unit
        }
        if(body.assumption==AvatarCentimeters.ASSUMPTION)when(part) {
            AvatarPart.CHEST -> high=min(high,AvatarCentimeters.chestLimit(body[AvatarPart.SHOULDER]))
            AvatarPart.SHOULDER -> low=max(low,body[AvatarPart.CHEST]/AvatarCentimeters.chestLimit(1f))
            AvatarPart.TORSO -> low=max(low,body.heightCm*.20f)
            else->Unit
        }
        return ceil((low-.00001f)*2)/2..floor((high+.00001f)*2)/2
    }
    fun drag(start:Float,deltaPx:Float,pixelsPerCm:Float,range:ClosedFloatingPointRange<Float>):Float {
        require(start.isFinite()&&deltaPx.isFinite()&&pixelsPerCm.isFinite()&&pixelsPerCm>0)
        return AvatarCentimeters.snap((start-deltaPx/pixelsPerCm).coerceIn(range)).coerceIn(range)
    }
    fun parse(text:String,range:ClosedFloatingPointRange<Float>):Float? = text.toFloatOrNull()?.takeIf {
        Regex("[0-9]{1,3}(\\.[0-9])?").matches(text) && it.isFinite() && it in range && abs(it*2-round(it*2))<.0001f
    }
}
