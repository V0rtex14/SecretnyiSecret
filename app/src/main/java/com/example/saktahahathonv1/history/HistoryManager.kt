package com.example.saktahahathonv1.history

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.osmdroid.util.GeoPoint
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Менеджер истории маршрутов
 */
class HistoryManager(private val context: Context) {

    private val gson = Gson()
    private val historyFile = File(context.filesDir, "route_history.json")
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    /**
     * Получить историю маршрутов пользователя
     */
    fun getHistory(userId: String): List<RouteHistory> {
        return try {
            if (!historyFile.exists()) {
                createDemoHistory()
            }

            val json = historyFile.readText()
            val type = object : TypeToken<List<RouteHistoryJson>>() {}.type
            val historyJson: List<RouteHistoryJson> = gson.fromJson(json, type) ?: emptyList()

            historyJson.map { it.toRouteHistory() }
                .filter { it.userId == userId }
                .sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Добавить маршрут в историю
     */
    fun addRoute(route: RouteHistory): Boolean {
        return try {
            val history = getAllHistory().toMutableList()
            history.add(route)
            saveHistory(history)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Удалить маршрут из истории
     */
    fun deleteRoute(routeId: String): Boolean {
        return try {
            val history = getAllHistory().toMutableList()
            history.removeAll { it.id == routeId }
            saveHistory(history)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Переключить избранное
     */
    fun toggleFavorite(routeId: String): Boolean {
        return try {
            val history = getAllHistory().toMutableList()
            val index = history.indexOfFirst { it.id == routeId }
            if (index != -1) {
                history[index] = history[index].copy(isFavorite = !history[index].isFavorite)
                saveHistory(history)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Получить статистику
     */
    fun getStats(userId: String): RouteStats {
        val history = getHistory(userId)

        if (history.isEmpty()) {
            return RouteStats(
                totalRoutes = 0,
                totalDistance = 0.0,
                totalDuration = 0.0,
                averageSafetyScore = 0.0,
                mostUsedFrom = "-",
                mostUsedTo = "-"
            )
        }

        val totalDistance = history.sumOf { it.distance }
        val totalDuration = history.sumOf { it.duration }
        val avgSafety = history.map { it.safetyScore }.average()

        val mostUsedFrom = history.groupBy { it.fromAddress }
            .maxByOrNull { it.value.size }?.key ?: "-"

        val mostUsedTo = history.groupBy { it.toAddress }
            .maxByOrNull { it.value.size }?.key ?: "-"

        return RouteStats(
            totalRoutes = history.size,
            totalDistance = totalDistance,
            totalDuration = totalDuration,
            averageSafetyScore = avgSafety,
            mostUsedFrom = mostUsedFrom,
            mostUsedTo = mostUsedTo
        )
    }

    /**
     * Очистить всю историю
     */
    fun clearHistory(userId: String): Boolean {
        return try {
            val history = getAllHistory().toMutableList()
            history.removeAll { it.userId == userId }
            saveHistory(history)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getAllHistory(): List<RouteHistory> {
        return try {
            if (!historyFile.exists()) {
                return emptyList()
            }

            val json = historyFile.readText()
            val type = object : TypeToken<List<RouteHistoryJson>>() {}.type
            val historyJson: List<RouteHistoryJson> = gson.fromJson(json, type) ?: emptyList()
            historyJson.map { it.toRouteHistory() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveHistory(history: List<RouteHistory>) {
        val historyJson = history.map { it.toJson() }
        val json = gson.toJson(historyJson)
        historyFile.writeText(json)
    }

    private fun createDemoHistory() {
        val demoHistory = listOf(
            RouteHistory(
                id = "route_1",
                userId = "current_user",
                fromAddress = "пр. Чуй, 123",
                toAddress = "ул. Ибраимова, 45",
                fromLocation = GeoPoint(42.8746, 74.5698),
                toLocation = GeoPoint(42.8766, 74.5708),
                distance = 2500.0,
                duration = 1800.0,
                timestamp = System.currentTimeMillis() - 86400000, // 1 день назад
                routeType = "Безопасный",
                safetyScore = 8.5,
                isFavorite = true
            ),
            RouteHistory(
                id = "route_2",
                userId = "current_user",
                fromAddress = "ул. Тоголока Молдо, 1",
                toAddress = "Дордой Плаза",
                fromLocation = GeoPoint(42.8656, 74.5898),
                toLocation = GeoPoint(42.8706, 74.5808),
                distance = 3200.0,
                duration = 2400.0,
                timestamp = System.currentTimeMillis() - 172800000, // 2 дня назад
                routeType = "Прямой",
                safetyScore = 7.0
            ),
            RouteHistory(
                id = "route_3",
                userId = "current_user",
                fromAddress = "Ош Базар",
                toAddress = "пр. Манаса, 100",
                fromLocation = GeoPoint(42.8556, 74.5998),
                toLocation = GeoPoint(42.8806, 74.5608),
                distance = 4100.0,
                duration = 3000.0,
                timestamp = System.currentTimeMillis() - 259200000, // 3 дня назад
                routeType = "Через УПСМ",
                safetyScore = 9.0,
                isFavorite = true
            )
        )
        saveHistory(demoHistory)
    }
}

// JSON модели
private data class RouteHistoryJson(
    val id: String,
    val userId: String,
    val fromAddress: String,
    val toAddress: String,
    val fromLat: Double,
    val fromLon: Double,
    val toLat: Double,
    val toLon: Double,
    val distance: Double,
    val duration: Double,
    val timestamp: Long,
    val routeType: String,
    val safetyScore: Double,
    val isFavorite: Boolean = false
) {
    fun toRouteHistory() = RouteHistory(
        id = id,
        userId = userId,
        fromAddress = fromAddress,
        toAddress = toAddress,
        fromLocation = GeoPoint(fromLat, fromLon),
        toLocation = GeoPoint(toLat, toLon),
        distance = distance,
        duration = duration,
        timestamp = timestamp,
        routeType = routeType,
        safetyScore = safetyScore,
        isFavorite = isFavorite
    )
}

private fun RouteHistory.toJson() = RouteHistoryJson(
    id = id,
    userId = userId,
    fromAddress = fromAddress,
    toAddress = toAddress,
    fromLat = fromLocation.latitude,
    fromLon = fromLocation.longitude,
    toLat = toLocation.latitude,
    toLon = toLocation.longitude,
    distance = distance,
    duration = duration,
    timestamp = timestamp,
    routeType = routeType,
    safetyScore = safetyScore,
    isFavorite = isFavorite
)
