package com.example.saktahahathonv1.map


import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.util.GeoPoint
import kotlinx.coroutines.*
import com.example.saktahahathonv1.data.*
import com.example.saktahahathonv1.map.*
import kotlin.math.*

/**
 * Улучшенная система построения безопасных маршрутов с взвешенным графом
 */
class SafeRoutingEngine(
    private val riskEngine: RiskEngine,
    private val roadManager: OSRMRoadManager
) {

    companion object {
        const val RISK_WEIGHT = 100.0  // Высокий вес для опасности
        const val DISTANCE_WEIGHT = 1.0
        const val LIT_STREET_BONUS = -30.0  // Снижение стоимости для освещённых улиц
        const val CROWDED_AREA_BONUS = -20.0
        const val SAFE_PLACE_BONUS = -50.0
        const val MAX_DETOUR_RATIO = 1.5
        const val GRID_STEP = 0.001  // ~100 метров
    }

    /**
     * Построение нескольких альтернативных маршрутов с разными приоритетами
     */
    suspend fun buildAlternativeRoutes(
        start: GeoPoint,
        end: GeoPoint,
        litStreets: List<LitSegment>,
        crowdedAreas: List<CrowdedArea>,
        safePlaces: List<SafePlace>
    ): List<RouteOption> = withContext(Dispatchers.IO) {

        val routes = mutableListOf<RouteOption>()

        try {
            // 1. Получаем несколько альтернативных маршрутов от OSRM
            val osrmRoutes = buildMultipleRoutesOSRM(start, end)

            for ((index, routeData) in osrmRoutes.withIndex()) {
                val evaluation = evaluateRouteWithContext(routeData, litStreets, crowdedAreas)
                val description = when (index) {
                    0 -> "Кратчайший путь"
                    1 -> "Альтернативный маршрут"
                    else -> "Вариант ${index + 1}"
                }

                routes.add(RouteOption(
                    route = routeData,
                    evaluation = evaluation,
                    type = if (index == 0) RouteType.DIRECT else RouteType.SAFEST,
                    description = description
                ))
            }

            // 2. Маршрут через безопасное место (УПСМ/больницу)
            val directRoute = osrmRoutes.firstOrNull()
            if (directRoute != null) {
                val viaPoliceRoute = buildRouteViaSafePlace(start, end, directRoute, safePlaces, litStreets, crowdedAreas)
                if (viaPoliceRoute != null) {
                    val policeEval = evaluateRouteWithContext(viaPoliceRoute.route, litStreets, crowdedAreas)
                    routes.add(RouteOption(
                        route = viaPoliceRoute.route,
                        evaluation = policeEval,
                        type = RouteType.VIA_SAFE_PLACE,
                        description = "Через ${viaPoliceRoute.safePlaceName}",
                        viaSafePlace = true
                    ))
                }
            }

            // Сортировка по безопасности (лучший скор сверху)
            routes.sortedBy { it.evaluation.totalScore }

        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback: хотя бы один прямой маршрут
            val fallbackRoute = buildDirectRouteOSRM(start, end)
            if (fallbackRoute != null) {
                val fallbackEval = evaluateRouteWithContext(fallbackRoute, litStreets, crowdedAreas)
                listOf(RouteOption(
                    route = fallbackRoute,
                    evaluation = fallbackEval,
                    type = RouteType.DIRECT,
                    description = "Прямой маршрут"
                ))
            } else {
                emptyList()
            }
        }
    }

    /**
     * Получение нескольких альтернативных маршрутов от OSRM
     */
    private suspend fun buildMultipleRoutesOSRM(
        start: GeoPoint,
        end: GeoPoint
    ): List<RouteData> = withContext(Dispatchers.IO) {

        try {
            val waypoints = ArrayList<GeoPoint>().apply {
                add(start)
                add(end)
            }

            // OSRM может возвращать альтернативные маршруты
            val road = roadManager.getRoad(waypoints)

            val routes = mutableListOf<RouteData>()

            // Главный маршрут
            if (road.mStatus == Road.STATUS_OK && road.mRouteHigh.isNotEmpty()) {
                routes.add(RouteData(
                    points = road.mRouteHigh,
                    distance = road.mLength * 1000,
                    duration = road.mDuration * 60,
                    roadType = RoadType.DIRECT
                ))
            }

            // Пробуем получить альтернативные маршруты через промежуточные точки
            // Создаём несколько вариантов маршрутов с небольшими отклонениями
            val alternatives = generateAlternativeWaypoints(start, end)
            for (altWaypoints in alternatives) {
                try {
                    val altRoad = roadManager.getRoad(altWaypoints)
                    if (altRoad.mStatus == Road.STATUS_OK && altRoad.mRouteHigh.isNotEmpty()) {
                        // Проверяем, что это действительно другой маршрут
                        val isDifferent = routes.none { existing ->
                            routesAreSimilar(existing.points, altRoad.mRouteHigh)
                        }

                        if (isDifferent) {
                            routes.add(RouteData(
                                points = altRoad.mRouteHigh,
                                distance = altRoad.mLength * 1000,
                                duration = altRoad.mDuration * 60,
                                roadType = RoadType.MIXED
                            ))
                        }
                    }
                } catch (e: Exception) {
                    // Пропускаем неудачные альтернативы
                }
            }

            routes.take(3) // Максимум 3 маршрута

        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback: хотя бы один прямой маршрут
            val fallback = buildDirectRouteOSRM(start, end)
            if (fallback != null) listOf(fallback) else emptyList()
        }
    }

    /**
     * Генерация альтернативных путей с промежуточными точками
     */
    private fun generateAlternativeWaypoints(start: GeoPoint, end: GeoPoint): List<ArrayList<GeoPoint>> {
        val alternatives = mutableListOf<ArrayList<GeoPoint>>()

        // Вычисляем среднюю точку
        val midLat = (start.latitude + end.latitude) / 2
        val midLon = (start.longitude + end.longitude) / 2

        // Создаём отклонения влево и вправо от прямой линии
        val perpAngle = Math.atan2(end.latitude - start.latitude, end.longitude - start.longitude) + Math.PI / 2
        val offset = 0.005 // ~500 метров

        // Альтернатива 1: отклонение влево
        val leftMid = GeoPoint(
            midLat + offset * Math.sin(perpAngle),
            midLon + offset * Math.cos(perpAngle)
        )
        alternatives.add(ArrayList<GeoPoint>().apply {
            add(start)
            add(leftMid)
            add(end)
        })

        // Альтернатива 2: отклонение вправо
        val rightMid = GeoPoint(
            midLat - offset * Math.sin(perpAngle),
            midLon - offset * Math.cos(perpAngle)
        )
        alternatives.add(ArrayList<GeoPoint>().apply {
            add(start)
            add(rightMid)
            add(end)
        })

        return alternatives
    }

    /**
     * Проверка, похожи ли два маршрута
     */
    private fun routesAreSimilar(route1: List<GeoPoint>, route2: List<GeoPoint>): Boolean {
        if (route1.size != route2.size) return false

        var matchingPoints = 0
        val threshold = 50.0 // 50 метров

        for (i in route1.indices) {
            if (distanceBetween(route1[i], route2[i]) < threshold) {
                matchingPoints++
            }
        }

        return (matchingPoints.toDouble() / route1.size) > 0.8 // Более 80% совпадающих точек
    }


    /**
     * Прямой маршрут от OSRM (для сравнения)
     */
    suspend fun buildDirectRouteOSRM(start: GeoPoint, end: GeoPoint): RouteData? =
        withContext(Dispatchers.IO) {
            try {
                val waypoints = ArrayList<GeoPoint>().apply {
                    add(start)
                    add(end)
                }
                val road = roadManager.getRoad(waypoints)

                if (road.mStatus == Road.STATUS_OK && road.mRouteHigh.isNotEmpty()) {
                    RouteData(
                        points = road.mRouteHigh,
                        distance = road.mLength * 1000,
                        duration = road.mDuration * 60,
                        roadType = RoadType.DIRECT
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }

    /**
     * SOS маршрут до ближайшего безопасного места
     */
    suspend fun buildSOSRoute(
        currentLocation: GeoPoint,
        safePlaces: List<SafePlace>
    ): RouteData? = withContext(Dispatchers.IO) {

        if (safePlaces.isEmpty()) return@withContext null

        val nearest = safePlaces.minByOrNull { sp ->
            distanceBetween(currentLocation, GeoPoint(sp.lat, sp.lon))
        } ?: return@withContext null

        val destination = GeoPoint(nearest.lat, nearest.lon)
        return@withContext buildDirectRouteOSRM(currentLocation, destination)
    }

    /**
     * Маршрут через безопасное место
     */
    private suspend fun buildRouteViaSafePlace(
        start: GeoPoint,
        end: GeoPoint,
        directRoute: RouteData?,
        safePlaces: List<SafePlace>,
        litStreets: List<LitSegment>,
        crowdedAreas: List<CrowdedArea>
    ): RouteViaSafePlace? = withContext(Dispatchers.IO) {

        if (directRoute == null) return@withContext null

        val relevantSafePlaces = safePlaces.filter { sp ->
            sp.type == SafePlaceType.POLICE || sp.type == SafePlaceType.HOSPITAL
        }

        for (sp in relevantSafePlaces) {
            val spPoint = GeoPoint(sp.lat, sp.lon)
            val detourDist = distanceBetween(start, spPoint) + distanceBetween(spPoint, end)

            if (detourDist <= directRoute.distance * MAX_DETOUR_RATIO) {
                // Строим два маршрута: до безопасного места и от него
                val routeToSP = buildDirectRouteOSRM(start, spPoint)
                val routeFromSP = buildDirectRouteOSRM(spPoint, end)

                if (routeToSP != null && routeFromSP != null) {
                    val combined = RouteData(
                        points = routeToSP.points + routeFromSP.points,
                        distance = routeToSP.distance + routeFromSP.distance,
                        duration = routeToSP.duration + routeFromSP.duration,
                        roadType = RoadType.VIA_SAFE
                    )

                    return@withContext RouteViaSafePlace(
                        route = combined,
                        safePlaceName = sp.name ?: sp.type.name,
                        safePlaceType = sp.type
                    )
                }
            }
        }
        null
    }

    /**
     * Оценка маршрута с учётом контекста
     */
    private fun evaluateRouteWithContext(
        route: RouteData,
        litStreets: List<LitSegment>,
        crowdedAreas: List<CrowdedArea>
    ): ExtendedRouteEvaluation {

        val baseEval = riskEngine.evaluateRoute(route.points)

        var lightBonus = 0.0
        var lightCoverage = 0.0

        for (i in 0 until route.points.size - 1) {
            val segment = Pair(route.points[i], route.points[i + 1])

            for (litSeg in litStreets) {
                if (segmentIntersectsLitStreet(segment, litSeg)) {
                    lightBonus += 0.2
                    lightCoverage += 1.0
                    break
                }
            }
        }

        lightCoverage = (lightCoverage / maxOf(1, route.points.size - 1)) * 100

        var crowdBonus = 0.0
        var crowdCoverage = 0.0

        for (point in route.points) {
            for (area in crowdedAreas) {
                if (distanceBetween(point, GeoPoint(area.lat, area.lon)) < area.radius) {
                    crowdBonus += 0.15
                    crowdCoverage += 1.0
                    break
                }
            }
        }

        crowdCoverage = (crowdCoverage / maxOf(1, route.points.size)) * 100

        val roadBonus = when (route.roadType) {
            RoadType.MAJOR_LIT -> 0.5
            RoadType.VIA_SAFE -> 0.4
            RoadType.MIXED -> 0.1
            RoadType.MINOR -> -0.2
            RoadType.DIRECT -> 0.0
        }

        val adjustedRisk = maxOf(0.0, baseEval.averageRisk - lightBonus - crowdBonus - roadBonus)
        val totalScore = adjustedRisk * RISK_WEIGHT + (route.distance / 1000) * DISTANCE_WEIGHT

        return ExtendedRouteEvaluation(
            baseEvaluation = baseEval,
            adjustedRisk = adjustedRisk,
            totalScore = totalScore,
            lightCoverage = lightCoverage,
            crowdCoverage = crowdCoverage,
            roadQuality = getRoadQualityText(route.roadType)
        )
    }

    private fun segmentIntersectsLitStreet(
        segment: Pair<GeoPoint, GeoPoint>,
        litStreet: LitSegment
    ): Boolean {
        val litStart = GeoPoint(litStreet.startLat, litStreet.startLon)
        val litEnd = GeoPoint(litStreet.endLat, litStreet.endLon)

        val dist1 = distanceToSegment(segment.first, litStart, litEnd)
        val dist2 = distanceToSegment(segment.second, litStart, litEnd)

        return dist1 < 50.0 || dist2 < 50.0
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

        if (dx == 0.0 && dy == 0.0) return distanceBetween(point, segStart)

        val t = maxOf(0.0, minOf(1.0, ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)))
        val projX = x1 + t * dx
        val projY = y1 + t * dy

        return distanceBetween(point, GeoPoint(projY, projX))
    }

    private fun distanceBetween(p1: GeoPoint, p2: GeoPoint): Double {
        val R = 6371000.0
        val lat1 = Math.toRadians(p1.latitude)
        val lat2 = Math.toRadians(p2.latitude)
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return R * c
    }

    private fun getRoadQualityText(roadType: RoadType): String {
        return when (roadType) {
            RoadType.MAJOR_LIT -> "Главная освещённая улица"
            RoadType.VIA_SAFE -> "Через безопасное место"
            RoadType.MIXED -> "Смешанный тип дорог"
            RoadType.MINOR -> "Второстепенные улицы"
            RoadType.DIRECT -> "Прямой маршрут"
        }
    }
}

// ===== МОДЕЛИ =====

data class RouteData(
    val points: List<GeoPoint>,
    val distance: Double,
    val duration: Double,
    val roadType: RoadType
)

data class RouteOption(
    val route: RouteData,
    val evaluation: ExtendedRouteEvaluation,
    val type: RouteType,
    val description: String,
    val viaSafePlace: Boolean = false
)

data class ExtendedRouteEvaluation(
    val baseEvaluation: RouteEvaluation,
    val adjustedRisk: Double,
    val totalScore: Double,
    val lightCoverage: Double,
    val crowdCoverage: Double,
    val roadQuality: String
)

data class RouteViaSafePlace(
    val route: RouteData,
    val safePlaceName: String,
    val safePlaceType: SafePlaceType
)

data class SafeRouteResult(
    val route: RouteData,
    val evaluation: ExtendedRouteEvaluation,
    val safePointVisited: SafePlace?,
    val reason: String
)

data class CrowdedArea(
    val lat: Double,
    val lon: Double,
    val radius: Double,
    val name: String,
    val timeOfDay: String = "all"
)

enum class RouteType {
    DIRECT,
    SAFEST,
    VIA_SAFE_PLACE
}

enum class RoadType {
    DIRECT,
    MAJOR_LIT,
    VIA_SAFE,
    MIXED,
    MINOR
}

object RoadManagerFactory {
    fun create(userAgent: String): OSRMRoadManager {
        return OSRMRoadManager(null, userAgent)
    }
}

