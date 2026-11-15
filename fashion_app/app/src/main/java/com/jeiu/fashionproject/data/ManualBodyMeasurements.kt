package com.jeiu.fashionproject.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Tape measurements entered by the user. Circumferences are never photo widths. */
data class ManualBodyMeasurements(
    val heightCm: Float,
    val shoulderWidthCm: Float?,
    val chestCircumferenceCm: Float?,
    val waistCircumferenceCm: Float?,
    val hipCircumferenceCm: Float?,
    val legLengthCm: Float?, // hip to ankle, matching the existing outfit profile
    val torsoLengthCm: Float?, // shoulder to hip
    val enteredAt: Long
) {
    fun isValid(): Boolean {
        fun optional(value: Float?, range: ClosedFloatingPointRange<Float>) =
            value == null || (value.isFinite() && value in range)
        return heightCm.isFinite() && heightCm in 100f..230f && enteredAt > 0L &&
            optional(shoulderWidthCm, 15f..75f) && optional(chestCircumferenceCm, 40f..200f) &&
            optional(waistCircumferenceCm, 40f..200f) && optional(hipCircumferenceCm, 40f..200f) &&
            optional(legLengthCm, heightCm * .25f..heightCm * .65f) &&
            optional(torsoLengthCm, heightCm * .15f..heightCm * .40f) &&
            (shoulderWidthCm == null || shoulderWidthCm / heightCm in .10f.. .45f) &&
            (legLengthCm == null || torsoLengthCm == null || legLengthCm + torsoLengthCm <= heightCm * .95f) &&
            listOf(shoulderWidthCm, chestCircumferenceCm, waistCircumferenceCm,
                hipCircumferenceCm, legLengthCm, torsoLengthCm).any { it != null }
    }
}

/** Local-only, opt-in restoration. No photos, server sync or automatic backup. */
object ManualBodyStore {
    private const val PREFS = "manual_body_measurements"
    private val keys = listOf("height", "shoulder", "chest", "waist", "hip", "leg", "torso")
    private val storedKeys = keys + listOf("save", "entered_at")
    private val _current = MutableStateFlow<ManualBodyMeasurements?>(null)
    val current: StateFlow<ManualBodyMeasurements?> = _current
    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved
    private val _issue = MutableStateFlow<String?>(null)
    val issue: StateFlow<String?> = _issue
    // A failed commit may already have changed SharedPreferences' memory map.
    // Until an explicit retry succeeds, load must not promote those unconfirmed values.
    private val unconfirmedWrites = mutableMapOf<String, Map<String, *>>()
    private var activeScope: String? = null

    @Synchronized
    fun load(context: Context) {
        val scope = enterScope(context) ?: return
        if (scope in unconfirmedWrites) return
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(scope + "save", false)) return
            val value = ManualBodyMeasurements(
                prefs.getFloat(scope + "height", 0f), readOptional(prefs, scope + "shoulder"),
                readOptional(prefs, scope + "chest"), readOptional(prefs, scope + "waist"),
                readOptional(prefs, scope + "hip"), readOptional(prefs, scope + "leg"),
                readOptional(prefs, scope + "torso"), prefs.getLong(scope + "entered_at", 0L)
            )
            if (BodyMeasurementScope.current() != scope) {
                _current.value = null; _saved.value = false
                return
            }
            if (value.isValid()) {
                _current.value = value
                _saved.value = true
                _issue.value = null
            } else {
                _current.value = null
                _saved.value = false
                _issue.value = "저장된 직접 입력값의 범위를 확인해주세요. 기록을 자동으로 삭제하지 않았어요."
            }
        } catch (_: Exception) {
            _current.value=null;_saved.value=false
            _issue.value="저장된 직접 입력값의 형식을 읽지 못했어요. 원본을 보존했어요."
        }
    }

    private fun readOptional(prefs: android.content.SharedPreferences, key: String): Float? =
        if (prefs.contains(key)) prefs.getFloat(key, 0f) else null

    @Synchronized
    fun update(context: Context, value: ManualBodyMeasurements, saveOnDevice: Boolean): Boolean {
        val scope = enterScope(context) ?: return false
        if (!value.isValid()) return false
        if (!commitChange(context, scope) { edit ->
            storedKeys.forEach { edit.remove(scope + it) }
            if (saveOnDevice) {
                edit.putBoolean(scope + "save", true).putLong(scope + "entered_at", value.enteredAt)
                    .putFloat(scope + "height", value.heightCm)
                listOf(value.shoulderWidthCm, value.chestCircumferenceCm,
                    value.waistCircumferenceCm, value.hipCircumferenceCm,
                    value.legLengthCm, value.torsoLengthCm).forEachIndexed { index, number ->
                    if (number != null) edit.putFloat(scope + keys[index + 1], number)
                }
            }
        }) return false
        if (BodyMeasurementScope.current() != scope) { _current.value=null; _saved.value=false; return false }
        _current.value = value
        _saved.value = saveOnDevice
        _issue.value = null
        return true
    }

    @Synchronized
    fun clear(context: Context): Boolean {
        val scope = enterScope(context) ?: return false
        if (!commitChange(context, scope) { edit -> storedKeys.forEach { edit.remove(scope + it) } }) return false
        _current.value = null
        _saved.value = false
        _issue.value = null
        return true
    }

    private fun enterScope(context: Context): String? {
        val scope = BodyMeasurementScope.current()
        if (activeScope != scope) {
            activeScope = scope
            _current.value = null
            _saved.value = false
            _issue.value = null
        }
        if (scope != null && !BodyMeasurementScope.prepare(context, PREFS, storedKeys, scope)) {
            _current.value = null; _saved.value = false
            _issue.value = "기존 체형 기록을 확인하지 못했어요. 원본은 그대로 보존했어요."
            return null
        }
        return scope
    }

    private fun commitChange(context: Context, scope: String, change: (SharedPreferences.Editor) -> Unit): Boolean {
        val prefs: SharedPreferences
        val previous: Map<String, *>
        try {
            prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val scopedKeys = storedKeys.map { scope + it }.toSet()
            previous = unconfirmedWrites[scope] ?: prefs.all.filterKeys { it in scopedKeys }
        } catch (_: Exception) {
            _issue.value = "기기 저장소에 접근하지 못했어요. 마지막으로 확인한 입력값을 유지해요."
            return false
        }
        val committed = runCatching { prefs.edit().let { change(it); it.commit() } }.getOrDefault(false)
        if (committed) {
            unconfirmedWrites.remove(scope)
            return true
        }
        unconfirmedWrites[scope] = previous
        // Best-effort compensation restores the cached map too. Disk durability remains
        // unconfirmed on I/O failure; never report this attempt as saved or deleted.
        runCatching {
            val rollback = prefs.edit()
            storedKeys.forEach { rollback.remove(scope + it) }
            previous.forEach { (key, value) ->
                when (value) {
                    is Boolean -> rollback.putBoolean(key, value)
                    is Float -> rollback.putFloat(key, value)
                    is Int -> rollback.putInt(key, value)
                    is Long -> rollback.putLong(key, value)
                    is String -> rollback.putString(key, value)
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        rollback.putStringSet(key, value as Set<String>)
                    }
                }
            }
            rollback.commit()
        }
        _issue.value = "기기 저장을 확인하지 못했어요. 마지막으로 확인한 입력값을 유지해요. 다시 시도해주세요."
        return false
    }
}
