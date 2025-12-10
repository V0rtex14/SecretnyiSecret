package com.example.saktahahathonv1.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Импортируем модели данных из RiskEngine
import com.example.saktahahathonv1.map.Incident
import com.example.saktahahathonv1.map.Complaint
import com.example.saktahahathonv1.map.SafePlace
import com.example.saktahahathonv1.map.SafePlaceType
import com.example.saktahahathonv1.map.LitSegment

/**
 * Менеджер данных - загружает все данные из JSON файлов
 */
class DataManager(private val context: Context) {

    private val gson = Gson()

    /**
     * Загрузить инциденты из JSON
     */
    fun loadIncidents(): List<Incident> {
        return try {
            val json = context.assets.open("incidents.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<IncidentJson>>() {}.type
            val incidents: List<IncidentJson> = gson.fromJson(json, type)

            incidents.map {
                Incident(
                    id = it.id,
                    lat = it.lat,
                    lon = it.lon,
                    type = it.type,
                    severity = it.severity,
                    datetime = it.datetime,
                    source = it.source ?: "Unknown",
                    description = it.description
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Загрузить жалобы из JSON
     */
    fun loadComplaints(): List<Complaint> {
        return try {
            val json = context.assets.open("complaints.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<ComplaintJson>>() {}.type
            val complaints: List<ComplaintJson> = gson.fromJson(json, type)

            complaints.map {
                Complaint(
                    id = it.id,
                    lat = it.lat,
                    lon = it.lon,
                    weight = it.weight.toDouble(),
                    isFemale = it.isFemale,
                    datetime = it.datetime,
                    text = it.text
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Загрузить безопасные места из JSON
     */
    fun loadSafePlaces(): List<SafePlace> {
        return try {
            val json = context.assets.open("safe_places.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<SafePlaceJson>>() {}.type
            val safePlaces: List<SafePlaceJson> = gson.fromJson(json, type)

            safePlaces.map {
                SafePlace(
                    lat = it.lat,
                    lon = it.lon,
                    type = SafePlaceType.valueOf(it.type),
                    power = 1.0,
                    radius = it.radius.toDouble(),
                    name = it.name
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Загрузить освещённые сегменты из JSON
     */
    fun loadLitSegments(): List<LitSegment> {
        return try {
            val json = context.assets.open("lit_segments.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<LitSegmentJson>>() {}.type
            val segments: List<LitSegmentJson> = gson.fromJson(json, type)

            segments.map {
                LitSegment(
                    startLat = it.startLat,
                    startLon = it.startLon,
                    endLat = it.endLat,
                    endLon = it.endLon
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Сохранить жалобу в файл
     */
    fun saveComplaint(complaint: Complaint): Boolean {
        return try {
            val complaints = loadComplaints().toMutableList()
            complaints.add(complaint)

            val complaintJsons = complaints.map {
                ComplaintJson(
                    id = it.id,
                    lat = it.lat,
                    lon = it.lon,
                    weight = it.weight.toInt(),
                    isFemale = it.isFemale,
                    datetime = it.datetime,
                    text = it.text ?: ""
                )
            }

            val json = gson.toJson(complaintJsons)
            val file = java.io.File(context.filesDir, "complaints.json")
            file.writeText(json)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

// ===== JSON модели =====

data class IncidentJson(
    val id: Long,
    val lat: Double,
    val lon: Double,
    val type: String,
    val severity: Int,
    val datetime: String,
    val source: String? = null,
    val description: String? = null
)

data class ComplaintJson(
    val id: Long,
    val lat: Double,
    val lon: Double,
    val weight: Int,
    val isFemale: Boolean,
    val datetime: String,
    val text: String
)

data class SafePlaceJson(
    val id: Long,
    val lat: Double,
    val lon: Double,
    val type: String,
    val name: String?,
    val radius: Int
)

data class LitSegmentJson(
    val id: Long,
    val startLat: Double,
    val startLon: Double,
    val endLat: Double,
    val endLon: Double,
    val streetName: String,
    val lightingQuality: String
)
