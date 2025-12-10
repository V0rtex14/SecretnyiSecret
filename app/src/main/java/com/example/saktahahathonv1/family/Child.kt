package com.example.saktahahathonv1.family

import org.osmdroid.util.GeoPoint

data class Child(
    val id: String,
    val name: String,
    val phone: String,
    val location: GeoPoint?,
    val safeZoneCenter: GeoPoint,
    val safeZoneRadius: Double, // в метрах
    val isInSafeZone: Boolean = true,
    val lastUpdate: Long = System.currentTimeMillis()
) {
    fun getStatusText(): String {
        return if (isInSafeZone) {
            "В безопасной зоне"
        } else {
            "Вне безопасной зоны!"
        }
    }

    fun getAvatar(): String {
        return name.firstOrNull()?.uppercase() ?: "?"
    }
}
