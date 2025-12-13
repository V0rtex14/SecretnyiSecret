package com.example.saktahahathonv1

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.preference.PreferenceManager

import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
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
import com.example.saktahahathonv1.friends.FriendsActivity
import com.example.saktahahathonv1.history.HistoryActivity
import com.example.saktahahathonv1.profile.ProfileActivity
import com.example.saktahahathonv1.auth.AuthManager
import com.example.saktahahathonv1.auth.LoginActivity
import com.example.saktahahathonv1.ai.AIPredictiveRiskEngine
import com.example.saktahahathonv1.ai.SafeGuardianAI
import com.example.saktahahathonv1.ai.UserProfile
import com.example.saktahahathonv1.ai.AnxietyLevel
import com.example.saktahahathonv1.ai.LocationTracker
import com.example.saktahahathonv1.sos.SosActivity
import com.example.saktahahathonv1.route.SafeRouteActivity
import com.example.saktahahathonv1.escort.EscortModeActivity
import com.example.saktahahathonv1.tracking.TrackingActivity
import android.widget.TextView
import kotlin.math.*

class MainActivity : AppCompatActivity() {

    private lateinit var authManager: AuthManager
    private lateinit var mapView: MapView
    private lateinit var btnSos: ExtendedFloatingActionButton
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var btnZoomIn: ImageButton
    private lateinit var btnZoomOut: ImageButton
    private lateinit var cardProfile: MaterialCardView
    private lateinit var cardFilter: MaterialCardView
    private lateinit var cardAIAssistant: MaterialCardView
    private lateinit var btnSafeRoute: MaterialCardView
    private lateinit var btnEscortHome: MaterialCardView
    private lateinit var cardZoneStatus: MaterialCardView
    private lateinit var statusDot: View
    private lateinit var txtSafetyStatus: TextView
    private lateinit var txtZoneTitle: TextView
    private lateinit var txtZoneSubtitle: TextView
    private lateinit var imgZoneIcon: ImageView

    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var routeLine: Polyline? = null

    // Engines
    private lateinit var riskEngine: RiskEngine
    private lateinit var roadManager: OSRMRoadManager
    private lateinit var routingEngine: SafeRoutingEngine

    // AI компоненты
    private lateinit var aiRiskEngine: AIPredictiveRiskEngine
    private lateinit var safeGuardianAI: SafeGuardianAI

    // Данные
    private val incidents = mutableListOf<Incident>()
    private val complaints = mutableListOf<Complaint>()
    private val safePlaces = mutableListOf<SafePlace>()
    private val litSegments = mutableListOf<LitSegment>()
    private val crowdedAreas = mutableListOf<CrowdedArea>()

    // Overlays
    private val incidentMarkers = mutableListOf<Marker>()
    private val complaintMarkers = mutableListOf<Marker>()
    private val safeZoneOverlays = mutableListOf<Polygon>()

    // Для выбора точки жалобы
    private var isSelectingComplaintLocation = false
    private var complaintSelectionMarker: Marker? = null

    // Текущий маршрут для AI
    private var currentRoute: List<GeoPoint>? = null

    private val LOCATION_PERMISSION_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authManager = AuthManager(this)

        // Проверяем авторизацию ПЕРЕД загрузкой UI
        if (!authManager.isLoggedIn()) {
            navigateToLogin()
            return
        }

        setContentView(R.layout.activity_main)

        Configuration.getInstance().load(
            this,
            PreferenceManager.getDefaultSharedPreferences(this)
        )
        Configuration.getInstance().userAgentValue = packageName

        initViews()
        setupMap()
        setupUI()
        loadDataAndInitEngines()
        checkPermissions()
    }

    private fun initViews() {
        mapView = findViewById(R.id.mapView)
        btnSos = findViewById(R.id.btnSos)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        btnZoomIn = findViewById(R.id.btnZoomIn)
        btnZoomOut = findViewById(R.id.btnZoomOut)
        cardProfile = findViewById(R.id.cardProfile)
        cardFilter = findViewById(R.id.cardFilter)
        cardAIAssistant = findViewById(R.id.cardAIAssistant)
        btnSafeRoute = findViewById(R.id.btnSafeRoute)
        btnEscortHome = findViewById(R.id.btnEscortHome)
        cardZoneStatus = findViewById(R.id.cardZoneStatus)
        statusDot = findViewById(R.id.statusDot)
        txtSafetyStatus = findViewById(R.id.txtSafetyStatus)
        txtZoneTitle = findViewById(R.id.txtZoneTitle)
        txtZoneSubtitle = findViewById(R.id.txtZoneSubtitle)
        imgZoneIcon = findViewById(R.id.imgZoneIcon)
    }

    override fun onResume() {
        super.onResume()

        if (!authManager.isLoggedIn()) {
            navigateToLogin()
            return
        }

        mapView.onResume()
        bottomNavigation.selectedItemId = R.id.nav_home
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        val bishkek = GeoPoint(42.8746, 74.5698)
        mapView.controller.setZoom(14.0)
        mapView.controller.setCenter(bishkek)

        val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                if (p != null) {
                    if (isSelectingComplaintLocation) {
                        selectComplaintLocation(p)
                        return true
                    }
                    if (routeDialogInputMode != null) {
                        handleRoutePointSelection(p)
                        return true
                    }
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
        // SOS
        btnSos.setOnClickListener {
            startActivity(Intent(this, SosActivity::class.java))
        }

        // Зум кнопки
        btnZoomIn.setOnClickListener {
            mapView.controller.zoomIn()
        }

        btnZoomOut.setOnClickListener {
            mapView.controller.zoomOut()
        }

        // Профиль
        cardProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // Безопасный маршрут
        btnSafeRoute.setOnClickListener {
            startActivity(Intent(this, SafeRouteActivity::class.java))
        }

        // Проводить домой (режим сопровождения)
        btnEscortHome.setOnClickListener {
            startActivity(Intent(this, EscortModeActivity::class.java))
        }

        // Карточка статуса зоны
        cardZoneStatus.setOnClickListener {
            showZoneInfoDialog()
        }

        // AI Assistant
        cardAIAssistant.setOnClickListener {
            showAIAssistantDialog()
        }

        // Нижняя навигация
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // Уже на главном экране
                    true
                }
                R.id.nav_history -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                    true
                }
                R.id.nav_tracking -> {
                    startActivity(Intent(this, TrackingActivity::class.java))
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun showFilterDialog() {
        val options = arrayOf(
            "Показать инциденты",
            "Показать жалобы",
            "Показать безопасные зоны",
            "Показать освещённые улицы",
            "Добавить жалобу на опасное место",
            "🤖 Показать AI прогноз рисков"
        )

        AlertDialog.Builder(this)
            .setTitle("Действия на карте")
            .setItems(options) { _, which ->
                when (which) {
                    0, 1, 2, 3 -> {
                        visualizeData()
                    }
                    4 -> {
                        startComplaintSelection()
                    }
                    5 -> {
                        showAIPredictiveHeatmap()
                    }
                }
            }
            .show()
    }

    private fun showZoneInfoDialog() {
        val currentPos = myLocationOverlay?.myLocation ?: mapView.mapCenter as GeoPoint
        val risk = if (::riskEngine.isInitialized) riskEngine.riskAtPoint(currentPos) else 0.0
        val riskLabel = when {
            risk < 0.5 -> "Низкий"
            risk < 1.0 -> "Средний"
            else -> "Высокий"
        }

        AlertDialog.Builder(this)
            .setTitle("Информация о зоне")
            .setMessage(
                """
                Уровень риска: $riskLabel
                Коэффициент: ${String.format("%.2f", risk)}

                Инцидентов поблизости: ${countNearbyIncidents(currentPos)}
                Жалоб поблизости: ${countNearbyComplaints(currentPos)}
                """.trimIndent()
            )
            .setPositiveButton("ОК", null)
            .show()
    }

    private fun countNearbyIncidents(pos: GeoPoint): Int {
        return incidents.count { distanceMeters(pos, GeoPoint(it.lat, it.lon)) < 500 }
    }

    private fun countNearbyComplaints(pos: GeoPoint): Int {
        return complaints.count { distanceMeters(pos, GeoPoint(it.lat, it.lon)) < 500 }
    }

    private fun loadDataAndInitEngines() {
        BishkekAddresses.loadAddresses(this)

        lifecycleScope.launch {
            try {
                val dataManager = DataManager(this@MainActivity)
                val mockDataSource = LocalMockDataSource(this@MainActivity)

                incidents.addAll(dataManager.loadIncidents())
                complaints.addAll(dataManager.loadComplaints())
                safePlaces.addAll(dataManager.loadSafePlaces())
                litSegments.addAll(dataManager.loadLitSegments())
                crowdedAreas.addAll(mockDataSource.getCrowdedAreas())

                riskEngine = RiskEngine(incidents, complaints, safePlaces, litSegments)
                roadManager = OSRMRoadManager(this@MainActivity, packageName)
                routingEngine = SafeRoutingEngine(riskEngine, roadManager)

                // ✅ Инициализация AI
                aiRiskEngine = AIPredictiveRiskEngine()
                safeGuardianAI = SafeGuardianAI(
                    userProfile = UserProfile(
                        gender = authManager.getCurrentUser()?.gender ?: "unknown",
                        age = 25,
                        anxietyLevel = AnxietyLevel.MEDIUM
                    ),
                    locationTracker = object : LocationTracker {
                        override fun getCurrentLocation(): com.example.saktahahathonv1.ai.GeoPoint {
                            val loc = myLocationOverlay?.myLocation
                            return com.example.saktahahathonv1.ai.GeoPoint(
                                loc?.latitude ?: 42.8746,
                                loc?.longitude ?: 74.5698
                            )
                        }
                    }
                )

                visualizeData()
                updateSafetyStatus()

                val currentUser = authManager.getCurrentUser()
                Toast.makeText(
                    this@MainActivity,
                    "Добро пожаловать, ${currentUser?.name ?: "Пользователь"}",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Ошибка загрузки данных: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun updateSafetyStatus() {
        val currentPos = myLocationOverlay?.myLocation ?: mapView.mapCenter as GeoPoint

        if (::riskEngine.isInitialized) {
            val risk = riskEngine.riskAtPoint(currentPos)

            if (risk < 0.5) {
                // Безопасная зона
                txtSafetyStatus.text = getString(R.string.you_are_safe)
                statusDot.setBackgroundResource(R.drawable.bg_circle_green)

                txtZoneTitle.text = getString(R.string.current_zone_safe)
                txtZoneSubtitle.text = getString(R.string.no_incidents)
                imgZoneIcon.setImageResource(R.drawable.ic_check_circle)
                imgZoneIcon.setColorFilter(ContextCompat.getColor(this, R.color.success))

                cardZoneStatus.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_safe_bg))
                cardZoneStatus.strokeColor = ContextCompat.getColor(this, R.color.card_safe_border)
            } else if (risk < 1.5) {
                // Средний риск
                txtSafetyStatus.text = "Будьте внимательны"
                statusDot.setBackgroundResource(R.drawable.bg_circle_orange)

                txtZoneTitle.text = "Текущая зона: Средний риск"
                val nearbyIncidents = countNearbyIncidents(currentPos)
                txtZoneSubtitle.text = "$nearbyIncidents инцидентов за последний месяц"
                imgZoneIcon.setImageResource(R.drawable.ic_warning)
                imgZoneIcon.setColorFilter(ContextCompat.getColor(this, R.color.warning))

                cardZoneStatus.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_warning_bg))
                cardZoneStatus.strokeColor = ContextCompat.getColor(this, R.color.card_warning_border)
            } else {
                // Опасная зона
                txtSafetyStatus.text = getString(R.string.you_are_in_danger)
                statusDot.setBackgroundResource(R.drawable.bg_circle_red)

                txtZoneTitle.text = getString(R.string.current_zone_danger)
                val nearbyIncidents = countNearbyIncidents(currentPos)
                txtZoneSubtitle.text = "$nearbyIncidents инцидентов за последний месяц"
                imgZoneIcon.setImageResource(R.drawable.ic_warning)
                imgZoneIcon.setColorFilter(ContextCompat.getColor(this, R.color.error))

                cardZoneStatus.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_danger_bg))
                cardZoneStatus.strokeColor = ContextCompat.getColor(this, R.color.card_danger_border)
            }
        }
    }

    private fun visualizeData() {
        clearOverlays()
        visualizeIncidents()
        visualizeComplaints()
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
            val marker = Marker(mapView).apply {
                position = GeoPoint(incident.lat, incident.lon)
                title = "Тип: ${getIncidentTypeName(incident.type)}"
                snippet = buildString {
                    append("Адрес: ${getAddressFromCoords(incident.lat, incident.lon)}\n")
                    append("Дата: ${formatDate(incident.datetime)}\n")
                    append("Описание: ${incident.description ?: "Нет данных"}")
                }
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = when {
                    incident.severity >= 4 -> createCircleDrawable(Color.RED, 24)
                    incident.severity >= 3 -> createCircleDrawable(Color.rgb(255, 100, 0), 20)
                    else -> createCircleDrawable(Color.rgb(255, 200, 0), 16)
                }
            }

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
                    append("Уровень: ${complaint.weight.toInt()}/5\n")
                    append("Комментарий: ${complaint.text ?: "Нет"}")
                }
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
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
            val safeCircle = Polygon(mapView).apply {
                points = Polygon.pointsAsCircle(GeoPoint(sp.lat, sp.lon), sp.radius)
                fillColor = Color.argb(20, 52, 199, 89) // success color with alpha
                strokeColor = Color.argb(60, 52, 199, 89)
                strokeWidth = 2f
            }

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

    // ===== AI ФУНКЦИИ =====

    private fun showAIAssistantDialog() {
        val dialogView = layoutInflater.inflate(
            android.R.layout.simple_list_item_1,
            null
        ).apply {
            findViewById<TextView>(android.R.id.text1).apply {
                text = """
                    🤖 SafeGuardian AI
                    
                    Ваш персональный ИИ-телохранитель!
                    
                    Возможности:
                    • Предиктивные предупреждения
                    • Проактивный мониторинг
                    • Автоперепостроение маршрута
                    • Эмоциональная поддержка
                    
                    AI будет следить за вашей безопасностью
                    в режиме реального времени.
                """.trimIndent()
                setPadding(40, 40, 40, 40)
                textSize = 16f
            }
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Активировать") { _, _ ->
                currentRoute?.let { route ->
                    safeGuardianAI.activate(
                        route.map { point ->
                            com.example.saktahahathonv1.ai.GeoPoint(
                                point.latitude,
                                point.longitude
                            )
                        }
                    )
                    Toast.makeText(
                        this,
                        "🛡️ AI-телохранитель активирован",
                        Toast.LENGTH_LONG
                    ).show()
                } ?: run {
                    Toast.makeText(
                        this,
                        "Сначала постройте маршрут",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun showAIPredictiveHeatmap() {
        Toast.makeText(this, "🤖 AI анализирует город...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val bounds = mapView.boundingBox
                val targetTime = System.currentTimeMillis() + 1800000 // +30 минут

                // Для demo показываем несколько предсказанных горячих точек
                val hotspots = listOf(
                    Triple(42.8756, 74.5698, 0.85), // Высокий риск
                    Triple(42.8730, 74.5720, 0.72),
                    Triple(42.8770, 74.5680, 0.91)  // Очень высокий риск
                )

                for ((lat, lon, risk) in hotspots) {
                    val color = when {
                        risk > 0.8 -> Color.argb(100, 255, 0, 0)
                        risk > 0.6 -> Color.argb(80, 255, 165, 0)
                        else -> Color.argb(60, 255, 255, 0)
                    }

                    val hotspot = Polygon(mapView).apply {
                        points = Polygon.pointsAsCircle(GeoPoint(lat, lon), 150.0)
                        fillColor = color
                        strokeColor = Color.argb(150, 255, 0, 0)
                        strokeWidth = 3f
                    }

                    val marker = Marker(mapView).apply {
                        position = GeoPoint(lat, lon)
                        title = "⚠️ AI Прогноз"
                        snippet = "Риск через 30 мин: ${(risk * 100).toInt()}%"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = createCircleDrawable(Color.RED, 16)
                    }

                    mapView.overlays.add(hotspot)
                    mapView.overlays.add(marker)
                }

                mapView.invalidate()
                Toast.makeText(
                    this@MainActivity,
                    "✅ AI прогноз загружен",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Ошибка AI: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ===== МАРШРУТЫ =====

    private var routeDialogInputMode: String? = null
    private var selectedFromPoint: GeoPoint? = null
    private var selectedToPoint: GeoPoint? = null
    private var routeSelectionMarker: Marker? = null

    private fun handleRoutePointSelection(point: GeoPoint) {
        when (routeDialogInputMode) {
            "from" -> {
                selectedFromPoint = point
                Toast.makeText(this, "Выберите точку назначения", Toast.LENGTH_SHORT).show()
                routeDialogInputMode = "to"
                routeSelectionMarker?.let { mapView.overlays.remove(it) }
                routeSelectionMarker = Marker(mapView).apply {
                    position = point
                    title = "Откуда"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = ContextCompat.getDrawable(
                        this@MainActivity,
                        android.R.drawable.ic_menu_mylocation
                    )
                }
                mapView.overlays.add(routeSelectionMarker)
                mapView.invalidate()
            }
            "to" -> {
                selectedToPoint = point
                routeDialogInputMode = null
                Toast.makeText(this, "Строим маршрут...", Toast.LENGTH_SHORT).show()
                routeSelectionMarker?.let { mapView.overlays.remove(it) }
                routeSelectionMarker = null
                mapView.invalidate()
                if (selectedFromPoint != null && selectedToPoint != null) {
                    buildSafeRoute(selectedFromPoint!!, selectedToPoint!!)
                    selectedFromPoint = null
                    selectedToPoint = null
                }
            }
        }
    }

    private fun showRouteDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_route_selection, null)
        val inputFrom = dialogView.findViewById<android.widget.AutoCompleteTextView>(R.id.inputFrom)
        val inputTo = dialogView.findViewById<android.widget.AutoCompleteTextView>(R.id.inputTo)
        val btnBuild = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnBuild)
        val btnSwap = dialogView.findViewById<android.widget.ImageButton>(R.id.btnSwap)
        val btnMyLocation = dialogView.findViewById<android.widget.ImageButton>(R.id.btnMyLocation)
        val btnPickFromOnMap = dialogView.findViewById<android.widget.ImageButton>(R.id.btnPickFromOnMap)
        val btnPickToOnMap = dialogView.findViewById<android.widget.ImageButton>(R.id.btnPickToOnMap)

        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        val addresses = BishkekAddresses.KNOWN_LOCATIONS.map { "${it.name} - ${it.address}" }
        inputFrom.setAdapter(android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, addresses))
        inputTo.setAdapter(android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, addresses))

        btnMyLocation.setOnClickListener {
            val myLocation = myLocationOverlay?.myLocation
            if (myLocation != null) {
                inputFrom.setText("Моё местоположение")
            } else {
                Toast.makeText(this, "Местоположение недоступно", Toast.LENGTH_SHORT).show()
            }
        }

        btnPickFromOnMap.setOnClickListener {
            dialog.dismiss()
            routeDialogInputMode = "from"
            Toast.makeText(this, "Нажмите на карту", Toast.LENGTH_LONG).show()
        }

        btnPickToOnMap.setOnClickListener {
            dialog.dismiss()
            routeDialogInputMode = "to"
            Toast.makeText(this, "Нажмите на карту", Toast.LENGTH_LONG).show()
        }

        btnSwap.setOnClickListener {
            val temp = inputFrom.text.toString()
            inputFrom.setText(inputTo.text.toString())
            inputTo.setText(temp)
        }

        btnBuild.setOnClickListener {
            val from = inputFrom.text.toString()
            val to = inputTo.text.toString()
            if (from.isNotBlank() && to.isNotBlank()) {
                dialog.dismiss()
                buildRouteFromAddresses(from, to)
            } else {
                Toast.makeText(this, "Заполните оба адреса", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun buildRouteFromAddresses(fromAddress: String, toAddress: String) {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@MainActivity, "Ищем адреса...", Toast.LENGTH_SHORT).show()
                val fromPoint = getPointFromAddress(fromAddress)
                val toPoint = getPointFromAddress(toAddress)
                if (fromPoint == null || toPoint == null) {
                    Toast.makeText(this@MainActivity, "Не удалось найти адрес", Toast.LENGTH_LONG).show()
                    return@launch
                }
                buildSafeRoute(fromPoint, toPoint)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun getPointFromAddress(address: String): GeoPoint? {
        if (address.contains("Моё местоположение")) {
            return myLocationOverlay?.myLocation
        }
        for (location in BishkekAddresses.KNOWN_LOCATIONS) {
            if (address.contains(location.name, ignoreCase = true) ||
                address.contains(location.address, ignoreCase = true)) {
                return GeoPoint(location.lat, location.lon)
            }
        }
        return geocodeAddress(address)
    }

    private suspend fun geocodeAddress(address: String): GeoPoint? {
        return withContext(Dispatchers.IO) {
            try {
                val encodedAddress = java.net.URLEncoder.encode("$address, Бишкек, Кыргызстан", "UTF-8")
                val url = "https://nominatim.openstreetmap.org/search?q=$encodedAddress&format=json&limit=1"
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.setRequestProperty("User-Agent", packageName)
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.requestMethod = "GET"
                if (connection.responseCode != 200) return@withContext null
                val response = connection.inputStream.bufferedReader().readText()
                connection.disconnect()
                if (response.isBlank()) return@withContext null
                val json = org.json.JSONArray(response)
                if (json.length() > 0) {
                    val obj = json.getJSONObject(0)
                    val lat = obj.optDouble("lat", Double.NaN)
                    val lon = obj.optDouble("lon", Double.NaN)
                    if (lat.isNaN() || lon.isNaN()) return@withContext null
                    GeoPoint(lat, lon)
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun buildSafeRoute(start: GeoPoint, end: GeoPoint) {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@MainActivity, "Строим безопасные маршруты...", Toast.LENGTH_SHORT).show()
                val routeOptions = routingEngine.buildAlternativeRoutes(
                    start = start,
                    end = end,
                    litStreets = litSegments,
                    crowdedAreas = crowdedAreas,
                    safePlaces = safePlaces
                )
                if (routeOptions.isNotEmpty()) {
                    showRouteSelectionDialog(routeOptions)
                } else {
                    Toast.makeText(this@MainActivity, "Не удалось построить маршруты", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }

    private fun showRouteSelectionDialog(routes: List<RouteOption>) {
        val items = routes.map { route ->
            val distance = (route.route.distance / 1000).format(2)
            // ПРОДОЛЖЕНИЕ MainActivity.kt (вставить после предыдущей части)

            val duration = (route.route.duration / 60).toInt()
            val riskLevel = when {
                route.evaluation.adjustedRisk < 0.5 -> "✅ Безопасно"
                route.evaluation.adjustedRisk < 1.5 -> "⚠️ Умеренный риск"
                else -> "⛔ Высокий риск"
            }
            "${route.description}\n$distance км • $duration мин • $riskLevel"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Выберите маршрут")
            .setItems(items) { _, which ->
                displayRouteOption(routes[which])
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun displayRouteOption(routeOption: RouteOption) {
        displayRoute(routeOption.route, routeOption.evaluation.baseEvaluation)

        // Сохраняем текущий маршрут для AI
        currentRoute = routeOption.route.points

        val distance = (routeOption.route.distance / 1000).format(2)
        val duration = (routeOption.route.duration / 60).toInt()
        val riskLevel = when {
            routeOption.evaluation.adjustedRisk < 0.5 -> "✅ Безопасно"
            routeOption.evaluation.adjustedRisk < 1.5 -> "⚠️ Умеренный риск"
            else -> "⛔ Высокий риск"
        }
        Toast.makeText(
            this,
            "${routeOption.description}\n$distance км • $duration мин\n$riskLevel",
            Toast.LENGTH_LONG
        ).show()
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

    // ===== SOS =====

    private fun buildSOSRoute() {
        val currentPos = myLocationOverlay?.myLocation ?: mapView.mapCenter as GeoPoint
        lifecycleScope.launch {
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
                        "🚨 SOS: ${nearestSafe.name} - ${route.distance.toInt()}м",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                Toast.makeText(this@MainActivity, "Безопасные места не найдены", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun findNearestSafeLocation(from: GeoPoint): SafeLocationResult? {
        val candidates = mutableListOf<SafeLocationResult>()
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
        for (seg in litSegments.take(3)) {
            val midPoint = GeoPoint(
                (seg.startLat + seg.endLat) / 2,
                (seg.startLon + seg.endLon) / 2
            )
            val dist = distanceMeters(from, midPoint)
            candidates.add(SafeLocationResult(
                position = midPoint,
                name = "Освещённая улица",
                distance = dist,
                priority = 3.0
            ))
        }
        return candidates.minByOrNull { it.distance / 100 + it.priority }
    }

    private data class SafeLocationResult(
        val position: GeoPoint,
        val name: String,
        val distance: Double,
        val priority: Double
    )

    private suspend fun buildRouteWithRoadPriority(start: GeoPoint, end: GeoPoint): RouteData? {
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
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    // ===== ЖАЛОБЫ =====

    private fun startComplaintSelection() {
        isSelectingComplaintLocation = true
        Toast.makeText(this, "Нажмите на карту для отметки опасного места", Toast.LENGTH_LONG).show()
    }

    private fun selectComplaintLocation(point: GeoPoint) {
        complaintSelectionMarker?.let { mapView.overlays.remove(it) }
        complaintSelectionMarker = Marker(mapView).apply {
            position = point
            title = "Выбранное место"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.ic_input_add)
        }
        mapView.overlays.add(complaintSelectionMarker)
        mapView.invalidate()
        showComplaintDetailsDialog(point)
    }

    private fun showComplaintDetailsDialog(point: GeoPoint) {
        val dangerOptions = arrayOf("1 - Слабо", "2 - Средне", "3 - Опасно", "4 - Очень опасно", "5 - Критично")
        var selectedWeight = 3.0
        AlertDialog.Builder(this)
            .setTitle("Насколько опасно это место?")
            .setSingleChoiceItems(dangerOptions, 2) { _, which ->
                selectedWeight = (which + 1).toDouble()
            }
            .setPositiveButton("Далее") { dialog, _ ->
                dialog.dismiss()
                val genderOptions = arrayOf("Женщина", "Мужчина", "Не указывать")
                var isFemale: Boolean? = null
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
    }

    // ===== УТИЛИТЫ =====

    private fun Double.format(digits: Int) = "%.${digits}f".format(this)

    private fun createCircleDrawable(color: Int, sizeDp: Int): android.graphics.drawable.Drawable {
        val sizePx = (sizeDp * resources.displayMetrics.density).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = Paint().apply {
            this.color = color
            isAntiAlias = true
        }
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)
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
            SafePlaceType.HOSPITAL -> "Больница"
            SafePlaceType.SHOP24 -> "Магазин 24/7"
            SafePlaceType.CAFE24 -> "Кафе 24/7"
        }
    }

    private fun getAddressFromCoords(lat: Double, lon: Double): String {
        return "Бишкек"
    }

    private fun formatDate(dateStr: String): String {
        return try {
            dateStr.substring(0, 10)
        } catch (e: Exception) {
            dateStr
        }
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

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), LOCATION_PERMISSION_CODE)
        } else {
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
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    enableLocationTracking()
                    Toast.makeText(this, "Доступ к местоположению предоставлен", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Требуется доступ к местоположению", Toast.LENGTH_LONG).show()
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

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        safeGuardianAI.deactivate()
    }
}
