package com.jeiu.fashionproject.ui.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.jeiu.fashionproject.data.DayForecast
import com.jeiu.fashionproject.data.WeatherInfo
import com.jeiu.fashionproject.data.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

// 위치를 못 받을 때 사용하는 기본 좌표 (서울시청)
private const val DEFAULT_LAT = 37.5665
private const val DEFAULT_LON = 126.9780
private const val LOCATION_TIMEOUT_MS = 5_000L

/** 5일 예보 상태 (계획 4 — 3단계) */
sealed class ForecastUiState {
    object Loading : ForecastUiState()
    data class Success(val days: List<DayForecast>) : ForecastUiState()
    data class Error(val message: String) : ForecastUiState()
}

sealed class WeatherUiState {
    object Loading : WeatherUiState()
    data class Success(val info: WeatherInfo) : WeatherUiState()
    data class Error(val message: String) : WeatherUiState()
}

class WeatherViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val state: StateFlow<WeatherUiState> = _state

    private val _forecast = MutableStateFlow<ForecastUiState>(ForecastUiState.Loading)
    val forecast: StateFlow<ForecastUiState> = _forecast

    private val fusedClient = LocationServices.getFusedLocationProviderClient(application)

    /**
     * 현재 위치를 받아 실시간 날씨를 조회한다. 서버 우선, 실패 시 직접 API, 위치 실패 시 기본 좌표.
     * 5일 예보도 함께 받는다 (예보는 OpenWeatherMap에 직접 요청).
     */
    fun fetchWeather() {
        _state.value = WeatherUiState.Loading
        _forecast.value = ForecastUiState.Loading
        viewModelScope.launch {
            // 위치를 5초 안에 못 받으면(에뮬레이터·실내 등) 기본 좌표로 진행한다
            val (lat, lon) = withTimeoutOrNull(LOCATION_TIMEOUT_MS) { getLocation() } ?: (DEFAULT_LAT to DEFAULT_LON)

            launch {
                runCatching { WeatherRepository.fetchForecast(lat, lon) }
                    .onSuccess { _forecast.value = ForecastUiState.Success(it) }
                    .onFailure { _forecast.value = ForecastUiState.Error("예보를 불러오지 못했어요") }
            }

            // 1) 백엔드 서버 우선 시도
            val server = com.jeiu.fashionproject.data.BackendApi.fetchWeather(lat, lon)
            if (server != null) {
                _state.value = WeatherUiState.Success(
                    WeatherInfo(server.condition, server.temp, server.humidity, server.conditionId)
                )
                return@launch
            }

            // 2) 서버 미연결 시 OpenWeatherMap 직접 호출 (기존 방식)
            runCatching { WeatherRepository.fetch(lat, lon) }
                .onSuccess { _state.value = WeatherUiState.Success(it) }
                .onFailure { _state.value = WeatherUiState.Error("날씨를 불러오지 못했어요") }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLocation(): Pair<Double, Double>? {
        val ctx = getApplication<Application>()
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) return null

        return suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    cont.resume(loc?.let { it.latitude to it.longitude })
                }
                .addOnFailureListener { cont.resume(null) }
            cont.invokeOnCancellation { cts.cancel() }
        }
    }
}
