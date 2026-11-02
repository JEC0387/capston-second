package com.jeiu.fashionproject.data

import android.content.Context
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
    private val _current = MutableStateFlow<ManualBodyMeasurements?>(null)
    val current: StateFlow<ManualBodyMeasurements?> = _current
    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved
    private val _issue = MutableStateFlow<String?>(null)
    val issue: StateFlow<String?> = _issue

    fun load(context: Context) {
        try {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("save", false)) return
        val value = ManualBodyMeasurements(
            prefs.getFloat("height", 0f), readOptional(prefs, "shoulder"),
            readOptional(prefs, "chest"), readOptional(prefs, "waist"),
            readOptional(prefs, "hip"), readOptional(prefs, "leg"),
            readOptional(prefs, "torso"), prefs.getLong("entered_at", 0L)
        )
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

    fun update(context: Context, value: ManualBodyMeasurements, saveOnDevice: Boolean): Boolean {
        if (!value.isValid()) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val edit = prefs.edit().clear()
        if (saveOnDevice) {
            edit.putBoolean("save", true).putLong("entered_at", value.enteredAt)
                .putFloat("height", value.heightCm)
            listOf(value.shoulderWidthCm, value.chestCircumferenceCm,
                value.waistCircumferenceCm, value.hipCircumferenceCm,
                value.legLengthCm, value.torsoLengthCm).forEachIndexed { index, number ->
                if (number != null) edit.putFloat(keys[index + 1], number)
            }
        }
        if (!edit.commit()) return false
        _current.value = value
        _saved.value = saveOnDevice
        _issue.value = null
        return true
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        _current.value = null
        _saved.value = false
        _issue.value = null
    }
}
