package com.example.saktahahathonv1.escort

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import kotlin.math.*

class EscortViewModel : ViewModel() {

    private val _routeState = MutableLiveData<RouteProgress>()
    val routeState: LiveData<RouteProgress> = _routeState

    private val _navigationState = MutableLiveData<NavigationState>()
    val navigationState: LiveData<NavigationState> = _navigationState

    private val _currentLocation = MutableLiveData<GeoPoint>()
    val currentLocation: LiveData<GeoPoint> = _currentLocation

    fun startNavigation(destination: GeoPoint, currentLocation: GeoPoint) {
        viewModelScope.launch {
            try {
                _navigationState.value = NavigationState.Active

                // Симуляция построения маршрута
                val totalDistance = calculateDistance(currentLocation, destination)
                val estimatedTime = (totalDistance / 1.4).toInt() // 1.4 м/с средняя скорость ходьбы

                _routeState.value = RouteProgress(
                    distanceRemaining = totalDistance,
                    timeRemaining = estimatedTime,
                    progressPercent = 0,
                    currentInstruction = "Начните движение к пункту назначения",
                    nextTurnDistance = 150
                )

                _currentLocation.value = currentLocation

            } catch (e: Exception) {
                _navigationState.value = NavigationState.Error(e.message ?: "Navigation error")
            }
        }
    }

    fun updateLocation(newLocation: GeoPoint, destination: GeoPoint, totalDistance: Double) {
        viewModelScope.launch {
            try {
                _currentLocation.value = newLocation

                val distanceRemaining = calculateDistance(newLocation, destination)
                val progressPercent = ((totalDistance - distanceRemaining) / totalDistance * 100).toInt()
                val timeRemaining = (distanceRemaining / 1.4).toInt()

                // Определяем следующую инструкцию
                val instruction = when {
                    distanceRemaining < 50 -> "Вы почти пришли"
                    distanceRemaining < 200 -> "Продолжайте прямо"
                    else -> "Следуйте по маршруту"
                }

                _routeState.value = RouteProgress(
                    distanceRemaining = distanceRemaining,
                    timeRemaining = timeRemaining,
                    progressPercent = progressPercent,
                    currentInstruction = instruction,
                    nextTurnDistance = distanceRemaining.toInt() / 3
                )

                // Проверка прибытия
                if (distanceRemaining < 30) {
                    _navigationState.value = NavigationState.Arrived
                }

            } catch (e: Exception) {
                _navigationState.value = NavigationState.Error(e.message ?: "Location update error")
            }
        }
    }

    fun stopNavigation() {
        viewModelScope.launch {
            _navigationState.value = NavigationState.Stopped
        }
    }

    fun sendSOS() {
        viewModelScope.launch {
            try {
                _navigationState.value = NavigationState.SOS
                // Здесь будет логика отправки SOS
                delay(1000)
                _navigationState.value = NavigationState.SOSSent
            } catch (e: Exception) {
                _navigationState.value = NavigationState.Error("Failed to send SOS")
            }
        }
    }

    private fun calculateDistance(point1: GeoPoint, point2: GeoPoint): Double {
        val earthRadius = 6371000.0 // meters

        val dLat = Math.toRadians(point2.latitude - point1.latitude)
        val dLon = Math.toRadians(point2.longitude - point1.longitude)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(point1.latitude)) *
                cos(Math.toRadians(point2.latitude)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }
}

data class RouteProgress(
    val distanceRemaining: Double,
    val timeRemaining: Int, // в секундах
    val progressPercent: Int,
    val currentInstruction: String,
    val nextTurnDistance: Int
) {
    fun getDistanceText(): String {
        return if (distanceRemaining < 1000) {
            "${distanceRemaining.toInt()} м"
        } else {
            "%.1f км".format(distanceRemaining / 1000)
        }
    }

    fun getTimeText(): String {
        val minutes = timeRemaining / 60
        return if (minutes < 1) {
            "$timeRemaining сек"
        } else {
            "$minutes мин"
        }
    }
}

sealed class NavigationState {
    object Active : NavigationState()
    object Stopped : NavigationState()
    object Arrived : NavigationState()
    object SOS : NavigationState()
    object SOSSent : NavigationState()
    data class Error(val message: String) : NavigationState()
}
