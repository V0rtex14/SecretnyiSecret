package com.example.saktahahathonv1.map

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.example.saktahahathonv1.data.*
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

class MapViewModel : ViewModel() {

    // State
    private val _mapState = MutableLiveData<MapState>()
    val mapState: LiveData<MapState> = _mapState

    private val _routeState = MutableLiveData<RouteState>()
    val routeState: LiveData<RouteState> = _routeState

    // Data
    private val _incidents = MutableLiveData<List<Incident>>()
    val incidents: LiveData<List<Incident>> = _incidents

    private val _complaints = MutableLiveData<List<Complaint>>()
    val complaints: LiveData<List<Complaint>> = _complaints

    private val _safePlaces = MutableLiveData<List<SafePlace>>()
    val safePlaces: LiveData<List<SafePlace>> = _safePlaces

    private val _litSegments = MutableLiveData<List<LitSegment>>()
    val litSegments: LiveData<List<LitSegment>> = _litSegments

    private val _crowdedAreas = MutableLiveData<List<CrowdedArea>>()
    val crowdedAreas: LiveData<List<CrowdedArea>> = _crowdedAreas

    // Engines
    private var riskEngine: RiskEngine? = null
    private var routingEngine: SafeRoutingEngine? = null

    fun initialize(mapView: MapView) {
        loadAllData()
    }

    private fun loadAllData() {
        viewModelScope.launch {
            try {
                _mapState.value = MapState.Loading

                // Генерация демо-данных
                val centerLat = 42.8746
                val centerLon = 74.5698

                val incidentsList = DemoDataGenerator.generateDemoIncidents(centerLat, centerLon, 20)
                val complaintsList = DemoDataGenerator.generateDemoComplaints(centerLat, centerLon, 12)
                val safePlacesList = DemoDataGenerator.generateDemoSafePlaces(centerLat, centerLon)
                val litSegmentsList = DemoDataGenerator.generateExtendedLitSegments(centerLat, centerLon)
                val crowdedAreasList = DemoDataGenerator.generateCrowdedAreas(centerLat, centerLon)

                _incidents.value = incidentsList
                _complaints.value = complaintsList
                _safePlaces.value = safePlacesList
                _litSegments.value = litSegmentsList
                _crowdedAreas.value = crowdedAreasList

                // Инициализируем engines
                val newRiskEngine = RiskEngine(incidentsList, complaintsList, safePlacesList, litSegmentsList)
                riskEngine = newRiskEngine

                val roadManager = RoadManagerFactory.create("SafeWalk")
                routingEngine = SafeRoutingEngine(newRiskEngine, roadManager)

                _mapState.value = MapState.Ready

            } catch (e: Exception) {
                _mapState.value = MapState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun buildSafeRoute(start: GeoPoint, end: GeoPoint) {
        viewModelScope.launch {
            try {
                _routeState.value = RouteState.Loading

                val engine = routingEngine ?: run {
                    _routeState.value = RouteState.Error("Engine not initialized")
                    return@launch
                }

                // Получаем данные
                val litStreets = _litSegments.value ?: emptyList()
                val crowded = _crowdedAreas.value ?: emptyList()
                val safePlacesList = _safePlaces.value ?: emptyList()

                // Строим альтернативные маршруты
                val routes = engine.buildAlternativeRoutes(
                    start = start,
                    end = end,
                    litStreets = litStreets,
                    crowdedAreas = crowded,
                    safePlaces = safePlacesList
                )

                if (routes.isNotEmpty()) {
                    // Возвращаем все маршруты для выбора пользователем
                    _routeState.value = RouteState.MultipleRoutes(routes)
                } else {
                    _routeState.value = RouteState.Error("Could not build routes")
                }

            } catch (e: Exception) {
                _routeState.value = RouteState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun buildSOSRoute(currentLocation: GeoPoint) {
        viewModelScope.launch {
            try {
                _routeState.value = RouteState.Loading

                val engine = routingEngine ?: run {
                    _routeState.value = RouteState.Error("Engine not initialized")
                    return@launch
                }

                val safePlacesList = _safePlaces.value ?: emptyList()

                val route = engine.buildSOSRoute(currentLocation, safePlacesList)

                if (route != null) {
                    // Оборачиваем в RouteOption для единообразия
                    val litStreets = _litSegments.value ?: emptyList()
                    val crowded = _crowdedAreas.value ?: emptyList()

                    val evaluation = riskEngine?.evaluateRoute(route.points) ?: run {
                        _routeState.value = RouteState.Error("RiskEngine not initialized")
                        return@launch
                    }

                    val routeOption = RouteOption(
                        route = route,
                        evaluation = ExtendedRouteEvaluation(
                            baseEvaluation = evaluation,
                            adjustedRisk = evaluation.averageRisk,
                            totalScore = evaluation.averageRisk,
                            lightCoverage = 0.0,
                            crowdCoverage = 0.0,
                            roadQuality = "SOS Route"
                        ),
                        type = RouteType.DIRECT,
                        description = "SOS - К ближайшему безопасному месту"
                    )

                    _routeState.value = RouteState.SOSSuccess(routeOption)
                } else {
                    _routeState.value = RouteState.Error("No safe places nearby")
                }

            } catch (e: Exception) {
                _routeState.value = RouteState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun addComplaint(complaint: Complaint) {
        viewModelScope.launch {
            try {
                val currentComplaints = _complaints.value?.toMutableList() ?: mutableListOf()
                currentComplaints.add(complaint)
                _complaints.value = currentComplaints

                // Пересоздаём RiskEngine
                riskEngine = RiskEngine(
                    _incidents.value ?: emptyList(),
                    currentComplaints,
                    _safePlaces.value ?: emptyList(),
                    _litSegments.value ?: emptyList()
                )

                _mapState.value = MapState.ComplaintAdded

            } catch (e: Exception) {
                _mapState.value = MapState.Error(e.message ?: "Failed to add complaint")
            }
        }
    }

    fun getRiskZones(bounds: org.osmdroid.util.BoundingBox): List<RiskZone> {
        val engine = riskEngine ?: return emptyList()

        val zones = mutableListOf<RiskZone>()
        val step = 0.003 // ~300м

        var lat = bounds.latSouth
        while (lat <= bounds.latNorth) {
            var lon = bounds.lonWest
            while (lon <= bounds.lonEast) {
                val point = GeoPoint(lat, lon)
                val risk = engine.riskAtPoint(point)

                if (risk >= 0.8) {
                    zones.add(RiskZone(
                        center = point,
                        risk = risk,
                        radius = 120.0
                    ))
                }

                lon += step
            }
            lat += step
        }

        return zones
    }
}

// ===== STATES =====

sealed class MapState {
    object Loading : MapState()
    object Ready : MapState()
    object ComplaintAdded : MapState()
    data class Error(val message: String) : MapState()
}

sealed class RouteState {
    object Idle : RouteState()
    object Loading : RouteState()
    data class Success(val route: RouteOption) : RouteState()
    data class SOSSuccess(val route: RouteOption) : RouteState()
    data class MultipleRoutes(val routes: List<RouteOption>) : RouteState()
    data class Error(val message: String) : RouteState()
}

data class RiskZone(
    val center: GeoPoint,
    val risk: Double,
    val radius: Double
)