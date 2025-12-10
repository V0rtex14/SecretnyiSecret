package com.example.saktahahathonv1.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.osmdroid.util.GeoPoint

/**
 * База известных адресов и локаций Бишкека для автодополнения
 */
object BishkekAddresses {

    data class KnownLocation(
        val name: String,
        val address: String,
        val lat: Double,
        val lon: Double,
        val category: String
    ) {
        fun getCategoryEnum(): LocationCategory {
            return try {
                LocationCategory.valueOf(category)
            } catch (e: Exception) {
                LocationCategory.LANDMARK
            }
        }
    }

    enum class LocationCategory {
        LANDMARK,      // Достопримечательности
        SHOPPING,      // Торговые центры
        TRANSPORT,     // Транспорт
        EDUCATION,     // Учебные заведения
        HOSPITAL,      // Больницы
        POLICE,        // Милиция
        STREET,        // Улицы
        GOVERNMENT     // Государственные учреждения
    }

    private var _knownLocations: List<KnownLocation>? = null

    /**
     * Загрузка адресов из JSON
     */
    fun loadAddresses(context: Context) {
        if (_knownLocations == null) {
            try {
                val json = context.assets.open("known_addresses.json")
                    .bufferedReader()
                    .use { it.readText() }

                val type = object : TypeToken<List<KnownLocation>>() {}.type
                _knownLocations = Gson().fromJson(json, type)
            } catch (e: Exception) {
                e.printStackTrace()
                _knownLocations = getDefaultLocations()
            }
        }
    }

    /**
     * Список известных локаций Бишкека
     */
    val KNOWN_LOCATIONS: List<KnownLocation>
        get() = _knownLocations ?: getDefaultLocations()

    /**
     * Дефолтные локации на случай ошибки загрузки
     */
    private fun getDefaultLocations() = listOf(
        KnownLocation("Площадь Ала-Тоо", "Площадь Ала-Тоо", 42.8746, 74.6122, "LANDMARK"),
        KnownLocation("ТЦ ЦУМ", "Чуй 155", 42.8763, 74.6074, "SHOPPING"),
        KnownLocation("КГТУ им. Раззакова", "Ч. Айтматова 66", 42.8586, 74.5842, "EDUCATION")
    )

    /**
     * Поиск адресов по запросу (для автодополнения)
     */
    fun searchAddresses(query: String): List<KnownLocation> {
        if (query.isBlank()) return emptyList()

        val lowerQuery = query.lowercase().trim()

        return KNOWN_LOCATIONS.filter { location ->
            location.name.lowercase().contains(lowerQuery) ||
            location.address.lowercase().contains(lowerQuery)
        }.take(10)
    }

    /**
     * Получить GeoPoint по имени локации
     */
    fun getLocationByName(name: String): KnownLocation? {
        return KNOWN_LOCATIONS.find {
            it.name.equals(name, ignoreCase = true) ||
            it.address.equals(name, ignoreCase = true)
        }
    }

    /**
     * Получить все адреса для категории
     */
    fun getLocationsByCategory(category: LocationCategory): List<KnownLocation> {
        return KNOWN_LOCATIONS.filter { it.getCategoryEnum() == category }
    }

    /**
     * Форматированный адрес для отображения
     */
    fun KnownLocation.toDisplayString(): String {
        return "$name - $address"
    }
}
