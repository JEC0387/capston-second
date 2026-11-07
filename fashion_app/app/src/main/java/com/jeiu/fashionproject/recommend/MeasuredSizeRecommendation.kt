package com.jeiu.fashionproject.recommend

import com.jeiu.fashionproject.data.*
import java.net.URI
import kotlin.math.abs

enum class FitPreference(val label:String,val chestEase:Float,val waistEase:Float,val hipEase:Float,val shoulderEase:Float) {
    SNUG("타이트핏 참고",2f,1f,2f,0f), REGULAR("정핏 참고",8f,3f,5f,1f), RELAXED("여유핏 참고",14f,6f,10f,3f)
}
data class SizeDimensionEvidence(val dimension:BodyDimension,val bodyCm:Float,val garmentCm:Float,val easeCm:Float,val origin:MeasurementOrigin)
data class MeasuredSizeSuggestion(val row:GarmentSizeRow,val preference:FitPreference,val evidence:List<SizeDimensionEvidence>,val score:Float)
data class MeasuredSizeResult(val suggestions:List<MeasuredSizeSuggestion>,val missing:Set<BodyDimension>,val unusableRows:Int,
    val missingGarmentDimensions:Set<BodyDimension> = emptySet())

/** Explainable ease heuristic. It cannot infer stretch, pattern, comfort or actual product fit. */
object MeasuredSizeRecommendation {
    fun suggest(profile:BodyDimensionProfile,rows:List<GarmentSizeRow>,preference:FitPreference):MeasuredSizeResult {
        val missing=linkedSetOf<BodyDimension>();var unusable=0
        val missingGarment=linkedSetOf<BodyDimension>()
        val candidates=rows.mapNotNull candidate@ { row ->
            val dimensions=when(row.category) {
                "상의","아우터" -> listOf(BodyDimension.SHOULDER_WIDTH,BodyDimension.CHEST_CIRCUMFERENCE)
                "하의" -> listOf(BodyDimension.WAIST_CIRCUMFERENCE,BodyDimension.HIP_CIRCUMFERENCE)
                "원피스" -> listOf(BodyDimension.SHOULDER_WIDTH,BodyDimension.CHEST_CIRCUMFERENCE,BodyDimension.WAIST_CIRCUMFERENCE,BodyDimension.HIP_CIRCUMFERENCE)
                else -> { unusable++;return@candidate null }
            }
            val source=runCatching { URI(row.sourceUrl) }.getOrNull()
            if(row.brand.isBlank()||row.sizeLabel.isBlank()||row.measureType !in setOf("단면","둘레")||
                source?.scheme!="https"||source.host.isNullOrBlank()||source.userInfo!=null) { unusable++;return@candidate null }
            var invalid=false
            val evidence=dimensions.mapNotNull dimension@ { dimension ->
                val body=profile[dimension]
                val isShoulder=dimension==BodyDimension.SHOULDER_WIDTH
                if(body==null || !body.cm.isFinite() || body.cm<=0 || (!isShoulder&&body.origin!=MeasurementOrigin.USER_ENTERED)) {
                    missing+=dimension;invalid=true;return@dimension null
                }
                val raw=when(dimension) {
                    BodyDimension.SHOULDER_WIDTH -> row.shoulderCm
                    BodyDimension.CHEST_CIRCUMFERENCE -> row.chestCm
                    BodyDimension.WAIST_CIRCUMFERENCE -> row.waistCm
                    BodyDimension.HIP_CIRCUMFERENCE -> row.hipCm
                    else -> null
                }
                if(raw==null||!raw.isFinite()||raw<=0f) { missingGarment+=dimension;invalid=true;return@dimension null }
                val garment=raw*(if(!isShoulder&&row.measureType=="단면")2f else 1f)
                if(garment !in (if(isShoulder) 15f..100f else 40f..300f)) { invalid=true;return@dimension null }
                SizeDimensionEvidence(dimension,body.cm,garment,garment-body.cm,body.origin)
            }
            if(invalid) { unusable++;return@candidate null }
            fun target(key:BodyDimension)=when(key) {
                BodyDimension.SHOULDER_WIDTH -> preference.shoulderEase
                BodyDimension.CHEST_CIRCUMFERENCE -> preference.chestEase
                BodyDimension.WAIST_CIRCUMFERENCE -> preference.waistEase
                else -> preference.hipEase
            }
            // Do not recommend a clearly too-small or unboundedly oversized row just because it exists.
            if(evidence.any { it.easeCm < target(it.dimension) ||
                it.easeCm > target(it.dimension)+(if(it.dimension==BodyDimension.SHOULDER_WIDTH)8f else 20f) }) return@candidate null
            MeasuredSizeSuggestion(row,preference,evidence,evidence.sumOf { abs(it.easeCm-target(it.dimension)).toDouble() }.toFloat())
        }
        val best=candidates.groupBy { listOf(it.row.brand,it.row.category,it.row.productKey.orEmpty(),it.row.sourceUrl) }
            .values.map { group -> group.minWith(compareBy<MeasuredSizeSuggestion>({it.score},{it.row.sizeLabel})) }
        return MeasuredSizeResult(best.sortedWith(compareBy({it.row.brand},{it.row.category},{it.row.sourceUrl})),missing,unusable,missingGarment)
    }
}
