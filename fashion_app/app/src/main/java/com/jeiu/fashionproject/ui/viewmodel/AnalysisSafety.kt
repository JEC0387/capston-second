package com.jeiu.fashionproject.ui.viewmodel

/** Request identity remains invalid after cancellation, even if a native callback arrives later. */
class AnalysisRequestGate {
    private var generation=0L
    private var active=false
    @Synchronized fun begin():Long { generation++;active=true;return generation }
    @Synchronized fun isCurrent(id:Long)=active && id==generation
    @Synchronized fun owns(id:Long)=id==generation
    @Synchronized fun invalidate(){generation++;active=false}
    @Synchronized fun publish(id:Long, action:()->Unit):Boolean {
        if(!isCurrent(id))return false
        try { action() } finally { active=false };return true
    }
}

enum class ImageInputIssue(val message:String) {
    RESOLUTION("사진 해상도가 너무 낮아요. 선명한 원본 전신 사진을 선택해주세요."),
    DARK("사진이 너무 어두워요. 몸과 배경이 구분되도록 밝은 곳에서 촬영해주세요."),
    BRIGHT("사진이 과도하게 밝아요. 역광을 피해 다시 촬영해주세요."),
    CONTRAST("사진의 명암과 윤곽이 거의 없어요. 흐린 사진 대신 선명한 원본을 선택해주세요.")
}
/** Only rejects severe input defects. These are heuristic capture checks, not accuracy estimates. */
object ImageInputQuality {
    fun evaluate(width:Int,height:Int,luma:IntArray):ImageInputIssue? {
        if(minOf(width,height)<128 || maxOf(width,height)<400 || luma.size<16)return ImageInputIssue.RESOLUTION
        val values=luma.sorted()
        val low=values[values.size/20];val high=values[values.size*19/20]
        return when {
            high<20 -> ImageInputIssue.DARK
            low>245 -> ImageInputIssue.BRIGHT
            high-low<8 -> ImageInputIssue.CONTRAST
            else -> null
        }
    }
}

/** One explicit approval for one photo/request; declining never affects body-analysis consent. */
class PhotoTransferConsent {
    private var pending:String?=null
    private var approved:String?=null
    fun request(photo:String){pending=photo.takeIf{it.isNotBlank()};approved=null}
    fun confirm(photo:String):Boolean {
        if(photo.isBlank() || pending!=photo)return false
        approved=photo;pending=null;return true
    }
    fun consume(photo:String):Boolean {
        val ok=approved!=null && approved==photo
        approved=null;return ok
    }
    fun cancel(){pending=null;approved=null}
}
