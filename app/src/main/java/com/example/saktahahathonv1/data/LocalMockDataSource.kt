package com.example.saktahahathonv1.data

import android.content.Context
import com.example.saktahahathonv1.map.Complaint
import com.example.saktahahathonv1.map.CrowdedArea
import com.example.saktahahathonv1.map.Incident
import com.example.saktahahathonv1.map.LitSegment
import com.example.saktahahathonv1.map.SafePlace
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Локальный источник демо-данных из assets для карт и аналитики риска.
 */
class LocalMockDataSource(private val context: Context) {

    private val gson = Gson()
    private val dataManager = DataManager(context)

    private var cachedIncidents: List<Incident>? = null
    private var cachedComplaints: List<Complaint>? = null
    private var cachedSafePlaces: List<SafePlace>? = null
    private var cachedLitSegments: List<LitSegment>? = null
    private var cachedCrowdedAreas: List<CrowdedArea>? = null

    fun getIncidents(): List<Incident> {
        if (cachedIncidents == null) {
            cachedIncidents = dataManager.loadIncidents()
        }
        return cachedIncidents.orEmpty()
    }

    fun getComplaints(): List<Complaint> {
        if (cachedComplaints == null) {
            cachedComplaints = dataManager.loadComplaints()
        }
        return cachedComplaints.orEmpty()
    }

    fun getSafePlaces(): List<SafePlace> {
        if (cachedSafePlaces == null) {
            cachedSafePlaces = dataManager.loadSafePlaces()
        }
        return cachedSafePlaces.orEmpty()
    }

    fun getLitSegments(): List<LitSegment> {
        if (cachedLitSegments == null) {
            cachedLitSegments = dataManager.loadLitSegments()
        }
        return cachedLitSegments.orEmpty()
    }

    fun getCrowdedAreas(): List<CrowdedArea> {
        if (cachedCrowdedAreas == null) {
            cachedCrowdedAreas = loadCrowdedAreas()
        }
        return cachedCrowdedAreas.orEmpty()
    }

    private fun loadCrowdedAreas(): List<CrowdedArea> {
        return try {
            val json = context.assets.open("crowded_areas.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<CrowdedAreaJson>>() {}.type
            gson.fromJson<List<CrowdedAreaJson>>(json, type).map {
                CrowdedArea(
                    lat = it.lat,
                    lon = it.lon,
                    radius = it.radius,
                    name = it.name,
                    timeOfDay = it.timeOfDay
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

private data class CrowdedAreaJson(
    val lat: Double,
    val lon: Double,
    val radius: Double,
    val name: String,
    val timeOfDay: String = "all"
)
