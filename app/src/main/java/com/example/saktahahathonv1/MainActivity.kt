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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.bottomnavigation.BottomNavigationView
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
import com.example.saktahahathonv1.history.HistoryActivity
import com.example.saktahahathonv1.profile.ProfileActivity
import com.example.saktahahathonv1.auth.AuthManager
import com.example.saktahahathonv1.auth.LoginActivity
import com.example.saktahahathonv1.sos.SosActivity
import com.example.saktahahathonv1.saferoute.SafeRouteActivity
import com.example.saktahahathonv1.escort.EscortModeActivity
import com.example.saktahahathonv1.tracking.TrackingActivity
import com.example.saktahahathonv1.notifications.NotificationHelper
import kotlin.math.*

class MainActivity : AppCompatActivity() {

    private lateinit var authManager: AuthManager
    private lateinit var mapView: MapView
    private lateinit var btnSos: MaterialButton
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var btnZoomIn: ImageButton
    private lateinit var btnZoomOut: ImageButton
    private lateinit var cardProfile: MaterialCardView
    private lateinit var btnSafeRoute: MaterialCardView
    private lateinit var btnEscortHome: MaterialCardView
    private lateinit var cardZoneStatus: MaterialCardView
    private lateinit var txtZoneTitle: TextView
    private lateinit var txtZoneSubtitle: TextView
    private lateinit var imgZoneIcon: ImageView
    private lateinit var txtSafetyStatus: TextView
    private lateinit var statusDot: View
    private lateinit var safetyStatusBar: LinearLayout

    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var routeLine: Polyline? = null

    // Engines
    private lateinit var riskEngine: RiskEngine
    private lateinit var roadManager: OSRMRoadManager
    private lateinit var routingEngine: SafeRoutingEngine

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
        btnSafeRoute = findViewById(R.id.btnSafeRoute)
        btnEscortHome = findViewById(R.id.btnEscortHome)
        cardZoneStatus = findViewById(R.id.cardZoneStatus)
        txtZoneTitle = findViewById(R.id.txtZoneTitle)
        txtZoneSubtitle = findViewById(R.id.txtZoneSubtitle)
        imgZoneIcon = findViewById(R.id.imgZoneIcon)
        txtSafetyStatus = findViewById(R.id.txtSafetyStatus)
        statusDot = findViewById(R.id.statusDot)
        safetyStatusBar = findViewById(R.id.safetyStatusBar)
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

        // Overlay для событий карты
        val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
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
        // SOS - открыть экран SOS
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

    private fun showZoneInfoDialog() {
        val currentPos = myLocationOverlay?.myLocation ?: mapView.mapCenter as GeoPoint

        if (::riskEngine.isInitialized) {
            val risk = riskEngine.riskAtPoint(currentPos)
            val riskLevel = when {
                risk < 0.5 -> "Низкий"
                risk < 1.5 -> "Средний"
                else -> "Высокий"
            }

            AlertDialog.Builder(this)
                .setTitle("Информация о зоне")
                .setMessage("""
                    Уровень риска: $riskLevel
                    Коэффициент: ${String.format("%.2f", risk)}

                    Инцидентов поблизости: ${countNearbyIncidents(currentPos)}
                    Жалоб поблизости: ${countNearbyComplaints(currentPos)}
                """.trimIndent())
                .setPositiveButton("ОК", null)
                .show()
        }
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
                val centerLat = 42.8746
                val centerLon = 74.5698

                val dataManager = DataManager(this@MainActivity)
                incidents.addAll(dataManager.loadIncidents())
                complaints.addAll(dataManager.loadComplaints())
                safePlaces.addAll(dataManager.loadSafePlaces())
                litSegments.addAll(dataManager.loadLitSegments())

                crowdedAreas.addAll(DemoDataGenerator.generateCrowdedAreas(centerLat, centerLon))

                riskEngine = RiskEngine(incidents, complaints, safePlaces, litSegments)
                roadManager = OSRMRoadManager(this@MainActivity, packageName)
                routingEngine = SafeRoutingEngine(riskEngine, roadManager)

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

            // Зона риска вокруг инцидента
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
            ActivityCompat.requestPermissions(
                this,
                notGranted.toTypedArray(),
                LOCATION_PERMISSION_CODE
            )
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
                if (grantResults.isNotEmpty() &&
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    enableLocationTracking()
                    Toast.makeText(
                        this,
                        getString(R.string.location_granted),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.location_permission_required),
                        Toast.LENGTH_LONG
                    ).show()
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
}
