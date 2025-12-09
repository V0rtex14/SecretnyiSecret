package com.example.saktahahathonv1.map


import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.util.GeoPoint
import kotlinx.coroutines.*
import com.example.saktahahathonv1.data.*
import com.example.saktahahathonv1.map.*

/**
 * Улучшенная система построения безопасных маршрутов
 */
class SafeRoutingEngine(
    private val riskEngine: RiskEngine,
    private val roadManager: OSRMRoadManager
) {

    companion object {
        const val RISK_WEIGHT = 0.6
        const val DISTANCE_WEIGHT = 0.3
        const val MAJOR_ROAD_BONUS = 0.1
        const val MAX_DETOUR_RATIO = 1.5
    }

    suspend fun buildAlternativeRoutes(
        start: GeoPoint,
        end: GeoPoint,
        litStreets: List<LitSegment>,
        crowdedAreas: List<CrowdedArea>,
        safePlaces: List<SafePlace>
    ): List<RouteOption> = withContext(Dispatchers.IO) {

        val routes = mutableListOf<RouteOption>()

        val directRoute = buildDirectRoute(start, end)
        if (directRoute != null) {
            val directEval = evaluateRouteWithContext(directRoute, litStreets, crowdedAreas)
            routes.add(RouteOption(
                route = directRoute,
                evaluation = directEval,
                type = RouteType.DIRECT,
                description = "Кратчайший путь"
            ))
        }

        val safeRoute = buildSafeRoute(start, end, litStreets, crowdedAreas)
        if (safeRoute != null) {
            val safeEval = evaluateRouteWithContext(safeRoute, litStreets, crowdedAreas)
            routes.add(RouteOption(
                route = safeRoute,
                evaluation = safeEval,
                type = RouteType.SAFEST,
                description = "Максимально безопасный"
            ))
        }

        val viaPoliceRoute = buildRouteViaSafePlace(start, end, directRoute, safePlaces)
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

        routes.sortedBy { it.evaluation.totalScore }
    }

    suspend fun buildDirectRoute(start: GeoPoint, end: GeoPoint): RouteData? =
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

    suspend fun buildSafeRoute(
        start: GeoPoint,
        end: GeoPoint,
        litStreets: List<LitSegment>,
        crowdedAreas: List<CrowdedArea>
    ): RouteData? = withContext(Dispatchers.IO) {

        val waypoints = findSafeWaypoints(start, end, litStreets, crowdedAreas)

        if (waypoints.isEmpty()) {
            return@withContext buildDirectRoute(start, end)
        }

        try {
            val allPoints = ArrayList<GeoPoint>().apply {
                add(start)
                addAll(waypoints)
                add(end)
            }
            val road = roadManager.getRoad(allPoints)

            if (road.mStatus == Road.STATUS_OK && road.mRouteHigh.isNotEmpty()) {
                RouteData(
                    points = road.mRouteHigh,
                    distance = road.mLength * 1000,
                    duration = road.mDuration * 60,
                    roadType = RoadType.MAJOR_LIT
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun buildSOSRoute(
        currentLocation: GeoPoint,
        safePlaces: List<SafePlace>
    ): RouteData? = withContext(Dispatchers.IO) {

        if (safePlaces.isEmpty()) return@withContext null

        val nearest = safePlaces.minByOrNull { sp ->
            distanceBetween(currentLocation, GeoPoint(sp.lat, sp.lon))
        } ?: return@withContext null

        val destination = GeoPoint(nearest.lat, nearest.lon)
        return@withContext buildDirectRoute(currentLocation, destination)
    }

    private fun findSafeWaypoints(
        start: GeoPoint,
        end: GeoPoint,
        litStreets: List<LitSegment>,
        crowdedAreas: List<CrowdedArea>
    ): List<GeoPoint> {

        val waypoints = mutableListOf<GeoPoint>()
        val totalDist = distanceBetween(start, end)

        for (segment in litStreets) {
            val segMid = GeoPoint(
                (segment.startLat + segment.endLat) / 2,
                (segment.startLon + segment.endLon) / 2
            )

            val distFromStart = distanceBetween(start, segMid)
            val distToEnd = distanceBetween(segMid, end)

            if (distFromStart < totalDist * 0.8 &&
                distToEnd < totalDist * 0.8 &&
                distFromStart + distToEnd < totalDist * 1.3) {
                waypoints.add(segMid)
            }
        }

        return waypoints.take(2)
    }

    private suspend fun buildRouteViaSafePlace(
        start: GeoPoint,
        end: GeoPoint,
        directRoute: RouteData?,
        safePlaces: List<SafePlace>
    ): RouteViaSafePlace? = withContext(Dispatchers.IO) {

        if (directRoute == null) return@withContext null

        val relevantSafePlaces = safePlaces.filter { sp ->
            sp.type == SafePlaceType.POLICE || sp.type == SafePlaceType.HOSPITAL
        }

        for (sp in relevantSafePlaces) {
            val spPoint = GeoPoint(sp.lat, sp.lon)
            val detourDist = distanceBetween(start, spPoint) + distanceBetween(spPoint, end)

            if (detourDist <= directRoute.distance * MAX_DETOUR_RATIO) {
                val routeToSP = buildDirectRoute(start, spPoint)
                val routeFromSP = buildDirectRoute(spPoint, end)

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

        lightCoverage = (lightCoverage / (route.points.size - 1)) * 100

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

        crowdCoverage = (crowdCoverage / route.points.size) * 100

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

// ===== МОДЕЛИ (только ОДИН раз!) =====

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