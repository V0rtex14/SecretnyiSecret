package com.example.saktahahathonv1.ai

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.*
import kotlin.math.*

/**
 * ИИ-движок для предиктивного анализа рисков
 * Использует Claude API для прогнозирования опасных зон
 */
class AIPredictiveRiskEngine {

    companion object {
        private const val CLAUDE_API_URL = "https://api.anthropic.com/v1/messages"
        private const val MODEL = "claude-sonnet-4-20250514"
    }

    /**
     * Предсказать уровень риска для зоны в определённое время
     */
    suspend fun predictRiskForZone(
        latitude: Double,
        longitude: Double,
        targetTime: Long,
        historicalIncidents: List<AIHistoricalIncident>,
        weatherData: WeatherData?,
        eventData: List<CityEvent>
    ): RiskPrediction = withContext(Dispatchers.IO) {

        val contextPrompt = buildContextPrompt(
            latitude, longitude, targetTime,
            historicalIncidents, weatherData, eventData
        )

        try {
            val prediction = callClaudeAPI(contextPrompt)
            parseRiskPrediction(prediction)
        } catch (e: Exception) {
            algorithmicFallback(latitude, longitude, targetTime, historicalIncidents)
        }
    }

    /**
     * Генерация тепловой карты рисков для всего города
     */
    suspend fun generateCitywidePredictiveHeatmap(
        bounds: MapBounds,
        targetTime: Long,
        gridStep: Double = 0.003
    ): List<RiskHotspot> = withContext(Dispatchers.IO) {

        val hotspots = mutableListOf<RiskHotspot>()
        val jobs = mutableListOf<Deferred<RiskHotspot?>>()

        var lat = bounds.south
        while (lat <= bounds.north) {
            var lon = bounds.west
            while (lon <= bounds.east) {
                val currentLat = lat
                val currentLon = lon

                jobs.add(async {
                    try {
                        val risk = predictRiskForPoint(currentLat, currentLon, targetTime)
                        if (risk > 0.7) {
                            RiskHotspot(currentLat, currentLon, risk, targetTime)
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                })

                lon += gridStep
            }
            lat += gridStep
        }

        jobs.mapNotNull { it.await() }
    }

    /**
     * Умное предложение оптимального времени для поездки
     */
    suspend fun suggestSafestTimeWindow(
        route: List<AIGeoPoint>,
        dateRange: LongRange
    ): TimeWindowSuggestion = withContext(Dispatchers.IO) {

        val timeSlots = mutableListOf<TimeSlotRisk>()

        for (timestamp in dateRange step 3600000) {
            val avgRisk = route.map { point ->
                predictRiskForPoint(point.latitude, point.longitude, timestamp)
            }.average()

            timeSlots.add(TimeSlotRisk(timestamp, avgRisk))
        }

        val safestSlot = timeSlots.minByOrNull { it.risk }!!

        TimeWindowSuggestion(
            recommendedTime = safestSlot.timestamp,
            averageRisk = safestSlot.risk,
            alternatives = timeSlots.sortedBy { it.risk }.take(3),
            reasoning = generateReasoningForTime(safestSlot.timestamp, route)
        )
    }

    /**
     * Аномалия-детекция: выявление необычных паттернов
     */
    suspend fun detectAnomalies(
        recentIncidents: List<AIHistoricalIncident>,
        normalPatterns: IncidentPatterns
    ): List<SafetyAnomaly> = withContext(Dispatchers.IO) {

        val anomalies = mutableListOf<SafetyAnomaly>()

        val zoneGroups = recentIncidents.groupBy {
            "${(it.lat * 100).toInt()}_${(it.lon * 100).toInt()}"
        }

        for ((zoneId, incidents) in zoneGroups) {
            val recentCount = incidents.filter {
                System.currentTimeMillis() - it.timestamp < 7 * 24 * 3600000
            }.size

            val normalCount = normalPatterns.getAverageForZone(zoneId)

            if (recentCount > normalCount * 3) {
                anomalies.add(SafetyAnomaly(
                    zoneId = zoneId,
                    latitude = incidents.first().lat,
                    longitude = incidents.first().lon,
                    severity = AnomalySeverity.HIGH,
                    description = "Резкий всплеск инцидентов: $recentCount за неделю (норма: $normalCount)",
                    detectedAt = System.currentTimeMillis()
                ))
            }
        }

        anomalies
    }

    // ===== ПРИВАТНЫЕ МЕТОДЫ =====

    private suspend fun predictRiskForPoint(
        lat: Double,
        lon: Double,
        time: Long
    ): Double = withContext(Dispatchers.IO) {

        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = time
        }

        val hourOfDay = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)

        var baseRisk = 0.3

        if (hourOfDay >= 22 || hourOfDay < 6) {
            baseRisk += 0.4
        }

        if (dayOfWeek == java.util.Calendar.SATURDAY ||
            dayOfWeek == java.util.Calendar.SUNDAY) {
            baseRisk += 0.2
        }

        baseRisk += (Math.random() - 0.5) * 0.2

        baseRisk.coerceIn(0.0, 1.0)
    }

    private fun buildContextPrompt(
        lat: Double,
        lon: Double,
        time: Long,
        incidents: List<AIHistoricalIncident>,
        weather: WeatherData?,
        events: List<CityEvent>
    ): String {
        return """
Проанализируй безопасность зоны города:
Координаты: $lat, $lon
Время прогноза: ${formatTime(time)}

Исторические инциденты в радиусе 500м за последние 90 дней:
${incidents.take(10).joinToString("\n") { "- ${it.type} (severity: ${it.severity})" }}

${weather?.let { "Погода: ${it.condition}, температура: ${it.temperature}°C" } ?: ""}

${if (events.isNotEmpty()) {
            "Городские события поблизости:\n" + events.take(3).joinToString("\n") { "- ${it.name} (${it.attendees} человек)" }
        } else ""}

Оцени уровень риска от 0.0 до 1.0 и объясни почему. Ответь только JSON:
{
  "risk": 0.0-1.0,
  "factors": ["фактор1", "фактор2"],
  "recommendation": "текст"
}
        """.trimIndent()
    }

    private suspend fun callClaudeAPI(prompt: String): String = withContext(Dispatchers.IO) {

        val requestBody = JSONObject().apply {
            put("model", MODEL)
            put("max_tokens", 500)
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        val connection = URL(CLAUDE_API_URL).openConnection() as HttpURLConnection
        connection.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("anthropic-version", "2023-06-01")
            doOutput = true
        }

        connection.outputStream.use { it.write(requestBody.toString().toByteArray()) }

        val response = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()

        val jsonResponse = JSONObject(response)
        jsonResponse.getJSONArray("content")
            .getJSONObject(0)
            .getString("text")
    }

    private fun parseRiskPrediction(apiResponse: String): RiskPrediction {
        return try {
            val json = JSONObject(apiResponse)
            RiskPrediction(
                riskLevel = json.getDouble("risk"),
                factors = parseFactors(json),
                recommendation = json.getString("recommendation"),
                confidence = 0.85
            )
        } catch (e: Exception) {
            RiskPrediction(0.5, emptyList(), "Не удалось получить прогноз", 0.0)
        }
    }

    private fun parseFactors(json: JSONObject): List<String> {
        val factors = mutableListOf<String>()
        val factorsArray = json.optJSONArray("factors")
        if (factorsArray != null) {
            for (i in 0 until factorsArray.length()) {
                factors.add(factorsArray.getString(i))
            }
        }
        return factors
    }

    private fun algorithmicFallback(
        lat: Double,
        lon: Double,
        time: Long,
        incidents: List<AIHistoricalIncident>
    ): RiskPrediction {

        val nearbyIncidents = incidents.filter {
            calculateDistance(lat, lon, it.lat, it.lon) < 500.0
        }

        val avgSeverity = nearbyIncidents.map { it.severity }.average()
        val riskLevel = (avgSeverity / 5.0).coerceIn(0.0, 1.0)

        return RiskPrediction(
            riskLevel = riskLevel,
            factors = listOf("${nearbyIncidents.size} инцидентов поблизости"),
            recommendation = if (riskLevel > 0.7) "Избегайте эту зону" else "Зона относительно безопасна",
            confidence = 0.65
        )
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        return R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun formatTime(timestamp: Long): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
            .format(java.util.Date(timestamp))
    }

    private fun generateReasoningForTime(timestamp: Long, route: List<AIGeoPoint>): String {
        val hour = java.util.Calendar.getInstance().apply {
            timeInMillis = timestamp
        }.get(java.util.Calendar.HOUR_OF_DAY)

        return when (hour) {
            in 6..9 -> "Утренние часы: хорошая видимость и много людей"
            in 10..16 -> "Дневное время: оптимальная безопасность"
            in 17..20 -> "Вечерние часы: ещё светло, но людей меньше"
            else -> "Ночное время: используйте только освещённые маршруты"
        }
    }
}

// ===== МОДЕЛИ ДАННЫХ (с префиксом AI чтобы избежать конфликтов) =====

data class RiskPrediction(
    val riskLevel: Double,
    val factors: List<String>,
    val recommendation: String,
    val confidence: Double
)

data class RiskHotspot(
    val latitude: Double,
    val longitude: Double,
    val predictedRisk: Double,
    val forecastTime: Long
)

data class TimeWindowSuggestion(
    val recommendedTime: Long,
    val averageRisk: Double,
    val alternatives: List<TimeSlotRisk>,
    val reasoning: String
)

data class TimeSlotRisk(
    val timestamp: Long,
    val risk: Double
)

data class AIHistoricalIncident(
    val lat: Double,
    val lon: Double,
    val type: String,
    val severity: Int,
    val timestamp: Long
)

data class WeatherData(
    val condition: String,
    val temperature: Double,
    val precipitation: Double
)

data class CityEvent(
    val name: String,
    val location: AIGeoPoint,
    val attendees: Int,
    val startTime: Long
)

data class SafetyAnomaly(
    val zoneId: String,
    val latitude: Double,
    val longitude: Double,
    val severity: AnomalySeverity,
    val description: String,
    val detectedAt: Long
)

enum class AnomalySeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}

data class MapBounds(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double
)

data class AIGeoPoint(
    val latitude: Double,
    val longitude: Double
)

class IncidentPatterns {
    private val zoneAverages = mutableMapOf<String, Double>()

    fun getAverageForZone(zoneId: String): Double {
        return zoneAverages.getOrDefault(zoneId, 2.0)
    }

    fun updateZoneAverage(zoneId: String, average: Double) {
        zoneAverages[zoneId] = average
    }
}