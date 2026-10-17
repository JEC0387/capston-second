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
import com.jeiu.fashionproject.data.WeatherInfo
import com.jeiu.fashionproject.data.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

// 위치 권한이 없거나 위치를 못 받으면 서울 시청 기준 날씨를 보여준다.
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

    // 홈 ↔ 날씨 코디 화면을 오갈 때마다 다시 부르지 않도록 한 번 성공하면 재사용
    private var loaded = false

    /** 현재 위치의 실시간 날씨를 OpenWeatherMap에서 가져온다. */
    fun fetchWeather(force: Boolean = false) {
        if (loaded && !force) return
        _state.value = WeatherUiState.Loading
        viewModelScope.launch {
            val (lat, lon) = currentLocation() ?: (DEFAULT_LAT to DEFAULT_LON)
            _state.value = try {
                val info = WeatherRepository.fetch(lat, lon)
                loaded = true
                WeatherUiState.Success(info)
            } catch (e: Exception) {
                WeatherUiState.Error("날씨를 불러오지 못했어요. 인터넷 연결을 확인해 주세요.")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(): Pair<Double, Double>? {
        val context = getApplication<Application>()
        val granted = listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (!granted) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        // 위치를 5초 안에 못 받으면 기본 위치(서울)로 넘어간다
        return withTimeoutOrNull(5_000) {
            suspendCancellableCoroutine<Pair<Double, Double>?> { cont ->
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc?.let { it.latitude to it.longitude }) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            }
        }
    }
}
