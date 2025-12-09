package com.example.saktahahathonv1

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.*
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import com.example.saktahahathonv1.map.*
import com.example.saktahahathonv1.data.*
import kotlin.math.*

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var btnSos: FloatingActionButton
    private lateinit var btnMarkDanger: MaterialButton
    private lateinit var btnRoute: MaterialButton

    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var routeLine: Polyline? = null

    // Engines
    private lateinit var riskEngine: RiskEngine
    private lateinit var roadManager: OSRMRoadManager

    // Данные
    private val incidents = mutableListOf<Incident>()
    private val complaints = mutableListOf<Complaint>()
    private val safePlaces = mutableListOf<SafePlace>()
    private val litSegments = mutableListOf<LitSegment>()

    // Overlays
    private val incidentMarkers = mutableListOf<Marker>()
    private val complaintMarkers = mutableListOf<Marker>()
    private val safeZoneOverlays = mutableListOf<Polygon>()

    // Для выбора точки жалобы
    private var isSelectingComplaintLocation = false
    private var complaintSelectionMarker: Marker? = null

    private val LOCATION_PERMISSION_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Configuration.getInstance().load(
            this,
            PreferenceManager.getDefaultSharedPreferences(this)
        )
        Configuration.getInstance().userAgentValue = packageName

        mapView = findViewById(R.id.mapView)
        btnSos = findViewById(R.id.btnSos)
        btnMarkDanger = findViewById(R.id.btnMarkDanger)
        btnRoute = findViewById(R.id.btnRoute)

        setupMap()
        setupUI()
        loadDataAndInitEngines()
        checkPermissions()
    }

    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        val bishkek = GeoPoint(42.8746, 74.5698)
        mapView.controller.setZoom(14.0)
        mapView.controller.setCenter(bishkek)

        // Overlay для выбора точки (клик по карте)
        val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                if (p != null && isSelectingComplaintLocation) {
                    selectComplaintLocation(p)
                    return true
                }
                return false
            }

            override fun longPressHelper(p: GeoPoint?): Boolean = false
        })
        mapView.overlays.add(eventsOverlay)

        myLocationOverlay = MyLocationNewOverlay(
            GpsMyLocationProvider(this), mapView
        ).apply {
            enableMyLocation()
            enableFollowLocation()
        }
        mapView.overlays.add(myLocationOverlay)
    }

    private fun setupUI() {
        // SOS - к ближайшему безопасному месту
        btnSos.setOnClickListener {
            buildSOSRoute()
        }

        // Жалоба - выбрать место на карте
        btnMarkDanger.setOnClickListener {
            startComplaintSelection()
        }

        // Маршрут - диалог с адресами
        btnRoute.setOnClickListener {
            showRouteDialog()
        }
    }

    private fun loadDataAndInitEngines() {
        lifecycleScope.launch {
            try {
                val centerLat = 42.8746
                val centerLon = 74.5698

                incidents.addAll(DemoDataGenerator.generateDemoIncidents(centerLat, centerLon, 20))
                complaints.addAll(DemoDataGenerator.generateDemoComplaints(centerLat, centerLon, 12))
                safePlaces.addAll(DemoDataGenerator.generateDemoSafePlaces(centerLat, centerLon))
                litSegments.addAll(DemoDataGenerator.generateDemoLitSegments(centerLat, centerLon))

                riskEngine = RiskEngine(incidents, complaints, safePlaces, litSegments)
                roadManager = OSRMRoadManager(this@MainActivity, packageName)

                visualizeData()

                Toast.makeText(
                    this@MainActivity,
                    "SafeWalk готов! 🛡️",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Ошибка: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun visualizeData() {
        clearOverlays()

        // 1. Инциденты - яркие точки с инфо
        visualizeIncidents()

        // 2. Жалобы - меньшие точки
        visualizeComplaints()

        // 3. Безопасные зоны (зелёные) вокруг УПСМ/больниц
        visualizeSafeZones()

        mapView.invalidate()
    }

    private fun clearOverlays() {
        incidentMarkers.forEach { mapView.overlays.remove(it) }
        incidentMarkers.clear()

        complaintMarkers.forEach { mapView.overlays.remove(it) }
        complaintMarkers.clear()

        safeZoneOverlays.forEach { mapView.overlays.remove(it) }
        safeZoneOverlays.clear()
    }

    private fun visualizeIncidents() {
        for (incident in incidents) {
            // ТОЧКА инцидента (не круг!)
            val marker = Marker(mapView).apply {
                position = GeoPoint(incident.lat, incident.lon)

                // Заголовок и описание
                title = "Тип преступления: ${getIncidentTypeName(incident.type)}"
                snippet = buildString {
                    append("Адрес: ${getAddressFromCoords(incident.lat, incident.lon)}\n")
                    append("Дата происшествия: ${formatDate(incident.datetime)}\n")
                    append("Описание инцидента: ${incident.description ?: "Нет данных"}")
                }

                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

                // Цвет по severity
                icon = when {
                    incident.severity >= 4 -> createCircleDrawable(Color.RED, 24)
                    incident.severity >= 3 -> createCircleDrawable(Color.rgb(255, 100, 0), 20)
                    else -> createCircleDrawable(Color.rgb(255, 200, 0), 16)
                }
            }

            // Зона повышенной активности (100м вокруг)
            val dangerZone = Polygon(mapView).apply {
                points = Polygon.pointsAsCircle(GeoPoint(incident.lat, incident.lon), 100.0)

                fillColor = when {
                    incident.severity >= 4 -> Color.argb(40, 255, 0, 0)
                    incident.severity >= 3 -> Color.argb(30, 255, 100, 0)
                    else -> Color.argb(20, 255, 200, 0)
                }

                strokeColor = Color.TRANSPARENT
                strokeWidth = 0f
            }

            mapView.overlays.add(dangerZone)
            mapView.overlays.add(marker)
            incidentMarkers.add(marker)
        }
    }

    private fun visualizeComplaints() {
        for (complaint in complaints) {
            val marker = Marker(mapView).apply {
                position = GeoPoint(complaint.lat, complaint.lon)

                title = if (complaint.isFemale) "Жалоба (женщина)" else "Жалоба (мужчина)"
                snippet = buildString {
                    append("Дата: ${formatDate(complaint.datetime)}\n")
                    append("Уровень тревоги: ${complaint.weight.toInt()}/5\n")
                    append("Комментарий: ${complaint.text ?: "Нет"}")
                }

                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

                // Меньшие точки, розовые для женщин
                val color = if (complaint.isFemale)
                    Color.rgb(233, 30, 99)
                else
                    Color.rgb(33, 150, 243)

                icon = createCircleDrawable(color, 12)
            }

            mapView.overlays.add(marker)
            complaintMarkers.add(marker)
        }
    }

    private fun visualizeSafeZones() {
        for (sp in safePlaces) {
            // Зелёная зона вокруг
            val safeCircle = Polygon(mapView).apply {
                points = Polygon.pointsAsCircle(GeoPoint(sp.lat, sp.lon), sp.radius)
                fillColor = Color.argb(20, 0, 255, 0)
                strokeColor = Color.argb(60, 0, 200, 0)
                strokeWidth = 2f
            }

            // Маркер места
            val marker = Marker(mapView).apply {
                position = GeoPoint(sp.lat, sp.lon)
                title = sp.name ?: sp.type.name
                snippet = "Безопасное место - ${getTypeName(sp.type)}"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                icon = when (sp.type) {
                    SafePlaceType.POLICE -> ContextCompat.getDrawable(
                        this@MainActivity,
                        android.R.drawable.ic_lock_idle_lock
                    )
                    SafePlaceType.HOSPITAL -> ContextCompat.getDrawable(
                        this@MainActivity,
                        android.R.drawable.ic_menu_add
                    )
                    else -> ContextCompat.getDrawable(
                        this@MainActivity,
                        android.R.drawable.btn_star_big_on
                    )
                }
            }

            mapView.overlays.add(safeCircle)
            mapView.overlays.add(marker)
            safeZoneOverlays.add(safeCircle)
        }
    }

    // ===== МАРШРУТ ПО АДРЕСАМ =====

    private fun showRouteDialog() {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }

        val inputFrom = EditText(this).apply {
            hint = "Откуда (адрес)"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        val inputTo = EditText(this).apply {
            hint = "Куда (адрес)"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        dialogView.addView(inputFrom)
        dialogView.addView(inputTo)

        AlertDialog.Builder(this)
            .setTitle("Построить маршрут")
            .setView(dialogView)
            .setPositiveButton("Построить") { _, _ ->
                val from = inputFrom.text.toString()
                val to = inputTo.text.toString()

                if (from.isNotBlank() && to.isNotBlank()) {
                    buildRouteFromAddresses(from, to)
                } else {
                    Toast.makeText(this, "Заполните оба адреса", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun buildRouteFromAddresses(fromAddress: String, toAddress: String) {
        lifecycleScope.launch {
            try {
                Toast.makeText(
                    this@MainActivity,
                    "Ищем адреса...",
                    Toast.LENGTH_SHORT
                ).show()

                // Геокодирование адресов
                val fromPoint = geocodeAddress(fromAddress)
                val toPoint = geocodeAddress(toAddress)

                if (fromPoint == null || toPoint == null) {
                    Toast.makeText(
                        this@MainActivity,
                        "Не удалось найти один из адресов",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                // Строим маршрут
                buildSafeRoute(fromPoint, toPoint)

            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Ошибка: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private suspend fun geocodeAddress(address: String): GeoPoint? {
        return withContext(Dispatchers.IO) {
            try {
                // Используем Nominatim для геокодирования
                val encodedAddress = java.net.URLEncoder.encode("$address, Бишкек, Кыргызстан", "UTF-8")
                val url = "https://nominatim.openstreetmap.org/search?" +
                        "q=$encodedAddress&format=json&limit=1"

                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.setRequestProperty("User-Agent", packageName)
                connection.connectTimeout = 10000 // 10 seconds
                connection.readTimeout = 10000 // 10 seconds
                connection.requestMethod = "GET"

                val responseCode = connection.responseCode
                if (responseCode != 200) {
                    android.util.Log.e("Geocoding", "HTTP Error: $responseCode")
                    return@withContext null
                }

                val response = connection.inputStream.bufferedReader().readText()
                connection.disconnect()

                if (response.isBlank()) {
                    android.util.Log.e("Geocoding", "Empty response")
                    return@withContext null
                }

                val json = org.json.JSONArray(response)

                if (json.length() > 0) {
                    val obj = json.getJSONObject(0)
                    val lat = obj.optDouble("lat", Double.NaN)
                    val lon = obj.optDouble("lon", Double.NaN)

                    if (lat.isNaN() || lon.isNaN()) {
                        android.util.Log.e("Geocoding", "Invalid coordinates in response")
                        return@withContext null
                    }

                    GeoPoint(lat, lon)
                } else {
                    android.util.Log.w("Geocoding", "No results found for: $address")
                    null
                }
            } catch (e: java.net.SocketTimeoutException) {
                android.util.Log.e("Geocoding", "Timeout: ${e.message}")
                null
            } catch (e: java.io.IOException) {
                android.util.Log.e("Geocoding", "IO Error: ${e.message}")
                null
            } catch (e: org.json.JSONException) {
                android.util.Log.e("Geocoding", "JSON Parse Error: ${e.message}")
                null
            } catch (e: Exception) {
                android.util.Log.e("Geocoding", "Unexpected error: ${e.message}")
                null
            }
        }
    }

    // ===== ПОСТРОЕНИЕ БЕЗОПАСНОГО МАРШРУТА =====

    private fun buildSafeRoute(start: GeoPoint, end: GeoPoint) {
        lifecycleScope.launch {
            try {
                Toast.makeText(
                    this@MainActivity,
                    "Строим безопасный маршрут...",
                    Toast.LENGTH_SHORT
                ).show()

                // Строим маршрут с учётом приоритета главных дорог
                val route = buildRouteWithRoadPriority(start, end)

                if (route != null) {
                    val evaluation = riskEngine.evaluateRoute(route.points)

                    displayRoute(route, evaluation)

                    val riskLevel = when {
                        evaluation.averageRisk < 0.5 -> "✅ Безопасно"
                        evaluation.averageRisk < 1.5 -> "⚠️ Умеренный риск"
                        else -> "⛔ Высокий риск"
                    }

                    Toast.makeText(
                        this@MainActivity,
                        "Маршрут: ${(route.distance / 1000).format(2)} км\n$riskLevel",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Не удалось построить маршрут",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Ошибка: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private suspend fun buildRouteWithRoadPriority(
        start: GeoPoint,
        end: GeoPoint
    ): RouteData? {
        return withContext(Dispatchers.IO) {
            try {
                val waypoints = arrayListOf(start, end)
                val road = roadManager.getRoad(waypoints)

                if (road.mStatus == Road.STATUS_OK && road.mRouteHigh.isNotEmpty()) {
                    RouteData(
                        points = road.mRouteHigh,
                        distance = road.mLength * 1000,
                        duration = road.mDuration * 60,
                        roadType = RoadType.DIRECT
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun displayRoute(route: RouteData, evaluation: RouteEvaluation) {
        routeLine?.let { mapView.overlays.remove(it) }

        routeLine = Polyline(mapView).apply {
            setPoints(route.points)
            outlinePaint.strokeWidth = 14f
            outlinePaint.strokeCap = Paint.Cap.ROUND

            outlinePaint.color = when {
                evaluation.averageRisk < 0.5 -> Color.argb(220, 76, 175, 80)
                evaluation.averageRisk < 1.5 -> Color.argb(220, 33, 150, 243)
                else -> Color.argb(220, 255, 152, 0)
            }
        }

        mapView.overlays.add(routeLine)

        // Добавляем маркеры старт/финиш
        val startMarker = Marker(mapView).apply {
            position = route.points.first()
            title = "Старт"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }

        val endMarker = Marker(mapView).apply {
            position = route.points.last()
            title = "Финиш"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }

        mapView.overlays.add(startMarker)
        mapView.overlays.add(endMarker)
        mapView.invalidate()
    }

    // ===== SOS РЕЖИМ =====

    private fun buildSOSRoute() {
        val currentPos = myLocationOverlay?.myLocation ?: mapView.mapCenter as GeoPoint

        lifecycleScope.launch {
            // Находим ближайшее БЕЗОПАСНОЕ место (УПСМ, больница, людная улица, кафе)
            val nearestSafe = findNearestSafeLocation(currentPos)

            if (nearestSafe != null) {
                val route = buildRouteWithRoadPriority(currentPos, nearestSafe.position)

                if (route != null) {
                    routeLine?.let { mapView.overlays.remove(it) }

                    routeLine = Polyline(mapView).apply {
                        setPoints(route.points)
                        outlinePaint.strokeWidth = 16f
                        outlinePaint.color = Color.argb(240, 244, 67, 54)
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                    }

                    mapView.overlays.add(routeLine)
                    mapView.invalidate()

                    Toast.makeText(
                        this@MainActivity,
                        "🚨 SOS: ${nearestSafe.name} - ${(route.distance).toInt()}м",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "Безопасные места не найдены поблизости",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun findNearestSafeLocation(from: GeoPoint): SafeLocationResult? {
        // Приоритет: УПСМ > Больница > Людная улица > Кафе 24/7
        val candidates = mutableListOf<SafeLocationResult>()

        // 1. Безопасные места из базы
        for (sp in safePlaces) {
            val dist = distanceMeters(from, GeoPoint(sp.lat, sp.lon))
            val priority = when (sp.type) {
                SafePlaceType.POLICE -> 1.0
                SafePlaceType.HOSPITAL -> 2.0
                SafePlaceType.SHOP24 -> 4.0
                SafePlaceType.CAFE24 -> 5.0
            }

            candidates.add(SafeLocationResult(
                position = GeoPoint(sp.lat, sp.lon),
                name = sp.name ?: sp.type.name,
                distance = dist,
                priority = priority
            ))
        }

        // 2. Главные освещённые улицы
        for (seg in litSegments.take(3)) {
            val midPoint = GeoPoint(
                (seg.startLat + seg.endLat) / 2,
                (seg.startLon + seg.endLon) / 2
            )
            val dist = distanceMeters(from, midPoint)

            candidates.add(SafeLocationResult(
                position = midPoint,
                name = "Освещённая главная улица",
                distance = dist,
                priority = 3.0
            ))
        }

        // Выбираем лучшее: комбинация расстояния и приоритета
        return candidates.minByOrNull { it.distance / 100 + it.priority }
    }

    // ===== ЖАЛОБА =====

    private fun startComplaintSelection() {
        isSelectingComplaintLocation = true

        Toast.makeText(
            this,
            "Нажмите на карту чтобы отметить опасное место",
            Toast.LENGTH_LONG
        ).show()

        btnMarkDanger.text = "Отмена"
        btnMarkDanger.setOnClickListener {
            cancelComplaintSelection()
        }
    }

    private fun selectComplaintLocation(point: GeoPoint) {

        // Показываем временный маркер
        complaintSelectionMarker?.let { mapView.overlays.remove(it) }

        complaintSelectionMarker = Marker(mapView).apply {
            position = point
            title = "Выбранное место"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = ContextCompat.getDrawable(
                this@MainActivity,
                android.R.drawable.ic_input_add
            )
        }

        mapView.overlays.add(complaintSelectionMarker)
        mapView.invalidate()

        // Диалог с деталями жалобы
        showComplaintDetailsDialog(point)
    }

    private fun showComplaintDetailsDialog(point: GeoPoint) {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }

        val dangerOptions = arrayOf("1 - Слабо", "2 - Средне", "3 - Опасно", "4 - Очень опасно", "5 - Критично")
        var selectedWeight = 3.0

        val genderOptions = arrayOf("Женщина", "Мужчина", "Не указывать")
        var isFemale: Boolean? = null

        AlertDialog.Builder(this)
            .setTitle("Насколько опасно это место?")
            .setSingleChoiceItems(dangerOptions, 2) { _, which ->
                selectedWeight = (which + 1).toDouble()
            }
            .setPositiveButton("Далее") { dialog, _ ->
                dialog.dismiss()
                // Show gender selection dialog
                AlertDialog.Builder(this)
                    .setTitle("Укажите пол (опционально)")
                    .setItems(genderOptions) { _, which ->
                        isFemale = when (which) {
                            0 -> true
                            1 -> false
                            else -> null
                        }
                        addComplaint(point, selectedWeight, isFemale ?: true)
                        cancelComplaintSelection()
                    }
                    .setNegativeButton("Отмена") { _, _ ->
                        cancelComplaintSelection()
                    }
                    .show()
            }
            .setNegativeButton("Отмена") { _, _ ->
                cancelComplaintSelection()
            }
            .show()
    }

    private fun addComplaint(point: GeoPoint, weight: Double, isFemale: Boolean) {
        val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date(System.currentTimeMillis()))

        val complaint = Complaint(
            id = System.currentTimeMillis(),
            lat = point.latitude,
            lon = point.longitude,
            weight = weight,
            isFemale = isFemale,
            datetime = currentDate,
            text = "Жалоба пользователя"
        )

        complaints.add(complaint)
        riskEngine = RiskEngine(incidents, complaints, safePlaces, litSegments)

        visualizeData()

        Toast.makeText(this, "Жалоба добавлена ✓", Toast.LENGTH_SHORT).show()
    }

    private fun cancelComplaintSelection() {
        isSelectingComplaintLocation = false
        complaintSelectionMarker?.let { mapView.overlays.remove(it) }
        complaintSelectionMarker = null
        mapView.invalidate()

        btnMarkDanger.text = "Жалоба"
        btnMarkDanger.setOnClickListener {
            startComplaintSelection()
        }
    }

    // ===== УТИЛИТЫ =====

    private fun createCircleDrawable(color: Int, sizeDp: Int): android.graphics.drawable.Drawable {
        val sizePx = (sizeDp * resources.displayMetrics.density).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        val paint = Paint().apply {
            this.color = color
            isAntiAlias = true
        }

        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)

        // Белая обводка
        paint.style = Paint.Style.STROKE
        paint.color = Color.WHITE
        paint.strokeWidth = 2f
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - 1, paint)

        return android.graphics.drawable.BitmapDrawable(resources, bitmap)
    }

    private fun getIncidentTypeName(type: String): String {
        return when (type) {
            "robbery" -> "Грабёж"
            "assault" -> "Нападение"
            "harassment" -> "Преследование"
            "murder" -> "Убийство"
            "armed_robbery" -> "Вооружённый грабёж"
            else -> "Прочее"
        }
    }

    private fun getTypeName(type: SafePlaceType): String {
        return when (type) {
            SafePlaceType.POLICE -> "УПСМ / Милиция"
            SafePlaceType.HOSPITAL -> "Больница / Травмпункт"
            SafePlaceType.SHOP24 -> "Магазин 24/7"
            SafePlaceType.CAFE24 -> "Кафе 24/7"
        }
    }

    private fun getAddressFromCoords(lat: Double, lon: Double): String {
        // TODO: реальный reverse geocoding
        return "Павлова улица" // Заглушка
    }


    private fun formatDate(dateStr: String): String {
        // TODO: нормальное форматирование
        return dateStr.substring(0, 10)
    }

    private fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val R = 6371000.0
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)

        val x = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(x), sqrt(1 - x))

        return R * c
    }

    private fun Double.format(digits: Int) = "%.${digits}f".format(this)

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                notGranted.toTypedArray(),
                LOCATION_PERMISSION_CODE
            )
        } else {
            // Permissions already granted, enable location tracking
            enableLocationTracking()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            LOCATION_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() &&
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    // All permissions granted
                    enableLocationTracking()
                    Toast.makeText(
                        this,
                        "Доступ к местоположению предоставлен",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    // Permission denied
                    Toast.makeText(
                        this,
                        "Для работы приложения требуется доступ к местоположению",
                        Toast.LENGTH_LONG
                    ).show()

                    // Check if we should show rationale
                    val shouldShowRationale = permissions.any { permission ->
                        ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
                    }

                    if (!shouldShowRationale) {
                        // User selected "Don't ask again", show dialog to open settings
                        showPermissionSettingsDialog()
                    }
                }
            }
        }
    }

    private fun enableLocationTracking() {
        myLocationOverlay?.let {
            if (!it.isMyLocationEnabled) {
                it.enableMyLocation()
            }
            if (!it.isFollowLocationEnabled) {
                it.enableFollowLocation()
            }
        }
    }

    private fun showPermissionSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Требуется доступ к местоположению")
            .setMessage("Для работы SafeWalk необходим доступ к вашему местоположению. Пожалуйста, включите разрешение в настройках приложения.")
            .setPositiveButton("Открыть настройки") { _, _ ->
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                )
                val uri = android.net.Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}

// ===== ВСПОМОГАТЕЛЬНЫЕ КЛАССЫ =====

data class SafeLocationResult(
    val position: GeoPoint,
    val name: String,
    val distance: Double,
    val priority: Double
)