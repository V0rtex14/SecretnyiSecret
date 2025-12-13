package com.example.saktahahathonv1.ai

import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * SafeGuardian AI - Персональный ИИ-телохранитель
 */
class SafeGuardianAI(
    private val userProfile: UserProfile,
    private val locationTracker: LocationTracker
) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isActive = false
    private var currentRoute: List<GeoPoint>? = null
    private val alertHistory = mutableListOf<AIAlert>()

    companion object {
        private const val CLAUDE_API_URL = "https://api.anthropic.com/v1/messages"
        private const val MODEL = "claude-sonnet-4-20250514"
        private const val CHECK_INTERVAL = 30000L
        private const val DANGER_PROXIMITY_THRESHOLD = 200.0
    }

    /**
     * Активация ИИ-телохранителя
     */
    fun activate(route: List<GeoPoint>) {
        isActive = true
        currentRoute = route

        scope.launch {
            while (isActive) {
                performSafetyCheck()
                delay(CHECK_INTERVAL)
            }
        }
    }

    fun deactivate() {
        isActive = false
    }

    /**
     * Основная логика проверки безопасности
     */
    private suspend fun performSafetyCheck() = withContext(Dispatchers.IO) {

        val currentLocation = locationTracker.getCurrentLocation()
        val nearbyThreats = identifyNearbyThreats(currentLocation)

        if (nearbyThreats.isNotEmpty()) {
            val aiResponse = consultAI(currentLocation, nearbyThreats)

            when (aiResponse.severity) {
                ThreatSeverity.CRITICAL -> {
                    triggerCriticalAlert(aiResponse)
                }
                ThreatSeverity.HIGH -> {
                    sendProactiveWarning(aiResponse)
                }
                ThreatSeverity.MEDIUM -> {
                    // Логируем
                }
                ThreatSeverity.LOW -> {
                    // Ничего не делаем
                }
            }
        }
    }

    /**
     * Консультация с ИИ о текущей ситуации
     */
    private suspend fun consultAI(
        location: GeoPoint,
        threats: List<Threat>
    ): AIResponse = withContext(Dispatchers.IO) {

        val prompt = buildContextualPrompt(location, threats)

        try {
            val apiResponse = callClaudeAPI(prompt)
            parseAIResponse(apiResponse)
        } catch (e: Exception) {
            AIResponse(
                severity = ThreatSeverity.MEDIUM,
                message = "Обнаружена потенциальная опасность",
                actions = listOf("Будьте внимательны", "Рассмотрите альтернативный маршрут"),
                reasoning = "Базовая оценка рисков"
            )
        }
    }

    /**
     * Формирование контекстного промпта для ИИ
     */
    private fun buildContextualPrompt(
        location: GeoPoint,
        threats: List<Threat>
    ): String {
        return """
Ты - SafeGuardian AI, персональный ИИ-телохранитель пользователя.

ПРОФИЛЬ ПОЛЬЗОВАТЕЛЯ:
- Пол: ${userProfile.gender}
- Возраст: ${userProfile.age}
- Уровень тревожности: ${userProfile.anxietyLevel}
- Время суток: ${getCurrentTimeOfDay()}

ТЕКУЩАЯ СИТУАЦИЯ:
Местоположение: ${location.latitude}, ${location.longitude}
Время: ${formatTime(System.currentTimeMillis())}

ОБНАРУЖЕННЫЕ УГРОЗЫ:
${threats.joinToString("\n") { "- ${it.type}: ${it.description} (расстояние: ${it.distance}м)" }}

ЗАДАЧА:
Оцени уровень опасности и дай КОНКРЕТНЫЕ, ПРАКТИЧНЫЕ рекомендации пользователю.
Будь эмпатичным, но не паникуй.

Ответь ТОЛЬКО в формате JSON:
{
  "severity": "LOW|MEDIUM|HIGH|CRITICAL",
  "message": "Короткое сообщение пользователю (максимум 2 предложения)",
  "actions": ["действие1", "действие2", "действие3"],
  "reasoning": "Почему такая оценка"
}
        """.trimIndent()
    }

    /**
     * Идентификация угроз поблизости
     */
    private suspend fun identifyNearbyThreats(location: GeoPoint): List<Threat> = withContext(Dispatchers.IO) {

        val threats = mutableListOf<Threat>()

        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        if (hour >= 22 || hour < 6) {
            threats.add(Threat(
                type = ThreatType.POOR_LIGHTING,
                description = "Плохое освещение в ночное время",
                distance = 0.0,
                severity = 2
            ))
        }

        threats
    }

    /**
     * Критическое оповещение
     */
    private suspend fun triggerCriticalAlert(response: AIResponse) = withContext(Dispatchers.Main) {
        // Placeholder - требует Android context
    }

    /**
     * Проактивное предупреждение
     */
    private suspend fun sendProactiveWarning(response: AIResponse) = withContext(Dispatchers.Main) {
        alertHistory.add(AIAlert(
            timestamp = System.currentTimeMillis(),
            response = response,
            userAction = null
        ))
    }

    /**
     * Голосовое взаимодействие
     */
    suspend fun processVoiceCommand(command: String): String = withContext(Dispatchers.IO) {

        val prompt = """
Ты - SafeGuardian AI, голосовой помощник безопасности.

Пользователь сказал: "$command"

Дай КРАТКИЙ (максимум 2 предложения), ПОЛЕЗНЫЙ ответ.

Ответь простым текстом (не JSON):
        """.trimIndent()

        try {
            callClaudeAPI(prompt)
        } catch (e: Exception) {
            "Извините, не удалось обработать команду."
        }
    }

    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====

    private suspend fun callClaudeAPI(prompt: String): String = withContext(Dispatchers.IO) {

        val requestBody = JSONObject().apply {
            put("model", MODEL)
            put("max_tokens", 400)
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

    private fun parseAIResponse(apiResponse: String): AIResponse {
        return try {
            val json = JSONObject(apiResponse)
            AIResponse(
                severity = ThreatSeverity.valueOf(json.getString("severity")),
                message = json.getString("message"),
                actions = parseActions(json),
                reasoning = json.getString("reasoning")
            )
        } catch (e: Exception) {
            AIResponse(
                ThreatSeverity.MEDIUM,
                "Будьте осторожны",
                listOf("Оставайтесь на освещённых улицах"),
                "Общая рекомендация"
            )
        }
    }

    private fun parseActions(json: JSONObject): List<String> {
        val actions = mutableListOf<String>()
        val actionsArray = json.optJSONArray("actions")
        if (actionsArray != null) {
            for (i in 0 until actionsArray.length()) {
                actions.add(actionsArray.getString(i))
            }
        }
        return actions
    }

    private fun getCurrentTimeOfDay(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 6..11 -> "утро"
            in 12..17 -> "день"
            in 18..21 -> "вечер"
            else -> "ночь"
        }
    }

    private fun formatTime(timestamp: Long): String {
        return java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
            .format(java.util.Date(timestamp))
    }
}

// ===== МОДЕЛИ ДАННЫХ =====

data class UserProfile(
    val gender: String,
    val age: Int,
    val anxietyLevel: AnxietyLevel
)

enum class AnxietyLevel {
    LOW, MEDIUM, HIGH
}

data class AIResponse(
    val severity: ThreatSeverity,
    val message: String,
    val actions: List<String>,
    val reasoning: String
)

enum class ThreatSeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}

data class Threat(
    val type: ThreatType,
    val description: String,
    val distance: Double,
    val severity: Int
)

enum class ThreatType {
    HISTORICAL_INCIDENT,
    POOR_LIGHTING,
    LOW_CROWD,
    FAR_FROM_HELP,
    SUSPICIOUS_ACTIVITY
}

data class AIAlert(
    val timestamp: Long,
    val response: AIResponse,
    val userAction: String?
)

interface LocationTracker {
    fun getCurrentLocation(): GeoPoint
}

data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)