package com.example.saktahahathonv1.data

import com.example.saktahahathonv1.data.*
import android.content.Context

import com.example.saktahahathonv1.map.Complaint
import com.example.saktahahathonv1.map.CrowdedArea
import com.example.saktahahathonv1.map.Incident
import com.example.saktahahathonv1.map.LitSegment
import com.example.saktahahathonv1.map.SafePlace
import com.example.saktahahathonv1.map.SafePlaceType

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken




/**
 * Утилита для загрузки данных из JSON файлов в assets
 */
class DataLoader(private val context: Context) {

    private val gson = Gson()

    /**
     * Загрузка инцидентов из assets/incidents.json
     */
    fun loadIncidents(): List<Incident> {
        return try {
            val json = loadJsonFromAssets("incidents.json")
            val type = object : TypeToken<List<Incident>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Загрузка жалоб из assets/complaints.json
     */
    fun loadComplaints(): List<Complaint> {
        return try {
            val json = loadJsonFromAssets("complaints.json")
            val type = object : TypeToken<List<Complaint>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Загрузка безопасных мест из assets/safe_places.json
     */
    fun loadSafePlaces(): List<SafePlace> {
        return try {
            val json = loadJsonFromAssets("safe_places.json")
            val type = object : TypeToken<List<SafePlace>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Загрузка освещённых сегментов из assets/lit_segments.json
     */
    fun loadLitSegments(): List<LitSegment> {
        return try {
            val json = loadJsonFromAssets("lit_segments.json")
            val type = object : TypeToken<List<LitSegment>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Сохранение жалобы (в реальности отправлять на сервер)
     */
    fun saveComplaint(complaint: Complaint): Boolean {
        return try {
            // TODO: Отправка на сервер через API
            // Retrofit POST /complaints

            // Временно: сохранение в SharedPreferences
            val prefs = context.getSharedPreferences("complaints", Context.MODE_PRIVATE)
            val existing = prefs.getString("data", "[]")
            val list = gson.fromJson<MutableList<Complaint>>(
                existing,
                object : TypeToken<MutableList<Complaint>>() {}.type
            )
            list.add(complaint)

            prefs.edit()
                .putString("data", gson.toJson(list))
                .apply()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun loadJsonFromAssets(fileName: String): String {
        return context.assets.open(fileName).bufferedReader().use { it.readText() }
    }
}

/**
 * Генератор демо-данных для тестирования
 */
object DemoDataGenerator {

    /**
     * Генерация демо-инцидентов для города
     */
    fun generateDemoIncidents(centerLat: Double, centerLon: Double, count: Int = 20): List<Incident> {
        val types = listOf("robbery", "assault", "harassment", "other")
        val incidents = mutableListOf<Incident>()

        repeat(count) { i ->
            incidents.add(
                Incident(
                    id = i.toLong(),
                    lat = centerLat + (Math.random() - 0.5) * 0.02,
                    lon = centerLon + (Math.random() - 0.5) * 0.02,
                    type = types.random(),
                    severity = (1..5).random(),
                    datetime = generateRandomDate(),
                    source = "demo",
                    description = "Демо-инцидент #$i"
                )
            )
        }

        return incidents
    }

    /**
     * Генерация демо-жалоб
     */
    fun generateDemoComplaints(centerLat: Double, centerLon: Double, count: Int = 15): List<Complaint> {
        val complaints = mutableListOf<Complaint>()

        repeat(count) { i ->
            complaints.add(
                Complaint(
                    id = i.toLong(),
                    lat = centerLat + (Math.random() - 0.5) * 0.02,
                    lon = centerLon + (Math.random() - 0.5) * 0.02,
                    weight = 1.0 + Math.random() * 4.0,
                    isFemale = Math.random() > 0.5,
                    datetime = generateRandomDate(),
                    text = "Жалоба пользователя #$i"
                )
            )
        }

        return complaints
    }

    /**
     * Генерация безопасных мест
     */
    fun generateDemoSafePlaces(centerLat: Double, centerLon: Double): List<SafePlace> {
        return listOf(
            SafePlace(
                lat = centerLat + 0.003,
                lon = centerLon + 0.002,
                type = SafePlaceType.POLICE,
                power = 2.5,
                radius = 300.0,
                name = "УПСМ №1"
            ),
            SafePlace(
                lat = centerLat - 0.002,
                lon = centerLon + 0.004,
                type = SafePlaceType.HOSPITAL,
                power = 2.0,
                radius = 250.0,
                name = "Больница скорой помощи"
            ),
            SafePlace(
                lat = centerLat + 0.001,
                lon = centerLon - 0.003,
                type = SafePlaceType.SHOP24,
                power = 1.0,
                radius = 150.0,
                name = "Супермаркет 24/7"
            ),
            SafePlace(
                lat = centerLat - 0.001,
                lon = centerLon - 0.001,
                type = SafePlaceType.CAFE24,
                power = 0.8,
                radius = 100.0,
                name = "Coffee House"
            )
        )
    }

    /**
     * Генерация освещённых сегментов (основные улицы)
     */
    fun generateDemoLitSegments(centerLat: Double, centerLon: Double): List<LitSegment> {
        return listOf(
            LitSegment(centerLat - 0.005, centerLon - 0.005, centerLat + 0.005, centerLon - 0.005),
            LitSegment(centerLat + 0.005, centerLon - 0.005, centerLat + 0.005, centerLon + 0.005),
            LitSegment(centerLat + 0.005, centerLon + 0.005, centerLat - 0.005, centerLon + 0.005),
            LitSegment(centerLat - 0.005, centerLon + 0.005, centerLat - 0.005, centerLon - 0.005),
            LitSegment(centerLat, centerLon - 0.005, centerLat, centerLon + 0.005),
            LitSegment(centerLat - 0.005, centerLon, centerLat + 0.005, centerLon)
        )
    }

    /**
     * Генерация людных мест (торговые центры, площади, главные улицы)
     */
    fun generateCrowdedAreas(centerLat: Double, centerLon: Double): List<CrowdedArea> {
        return listOf(
            // Главные площади и торговые центры
            CrowdedArea(centerLat + 0.002, centerLon + 0.001, 200.0, "ТЦ Ала-Арча", "all"),
            CrowdedArea(centerLat - 0.001, centerLon + 0.003, 250.0, "ТЦ Bishkek Park", "all"),
            CrowdedArea(centerLat + 0.004, centerLon - 0.002, 180.0, "Ошский базар", "day"),
            CrowdedArea(centerLat, centerLon, 300.0, "Площадь Ала-Тоо", "all"),
            CrowdedArea(centerLat + 0.003, centerLon + 0.004, 150.0, "Дордой Плаза", "day"),
            CrowdedArea(centerLat - 0.003, centerLon - 0.001, 200.0, "ЦУМ", "all"),

            // Главные улицы (проспекты)
            CrowdedArea(centerLat + 0.001, centerLon + 0.002, 100.0, "Проспект Чуй", "all"),
            CrowdedArea(centerLat - 0.002, centerLon + 0.001, 100.0, "Проспект Манаса", "all"),
            CrowdedArea(centerLat + 0.002, centerLon - 0.003, 100.0, "Улица Киевская", "all")
        )
    }

    /**
     * Генерация освещённых улиц (расширенная версия)
     */
    fun generateExtendedLitSegments(centerLat: Double, centerLon: Double): List<LitSegment> {
        val segments = mutableListOf<LitSegment>()

        // Главные проспекты (хорошее освещение)
        segments.addAll(listOf(
            // Проспект Чуй (центральная ось)
            LitSegment(centerLat - 0.01, centerLon - 0.01, centerLat + 0.01, centerLon - 0.01),
            LitSegment(centerLat + 0.01, centerLon - 0.01, centerLat + 0.01, centerLon + 0.01),

            // Проспект Манаса
            LitSegment(centerLat - 0.01, centerLon + 0.01, centerLat + 0.01, centerLon + 0.01),

            // Проспект Мира
            LitSegment(centerLat, centerLon - 0.01, centerLat, centerLon + 0.01),

            // Улица Киевская
            LitSegment(centerLat - 0.005, centerLon - 0.005, centerLat + 0.005, centerLon - 0.005),

            // Улица Московская
            LitSegment(centerLat - 0.005, centerLon + 0.005, centerLat + 0.005, centerLon + 0.005),

            // Поперечные улицы
            LitSegment(centerLat - 0.008, centerLon - 0.008, centerLat - 0.008, centerLon + 0.008),
            LitSegment(centerLat - 0.004, centerLon - 0.008, centerLat - 0.004, centerLon + 0.008),
            LitSegment(centerLat + 0.004, centerLon - 0.008, centerLat + 0.004, centerLon + 0.008),
            LitSegment(centerLat + 0.008, centerLon - 0.008, centerLat + 0.008, centerLon + 0.008)
        ))

        return segments
    }

    /**
     * Генерация случайной даты для демо-данных
     */
    private fun generateRandomDate(): String {
        val daysAgo = (0..90).random()
        val hoursAgo = (0..23).random()
        val month = (10..12).random()
        val day = (1..28).random()

        return "2025-$month-${day.toString().padStart(2, '0')}T${hoursAgo.toString().padStart(2, '0')}:00:00"
    }
}

/**
 * Repository для работы с данными
 */
class SafeWalkRepository(private val dataLoader: DataLoader) {

    private var cachedIncidents: List<Incident>? = null
    private var cachedComplaints: List<Complaint>? = null
    private var cachedSafePlaces: List<SafePlace>? = null
    private var cachedLitSegments: List<LitSegment>? = null

    suspend fun getIncidents(forceRefresh: Boolean = false): List<Incident> {
        if (cachedIncidents == null || forceRefresh) {
            cachedIncidents = dataLoader.loadIncidents()
        }
        return cachedIncidents ?: emptyList()
    }

    suspend fun getComplaints(forceRefresh: Boolean = false): List<Complaint> {
        if (cachedComplaints == null || forceRefresh) {
            cachedComplaints = dataLoader.loadComplaints()
        }
        return cachedComplaints ?: emptyList()
    }

    suspend fun getSafePlaces(forceRefresh: Boolean = false): List<SafePlace> {
        if (cachedSafePlaces == null || forceRefresh) {
            cachedSafePlaces = dataLoader.loadSafePlaces()
        }
        return cachedSafePlaces ?: emptyList()
    }

    suspend fun getLitSegments(forceRefresh: Boolean = false): List<LitSegment> {
        if (cachedLitSegments == null || forceRefresh) {
            cachedLitSegments = dataLoader.loadLitSegments()
        }
        return cachedLitSegments ?: emptyList()
    }

    suspend fun addComplaint(complaint: Complaint): Boolean {
        return dataLoader.saveComplaint(complaint).also { success ->
            if (success) {
                // Обновить кеш
                cachedComplaints = null
            }
        }
    }
}