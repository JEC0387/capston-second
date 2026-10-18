package com.jeiu.fashionproject.ui.camera

import kotlin.math.abs

/** Shared camera/gallery geometry checks. Thresholds are capture heuristics, not body-type rules. */
object PoseQualityRules {
    fun evaluate(points: Map<Joint, GuidePoint>, width: Float, height: Float): GuideStatus {
        if (points.isEmpty()) return GuideStatus.NO_PERSON
        if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f || Joint.entries.any {
            val p = points[it]
            p == null || !p.x.isFinite() || !p.y.isFinite() || !p.confidence.isFinite() ||
                p.confidence !in .65f..1f || p.x !in 0f..width || p.y !in 0f..height
        }) return GuideStatus.FULL_BODY
        fun p(j: Joint) = points.getValue(j)
        val ls=p(Joint.LEFT_SHOULDER); val rs=p(Joint.RIGHT_SHOULDER)
        val lh=p(Joint.LEFT_HIP); val rh=p(Joint.RIGHT_HIP)
        val shoulder=abs(ls.x-rs.x); val hip=abs(lh.x-rh.x)
        val torso=(lh.y+rh.y-ls.y-rs.y)/2f
        val nose=p(Joint.NOSE)
        if (torso <= 0f || shoulder <= 0f || hip <= 0f ||
            nose.y >= minOf(ls.y,rs.y) || minOf(p(Joint.LEFT_ANKLE).y,p(Joint.RIGHT_ANKLE).y) <= maxOf(lh.y,rh.y) ||
            maxOf(p(Joint.LEFT_FOOT).y,p(Joint.RIGHT_FOOT).y) >= height*.99f) return GuideStatus.FULL_BODY
        // Reject collapsed projections independently (OR); broad floors avoid the old 0.55 physique bias.
        if (shoulder/torso < .35f || hip/torso < .12f ||
            abs(ls.y-rs.y)/shoulder > .30f || abs(lh.y-rh.y)/shoulder > .30f ||
            abs(nose.x-(ls.x+rs.x)/2f)/shoulder > .35f ||
            abs(p(Joint.LEFT_EYE).x-p(Joint.RIGHT_EYE).x)/shoulder < .10f) return GuideStatus.FRONT
        fun separated(s: GuidePoint,h: GuidePoint,a: GuidePoint): Boolean {
            if (a.y < s.y || a.y > (lh.y+rh.y)/2f+torso*.6f || h.y<=s.y) return false
            val t=(a.y-s.y)/(h.y-s.y)
            val edge=s.x+(h.x-s.x)*t
            val outward=if(s.x<(ls.x+rs.x)/2f) -1 else 1
            return (a.x-edge)*outward >= shoulder*.12f
        }
        if (!separated(ls,lh,p(Joint.LEFT_ELBOW)) || !separated(ls,lh,p(Joint.LEFT_WRIST)) ||
            !separated(rs,rh,p(Joint.RIGHT_ELBOW)) || !separated(rs,rh,p(Joint.RIGHT_WRIST))) return GuideStatus.ARMS
        return GuideStatus.READY
    }
}
