package com.jeiu.fashionproject.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 가장 최근 사진 분석의 체형 분류(역삼각형 · 삼각형 · 모래시계형 · 직사각형)만 기기에 남긴다.
 * 사진 · 치수는 저장하지 않는다. 직접 입력한 치수와 합친 최종 체형은 BodyShapeFusion이 계산한다.
 */
object BodyTypeStore {
    data class Saved(val type: String, val at: Long)

    val TYPES = listOf("역삼각형", "삼각형", "모래시계형", "직사각형")
    private const val PREFS = "body_type_store"
    private val _current = MutableStateFlow<Saved?>(null)
    val current: StateFlow<Saved?> = _current

    // 계정(게스트 포함)마다 따로 저장한다. 로그인 확인 중(scope null)에는 읽지도 쓰지도 않는다.
    private fun scope(): String? = BodyMeasurementScope.current()

    fun load(context: Context) {
        val scope = scope() ?: run { _current.value = null; return }
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val type = p.getString(scope + "type", null)?.takeIf { it in TYPES }
        _current.value = type?.let { Saved(it, p.getLong(scope + "at", 0L)) }
    }

    fun save(context: Context, type: String, at: Long = System.currentTimeMillis()): Boolean {
        val scope = scope() ?: return false
        if (type !in TYPES) return false
        val ok = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(scope + "type", type).putLong(scope + "at", at).commit()
        if (ok) _current.value = Saved(type, at)
        return ok
    }

    fun clear(context: Context) {
        val scope = scope() ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(scope + "type").remove(scope + "at").commit()
        _current.value = null
    }
}
