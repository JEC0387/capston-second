package com.jeiu.fashionproject.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeiu.fashionproject.data.FashionQuery
import com.jeiu.fashionproject.data.Gender
import com.jeiu.fashionproject.data.NaverImageSearchRepository
import com.jeiu.fashionproject.data.ShopImageResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ShopUiState {
    object Idle : ShopUiState()
    object Loading : ShopUiState()
    data class Success(val results: List<ShopImageResult>, val keyword: String = "") : ShopUiState()
    data class Error(val message: String) : ShopUiState()
    /** 옷과 관련 없는 검색어라 검색하지 않음 */
    data class NotFashion(val message: String) : ShopUiState()
}

class ShopViewModel(
    private val searchImages: suspend (String, Int) -> List<ShopImageResult> = { query, display ->
        NaverImageSearchRepository.search(query, display)
    }
) : ViewModel() {

    private val _state = MutableStateFlow<ShopUiState>(ShopUiState.Loading)
    /** 스타일 검색 결과 */
    val state: StateFlow<ShopUiState> = _state

    private val _productState = MutableStateFlow<ShopUiState>(ShopUiState.Idle)
    /** 상품 검색 — 온라인 쇼핑 결과 (옷 상품만) */
    val productState: StateFlow<ShopUiState> = _productState

    // 홈의 "전체보기"에서 넘겨준 검색어. 쇼핑 탭이 열릴 때 한 번 꺼내 검색창에 넣는다.
    private var presetQuery: String? = null

    fun setPresetQuery(query: String) { presetQuery = query }

    fun consumePresetQuery(): String? = presetQuery.also { presetQuery = null }

    // 같은 검색어+성별로 다시 안 그려지게 중복 호출 방지
    private var lastKey: String? = null
    private var lastProductKey: String? = null
    private var styleJob: Job? = null
    private var productJob: Job? = null
    private var styleRequest = 0L
    private var productRequest = 0L

    /**
     * 스타일 검색 — 옷 스타일과 관련된 검색어만 (FashionQuery.stylePlan).
     * 빈 검색어면 성별 기본 검색어("남성 데일리룩 코디")로 찾는다.
     */
    fun search(query: String, gender: Gender?) {
        val plan = FashionQuery.stylePlan(query, gender)
        val key = "${plan.query}|${plan.message}|$gender"
        if (key == lastKey) return
        lastKey = key
        val request = ++styleRequest
        styleJob?.cancel()
        if (plan.query == null) { _state.value = ShopUiState.NotFashion(plan.message ?: FashionQuery.STYLE_HINT); return }

        _state.value = ShopUiState.Loading
        styleJob = viewModelScope.launch {
            val next = try {
                val results = searchImages(plan.query, 50)
                    .filter { FashionQuery.keep(it.title, plan, gender) }
                    .take(30)
                ShopUiState.Success(results, plan.query)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (request == styleRequest) lastKey = null
                ShopUiState.Error("이미지를 불러오지 못했어요. 서버 연결을 확인해 주세요.")
            }
            if (request == styleRequest) _state.value = next
        }
    }

    /** 상품 검색 — 옷 상품 검색어일 때만 온라인 쇼핑 이미지를 찾는다 (FashionQuery.productPlan) */
    fun searchProducts(query: String, gender: Gender?) {
        val plan = FashionQuery.productPlan(query, gender)
        val key = "${plan.query}|${plan.message}|$gender"
        if (key == lastProductKey) return
        lastProductKey = key
        val request = ++productRequest
        productJob?.cancel()
        when {
            plan.message != null -> { _productState.value = ShopUiState.NotFashion(plan.message); return }
            plan.query == null -> { _productState.value = ShopUiState.Idle; return }
        }
        _productState.value = ShopUiState.Loading
        productJob = viewModelScope.launch {
            val next = try {
                val results = searchImages("${plan.query} 상품", 50)
                    .filter { FashionQuery.keep(it.title, plan, gender) }
                    .take(20)
                ShopUiState.Success(results, plan.query!!)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (request == productRequest) lastProductKey = null
                ShopUiState.Error("상품을 불러오지 못했어요. 서버 연결을 확인해 주세요.")
            }
            if (request == productRequest) _productState.value = next
        }
    }
}
