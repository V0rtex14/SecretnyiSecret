package com.example.saktahahathonv1.history

import org.osmdroid.util.GeoPoint

data class RouteHistory(
    val id: String,
    val userId: String,
    val fromAddress: String,
    val toAddress: String,
    val fromLocation: GeoPoint,
    val toLocation: GeoPoint,
    val distance: Double,
    val duration: Double,
    val timestamp: Long,
    val routeType: String,
    val safetyScore: Double,
    val isFavorite: Boolean = false
)

data class RouteStats(
    val totalRoutes: Int,
    val totalDistance: Double,
    val totalDuration: Double,
    val averageSafetyScore: Double,
    val mostUsedFrom: String,
    val mostUsedTo: String
)
