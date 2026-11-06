package com.jeiu.fashionproject.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import com.jeiu.fashionproject.recommend.MeasuredSizeSuggestion

enum class SizeSatisfaction(val label:String) { SMALL("작았어요"), FIT("잘 맞았어요"), LARGE("컸어요") }
data class SizeFeedback(val chartKey:String,val brand:String,val size:String,val preference:String,val satisfaction:SizeSatisfaction,val at:Long)

/** Explicit local feedback; no photos, measurements, server sync or automatic backup. */
object SizeFeedbackStore {
    private const val PREFS="size_feedback"
    fun feedback(suggestion:MeasuredSizeSuggestion,rating:SizeSatisfaction,at:Long):SizeFeedback {
        val row=suggestion.row
        val key=listOf(row.brand,row.category,row.productKey.orEmpty(),row.sourceUrl).joinToString("|")
        val digest=MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString(""){"%02x".format(it)}
        return SizeFeedback(digest,row.brand,row.sizeLabel,suggestion.preference.name,rating,at)
    }
    private fun read(context:Context):List<SizeFeedback>? = runCatching {
        val array=JSONArray(context.getSharedPreferences(PREFS,0).getString("entries","[]"))
        List(array.length()) { index -> array.getJSONObject(index).let {
            SizeFeedback(it.getString("chart"),it.getString("brand"),it.getString("size"),it.getString("preference"),
                SizeSatisfaction.valueOf(it.getString("rating")),it.getLong("at"))
        } }
    }.getOrNull()
    fun load(context:Context):List<SizeFeedback> = read(context).orEmpty()

    fun save(context:Context,entry:SizeFeedback):Boolean {
        if(entry.at<=0 || !entry.chartKey.matches(Regex("[a-f0-9]{64}")))return false
        val prior=read(context) ?: return false // Preserve an unreadable original instead of overwriting it.
        val entries=(prior+entry).takeLast(100)
        val array=JSONArray()
        entries.forEach { array.put(JSONObject().put("chart",it.chartKey).put("brand",it.brand).put("size",it.size)
            .put("preference",it.preference).put("rating",it.satisfaction.name).put("at",it.at)) }
        return context.getSharedPreferences(PREFS,0).edit().putString("entries",array.toString()).commit()
    }
}
