package com.example.saktahahathonv1.map

import com.example.saktahahathonv1.data.*
import org.osmdroid.util.GeoPoint
import kotlin.math.*

/**
 * Ядро расчёта риска для безопасной навигации
 * Учитывает: инциденты, жалобы, освещение, безопасные точки
 */
class RiskEngine(
    private val incidents: List<Incident>,
    private val complaints: List<Complaint>,
    private val safePlaces: List<SafePlace>,
    private val litSegments: List<LitSegment>
) {

    companion object {
        // Радиусы влияния (метры)
        const val INCIDENT_MAX_DISTANCE = 1200.0
        const val COMPLAINT_MAX_DISTANCE = 800.0
        const val SAFE_PLACE_CHECK_DISTANCE = 1000.0
        const val LIGHT_CHECK_DISTANCE = 500.0

        // Веса по типам инцидентов
        private val INCIDENT_TYPE_WEIGHTS = mapOf(
            "murder" to 5.0,
            "armed_robbery" to 3.5,
            "assault" to 3.0,
            "harassment" to 2.0,
            "robbery" to 2.5,
            "other" to 1.0
        )

        // Период полураспада для временного затухания (дни)
        const val TIME_HALFLIFE_DAYS = 90.0

        // Коэффициенты для расчёта score маршрута
        const val RISK_WEIGHT = 1.0
        const val DISTANCE_WEIGHT = 0.3

        // Коэффициент для жалоб от женщин
        const val FEMALE_COMPLAINT_MULTIPLIER = 1.8
    }

    /**
     * Основная функция: расчёт риска в конкретной точке
     */
    fun riskAtPoint(point: GeoPoint, currentTime: Long = System.currentTimeMillis()): Double {
        var totalRisk = 0.0

        // 1. Вклад официальных инцидентов
        totalRisk += calculateIncidentRisk(point, currentTime)

        // 2. Вклад жалоб пользователей
        totalRisk += calculateComplaintRisk(point, currentTime)

        // 3. Базовый контекстный риск (удалённость от безопасных мест и света)
        totalRisk += calculateContextRisk(point)

        // 4. Снижение риска от островков безопасности
        totalRisk -= calculateSafePlaceReduction(point)

        // 5. Корректировка на освещённость
        totalRisk = applyLightingFactor(point, totalRisk)

        // Нормализация: риск не может быть отрицательным
        return max(totalRisk, 0.0)
    }

    /**
     * Расчёт риска вдоль отрезка маршрута
     */
    fun riskAlongSegment(start: GeoPoint, end: GeoPoint, steps: Int = 25): Double {
        var totalRisk = 0.0

        for (i in 0 until steps) {
            val t = i.toDouble() / steps
            val lat = start.latitude + t * (end.latitude - start.latitude)
            val lon = start.longitude + t * (end.longitude - start.longitude)
            val point = GeoPoint(lat, lon)

            totalRisk += riskAtPoint(point)
        }

        return totalRisk / steps
    }

    /**
     * Оценка всего маршрута (список точек)
     */
    fun evaluateRoute(route: List<GeoPoint>): RouteEvaluation {
        if (route.size < 2) return RouteEvaluation(0.0, 0.0, 0.0, emptyList())

        var totalDistance = 0.0
        var totalRisk = 0.0
        val segmentRisks = mutableListOf<SegmentRisk>()

        for (i in 0 until route.size - 1) {
            val start = route[i]
            val end = route[i + 1]

            val segmentDist = distanceBetween(start, end)
            val segmentRisk = riskAlongSegment(start, end)

            totalDistance += segmentDist
            totalRisk += segmentRisk * segmentDist

            segmentRisks.add(SegmentRisk(start, end, segmentRisk, getRiskLevel(segmentRisk)))
        }

        val avgRisk = if (totalDistance > 0) totalRisk / totalDistance else 0.0
        val score = avgRisk * RISK_WEIGHT + (totalDistance / 1000) * DISTANCE_WEIGHT

        return RouteEvaluation(totalDistance, avgRisk, score, segmentRisks)
    }

    // ============= ПРИВАТНЫЕ МЕТОДЫ =============

    private fun calculateIncidentRisk(point: GeoPoint, currentTime: Long): Double {
        var risk = 0.0

        for (incident in incidents) {
            val distance = distanceBetween(point, GeoPoint(incident.lat, incident.lon))

            if (distance > INCIDENT_MAX_DISTANCE) continue

            // Базовый вес по типу
            val baseWeight = INCIDENT_TYPE_WEIGHTS[incident.type] ?: 1.0

            // Множитель по severity (1-5)
            val severityK = incident.severity / 3.0

            // Временное затухание (формула полураспада)
            val timeFactor = calculateTimeFactor(incident.datetime, currentTime)

            // Пространственное затухание
            val spatialFactor = exp(-distance / 400.0)

            risk += baseWeight * severityK * timeFactor * spatialFactor
        }

        return risk
    }

    private fun calculateComplaintRisk(point: GeoPoint, currentTime: Long): Double {
        var risk = 0.0

        for (complaint in complaints) {
            val distance = distanceBetween(point, GeoPoint(complaint.lat, complaint.lon))

            if (distance > COMPLAINT_MAX_DISTANCE) continue

            // Повышенный коэффициент для жалоб от женщин
            val femaleFactor = if (complaint.isFemale) FEMALE_COMPLAINT_MULTIPLIER else 1.0

            // Временное затухание
            val timeFactor = calculateTimeFactor(complaint.datetime, currentTime)

            // Более быстрое пространственное затухание для жалоб
            val spatialFactor = exp(-distance / 250.0)

            risk += complaint.weight * femaleFactor * timeFactor * spatialFactor
        }

        return risk
    }

    private fun calculateContextRisk(point: GeoPoint): Double {
        val distToSafe = findNearestSafePlace(point)
        val distToLight = findNearestLitSegment(point)

        return when {
            distToSafe > 800 && distToLight > 500 -> 0.6
            distToSafe > 500 && distToLight > 300 -> 0.4
            distToSafe > 300 || distToLight > 200 -> 0.25
            else -> 0.1
        }
    }

    private fun calculateSafePlaceReduction(point: GeoPoint): Double {
        var reduction = 0.0

        for (safePlace in safePlaces) {
            val distance = distanceBetween(point, GeoPoint(safePlace.lat, safePlace.lon))

            if (distance < safePlace.radius) {
                val decay = exp(-distance / (safePlace.radius / 2))
                reduction += safePlace.power * decay
            }
        }

        return reduction
    }

    private fun applyLightingFactor(point: GeoPoint, risk: Double): Double {
        val distToLight = findNearestLitSegment(point)

        return if (distToLight < 100) {
            // Чем ближе к свету, тем меньше риск
            val factor = 0.4 + 0.6 * (distToLight / 100.0)
            risk * factor
        } else {
            risk
        }
    }

    private fun calculateTimeFactor(datetimeStr: String, currentTime: Long): Double {
        val incidentTime = parseDateTime(datetimeStr)
        val daysDiff = (currentTime - incidentTime) / (1000.0 * 60 * 60 * 24)

        // Формула полураспада: 2^(-days / halflife)
        return 2.0.pow(-daysDiff / TIME_HALFLIFE_DAYS)
    }

    private fun findNearestSafePlace(point: GeoPoint): Double {
        return safePlaces.minOfOrNull {
            distanceBetween(point, GeoPoint(it.lat, it.lon))
        } ?: Double.MAX_VALUE
    }

    private fun findNearestLitSegment(point: GeoPoint): Double {
        return litSegments.minOfOrNull { segment ->
            distanceToSegment(point,
                GeoPoint(segment.startLat, segment.startLon),
                GeoPoint(segment.endLat, segment.endLon)
            )
        } ?: Double.MAX_VALUE
    }

    private fun distanceBetween(p1: GeoPoint, p2: GeoPoint): Double {
        val earthRadius = 6371000.0 // метры

        val lat1 = Math.toRadians(p1.latitude)
        val lat2 = Math.toRadians(p2.latitude)
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }

    private fun distanceToSegment(point: GeoPoint, segStart: GeoPoint, segEnd: GeoPoint): Double {
        val px = point.longitude
        val py = point.latitude
        val x1 = segStart.longitude
        val y1 = segStart.latitude
        val x2 = segEnd.longitude
        val y2 = segEnd.latitude

        val dx = x2 - x1
        val dy = y2 - y1

        if (dx == 0.0 && dy == 0.0) {
            return distanceBetween(point, segStart)
        }

        val t = max(0.0, min(1.0, ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)))
        val projX = x1 + t * dx
        val projY = y1 + t * dy

        return distanceBetween(point, GeoPoint(projY, projX))
    }

    private fun parseDateTime(dateStr: String): Long {
        return try {
            // Parse ISO datetime format: "2025-06-12T23:15:00"
            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            formatter.parse(dateStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            // If parsing fails, try without time component
            try {
                val dateFormatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                dateFormatter.parse(dateStr)?.time ?: System.currentTimeMillis()
            } catch (e2: Exception) {
                // Fallback to current time if all parsing fails
                System.currentTimeMillis()
            }
        }
    }

    private fun getRiskLevel(risk: Double): RiskLevel {
        return when {
            risk < 0.5 -> RiskLevel.SAFE
            risk < 1.5 -> RiskLevel.MEDIUM
            else -> RiskLevel.HIGH
        }
    }
}

// ============= МОДЕЛИ ДАННЫХ =============

data class Incident(
    val id: Long,
    val lat: Double,
    val lon: Double,
    val type: String,
    val severity: Int,
    val datetime: String,
    val source: String,
    val description: String? = null
)

data class Complaint(
    val id: Long,
    val lat: Double,
    val lon: Double,
    val weight: Double,
    val isFemale: Boolean,
    val datetime: String,
    val text: String? = null
)

data class SafePlace(
    val lat: Double,
    val lon: Double,
    val type: SafePlaceType,
    val power: Double,
    val radius: Double,
    val name: String? = null
)

enum class SafePlaceType {
    POLICE,    // УПСМ, отделение милиции
    HOSPITAL,  // Травмпункты, больницы
    SHOP24,    // Супермаркеты 24/7
    CAFE24     // Круглосуточные кофейни
}

data class LitSegment(
    val startLat: Double,
    val startLon: Double,
    val endLat: Double,
    val endLon: Double
)

data class RouteEvaluation(
    val totalDistance: Double,      // метры
    val averageRisk: Double,         // средний риск
    val score: Double,               // комбинированная оценка
    val segments: List<SegmentRisk>  // детализация по сегментам
)

data class SegmentRisk(
    val start: GeoPoint,
    val end: GeoPoint,
    val risk: Double,
    val level: RiskLevel
)

enum class RiskLevel {
    SAFE,    // Зелёный
    MEDIUM,  // Жёлтый
    HIGH     // Красный
}