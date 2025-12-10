package com.example.saktahahathonv1.history

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

class HistoryViewModel : ViewModel() {

    private val _routeHistory = MutableLiveData<List<RouteHistoryItem>>()
    val routeHistory: LiveData<List<RouteHistoryItem>> = _routeHistory

    private val _statistics = MutableLiveData<RouteStatistics>()
    val statistics: LiveData<RouteStatistics> = _statistics

    private val _state = MutableLiveData<HistoryState>(HistoryState.Loading)
    val state: LiveData<HistoryState> = _state

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            try {
                _state.value = HistoryState.Loading

                // Загрузка истории маршрутов (заглушка)
                val mockHistory = listOf(
                    RouteHistoryItem(
                        id = "route_1",
                        startPoint = GeoPoint(42.8746, 74.5698),
                        endPoint = GeoPoint(42.8800, 74.5750),
                        startAddress = "пр. Чуй, 101",
                        endAddress = "ул. Токтогула, 45",
                        distance = 1500.0,
                        duration = 18 * 60, // 18 минут
                        safetyScore = 8.5,
                        timestamp = System.currentTimeMillis() - 86400000, // вчера
                        routeType = "Безопасный"
                    ),
                    RouteHistoryItem(
                        id = "route_2",
                        startPoint = GeoPoint(42.8750, 74.5700),
                        endPoint = GeoPoint(42.8720, 74.5680),
                        startAddress = "ТЦ Bishkek Park",
                        endAddress = "ул. Исанова, 12",
                        distance = 850.0,
                        duration = 12 * 60, // 12 минут
                        safetyScore = 9.2,
                        timestamp = System.currentTimeMillis() - 172800000, // 2 дня назад
                        routeType = "Быстрый"
                    ),
                    RouteHistoryItem(
                        id = "route_3",
                        startPoint = GeoPoint(42.8730, 74.5690),
                        endPoint = GeoPoint(42.8780, 74.5730),
                        startAddress = "пр. Манаса, 23",
                        endAddress = "Ала-Тоо площадь",
                        distance = 2200.0,
                        duration = 25 * 60, // 25 минут
                        safetyScore = 7.8,
                        timestamp = System.currentTimeMillis() - 259200000, // 3 дня назад
                        routeType = "Освещенный"
                    )
                )

                _routeHistory.value = mockHistory

                // Вычисляем статистику
                val stats = RouteStatistics(
                    totalRoutes = mockHistory.size,
                    totalDistance = mockHistory.sumOf { it.distance },
                    totalDuration = mockHistory.sumOf { it.duration.toLong() },
                    averageSafety = mockHistory.map { it.safetyScore }.average()
                )

                _statistics.value = stats

                _state.value = if (mockHistory.isEmpty()) {
                    HistoryState.Empty
                } else {
                    HistoryState.Success
                }

            } catch (e: Exception) {
                _state.value = HistoryState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun deleteRoute(routeId: String) {
        viewModelScope.launch {
            try {
                val currentHistory = _routeHistory.value?.toMutableList() ?: return@launch
                currentHistory.removeAll { it.id == routeId }
                _routeHistory.value = currentHistory

                // Пересчитываем статистику
                if (currentHistory.isNotEmpty()) {
                    val stats = RouteStatistics(
                        totalRoutes = currentHistory.size,
                        totalDistance = currentHistory.sumOf { it.distance },
                        totalDuration = currentHistory.sumOf { it.duration.toLong() },
                        averageSafety = currentHistory.map { it.safetyScore }.average()
                    )
                    _statistics.value = stats
                } else {
                    _statistics.value = RouteStatistics(0, 0.0, 0, 0.0)
                }

                _state.value = if (currentHistory.isEmpty()) {
                    HistoryState.Empty
                } else {
                    HistoryState.Success
                }

            } catch (e: Exception) {
                _state.value = HistoryState.Error("Failed to delete route: ${e.message}")
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            try {
                _routeHistory.value = emptyList()
                _statistics.value = RouteStatistics(0, 0.0, 0, 0.0)
                _state.value = HistoryState.Empty

            } catch (e: Exception) {
                _state.value = HistoryState.Error("Failed to clear history: ${e.message}")
            }
        }
    }
}

data class RouteHistoryItem(
    val id: String,
    val startPoint: GeoPoint,
    val endPoint: GeoPoint,
    val startAddress: String,
    val endAddress: String,
    val distance: Double, // в метрах
    val duration: Int, // в секундах
    val safetyScore: Double, // 0-10
    val timestamp: Long,
    val routeType: String
) {
    fun getDistanceText(): String {
        return if (distance < 1000) {
            "${distance.toInt()} м"
        } else {
            "%.1f км".format(distance / 1000)
        }
    }

    fun getDurationText(): String {
        val minutes = duration / 60
        return if (minutes < 60) {
            "$minutes мин"
        } else {
            val hours = minutes / 60
            val remainingMinutes = minutes % 60
            "${hours}ч ${remainingMinutes}м"
        }
    }

    fun getFormattedDate(): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val days = diff / 86400000

        return when {
            days == 0L -> "Сегодня"
            days == 1L -> "Вчера"
            days < 7 -> "$days дн. назад"
            else -> {
                val weeks = days / 7
                "$weeks нед. назад"
            }
        }
    }
}

data class RouteStatistics(
    val totalRoutes: Int,
    val totalDistance: Double, // в метрах
    val totalDuration: Long, // в секундах
    val averageSafety: Double
) {
    fun getTotalDistanceText(): String {
        return if (totalDistance < 1000) {
            "${totalDistance.toInt()} м"
        } else {
            "%.1f км".format(totalDistance / 1000)
        }
    }

    fun getAverageSafetyText(): String {
        return "%.1f".format(averageSafety)
    }
}

sealed class HistoryState {
    object Loading : HistoryState()
    object Success : HistoryState()
    object Empty : HistoryState()
    data class Error(val message: String) : HistoryState()
}
