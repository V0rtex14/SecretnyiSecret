package com.example.saktahahathonv1.tracking

import android.content.Context
import com.example.saktahahathonv1.family.Child
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.osmdroid.util.GeoPoint

/**
 * Поставщик локальных маршрутных данных для детей/семьи.
 */
class MockTrackingProvider(private val context: Context) {

    private val gson = Gson()

    fun loadTrackedChildren(): List<TrackedChild> {
        return try {
            val json = context.assets.open("family_routes.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<TrackedChildJson>>() {}.type
            gson.fromJson<List<TrackedChildJson>>(json, type).map { it.toDomain() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

data class TrackedChild(
    val id: String,
    val name: String,
    val relationship: String,
    val phone: String,
    val avatar: String,
    val age: Int,
    val currentLocation: GeoPoint,
    val lastUpdated: Long,
    val route: List<RouteCoordinate>,
    val safeZoneCenter: GeoPoint,
    val safeZoneRadius: Double
) {
    fun toFamilyChild(): Child = Child(
        id = id,
        name = name,
        phone = phone,
        location = currentLocation,
        safeZoneCenter = safeZoneCenter,
        safeZoneRadius = safeZoneRadius,
        isInSafeZone = true,
        lastUpdate = lastUpdated
    )
}

data class RouteCoordinate(
    val lat: Double,
    val lon: Double,
    val timestamp: Long
) {
    fun toGeoPoint() = GeoPoint(lat, lon)
}

private data class TrackedChildJson(
    val id: String,
    val name: String,
    val age: Int,
    val relationship: String,
    val avatar: String,
    val phone: String,
    val currentLocation: CoordinateJson,
    val lastUpdated: String,
    val safeZone: SafeZoneJson,
    val route: List<RouteJson>
) {
    fun toDomain(): TrackedChild {
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
        val lastUpdatedMillis = try {
            formatter.parse(lastUpdated)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }

        return TrackedChild(
            id = id,
            name = name,
            relationship = relationship,
            phone = phone,
            avatar = avatar,
            age = age,
            currentLocation = GeoPoint(currentLocation.lat, currentLocation.lon),
            lastUpdated = lastUpdatedMillis,
            route = route.mapNotNull { it.toDomain(formatter) },
            safeZoneCenter = GeoPoint(safeZone.center.lat, safeZone.center.lon),
            safeZoneRadius = safeZone.radius
        )
    }
}

private data class CoordinateJson(
    val lat: Double,
    val lon: Double
)

private data class SafeZoneJson(
    val center: CoordinateJson,
    val radius: Double
)

private data class RouteJson(
    val lat: Double,
    val lon: Double,
    val timestamp: String
) {
    fun toDomain(formatter: java.text.SimpleDateFormat): RouteCoordinate? {
        val millis = try {
            formatter.parse(timestamp)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
        return RouteCoordinate(lat, lon, millis)
    }
}
