package com.jeiu.fashionproject.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeiu.fashionproject.data.Gender
import com.jeiu.fashionproject.data.NaverImageSearchRepository
import com.jeiu.fashionproject.data.ShopImageResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ShopUiState {
    object Loading : ShopUiState()
    data class Success(val results: List<ShopImageResult>) : ShopUiState()
    data class Error(val message: String) : ShopUiState()
}

class ShopViewModel : ViewModel() {

    private val _state = MutableStateFlow<ShopUiState>(ShopUiState.Loading)
    val state: StateFlow<ShopUiState> = _state

    // 홈의 "전체보기"에서 넘겨준 검색어. 쇼핑 탭이 열릴 때 한 번 꺼내 검색창에 넣는다.
    private var presetQuery: String? = null

    fun setPresetQuery(query: String) { presetQuery = query }

    fun consumePresetQuery(): String? = presetQuery.also { presetQuery = null }

    // 같은 검색어+성별로 다시 안 그려지게 중복 호출 방지
    private var lastKey: String? = null

    /**
     * query가 비어있으면 성별 기반 기본 추천 검색어를, 아니면 "성별 + 검색어"를 네이버
     * 이미지 검색에 던진다. 성별을 모르면(null) 검색어만 그대로 쓴다.
     */
    fun search(query: String, gender: Gender?) {
        val effectiveQuery = buildQuery(query, gender)
        if (effectiveQuery == lastKey) return
        lastKey = effectiveQuery

        _state.value = ShopUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                ShopUiState.Success(NaverImageSearchRepository.search(effectiveQuery))
            } catch (e: Exception) {
                ShopUiState.Error(e.message ?: "이미지를 불러오지 못했어요.")
            }
        }
    }

    private fun buildQuery(query: String, gender: Gender?): String {
        val genderKeyword = gender?.label // "여성" / "남성"
        return when {
            query.isBlank() && genderKeyword != null -> "$genderKeyword 데일리룩 추천"
            query.isBlank() -> "데일리룩 추천"
            genderKeyword != null -> "$genderKeyword $query"
            else -> query
        }
    }
}
