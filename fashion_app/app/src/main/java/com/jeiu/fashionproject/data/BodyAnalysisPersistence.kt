package com.jeiu.fashionproject.data

import com.jeiu.fashionproject.ui.viewmodel.*
import kotlin.math.abs

/** Defense in depth at the queue boundary. A plausible type label alone is not sufficient. */
object BodyAnalysisPersistence {
    fun mayStore(result:BodyAnalysisResult):Boolean {
        val m=result.measurements ?: return false
        if(!BodyMeasurementCalculator.isValid(m) || result.confidence !in 0..100)return false
        val widths=BodyWidths(m.shoulderWidthCm/m.cmPerPixel,m.waistWidthCm/m.cmPerPixel,
            m.hipWidthCm/m.cmPerPixel,m.measuredBodyHeightPx,fullBodyReliable=true)
        val type=BodyTypeClassifier.classify(widths).type ?: return false
        if(type.label!=result.bodyTypeKo)return false
        fun same(a:Float,b:Float)=a.isFinite() && b.isFinite() && abs(a-b)<.001f
        val maximum=maxOf(m.shoulderWidthCm,m.waistWidthCm,m.hipWidthCm)
        if(!same(result.shoulderRatio,m.shoulderWidthCm/maximum) ||
            !same(result.waistRatio,m.waistWidthCm/maximum) || !same(result.hipRatio,m.hipWidthCm/maximum) ||
            !same(result.shoulderToHip,m.shoulderWidthCm/m.hipWidthCm) ||
            !same(result.waistToHip,m.waistWidthCm/m.hipWidthCm))return false
        val expected=m.legLengthCm?.div(m.heightCm)
        return if(expected==null) result.legRatio==null else result.legRatio?.let{same(it,expected)}==true
    }
}
