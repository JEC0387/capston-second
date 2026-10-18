package com.jeiu.fashionproject.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import com.jeiu.fashionproject.data.DbSync
import com.jeiu.fashionproject.data.Gender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 마이페이지에서 고른 성별을 SharedPreferences(+ 서버 DB users.gender)에 저장하고, 앱 전체(쇼핑/코디 추천)에서
 * 공유해서 쓸 수 있게 들고 있는 뷰모델. BodyAnalysisViewModel처럼 Context는 호출 시점에
 * 받아서 쓴다 (AndroidViewModel을 새로 끌어오지 않기 위함).
 */
class UserPreferencesViewModel : ViewModel() {

    private val _gender = MutableStateFlow<Gender?>(null)
    val gender: StateFlow<Gender?> = _gender

    private var loaded = false

    /** 앱 시작 시 한 번만 저장된 값을 불러온다. */
    fun loadIfNeeded(context: Context) {
        if (loaded) return
        loaded = true
        val saved = prefs(context).getString(KEY_GENDER, null)
        _gender.value = saved?.let { runCatching { Gender.valueOf(it) }.getOrNull() }
        // 서버 DB(users.gender)도 폰과 같게 맞춰 둔다
        _gender.value?.let { DbSync.pushGender(it.name) }
    }

    fun setGender(context: Context, gender: Gender) {
        _gender.value = gender
        prefs(context).edit().putString(KEY_GENDER, gender.name).apply()
        DbSync.pushGender(gender.name)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "user_prefs"
        private const val KEY_GENDER = "gender"

        /** 백업 코드로 복원할 때 — 서버의 성별 설정을 폰에 써 둔다 (FEMALE / MALE, 그 밖의 값은 무시) */
        fun writeForRestore(context: Context, gender: String?) {
            val valid = gender?.let { g -> Gender.entries.firstOrNull { it.name == g } } ?: return
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_GENDER, valid.name).commit()
        }
    }
}
