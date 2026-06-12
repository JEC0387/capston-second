package com.jeiu.fashionproject.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.jeiu.fashionproject.data.WeatherInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val DEFAULT_LAT = 37.5665
private const val DEFAULT_LON = 126.9780

sealed class WeatherUiState {
    object Loading : WeatherUiState()
    data class Success(val info: WeatherInfo) : WeatherUiState()
    data class Error(val message: String) : WeatherUiState()
}

class WeatherViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val state: StateFlow<WeatherUiState> = _state

    fun fetchWeather() {
        // 따뜻한 날씨 고정 (20~27°C warm 카테고리)
        _state.value = WeatherUiState.Success(WeatherInfo("맑음", 25, 50, 800))
    }
}
