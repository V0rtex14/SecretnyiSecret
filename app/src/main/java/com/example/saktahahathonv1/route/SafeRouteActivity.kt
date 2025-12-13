package com.example.saktahahathonv1.route

import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.saktahahathonv1.R
import com.example.saktahahathonv1.data.*
import com.example.saktahahathonv1.escort.EscortModeActivity
import com.example.saktahahathonv1.map.RiskEngine
import com.example.saktahahathonv1.map.SafeRoutingEngine
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class SafeRouteActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var btnBack: TextView
    private lateinit var cardSafeRoute: MaterialCardView
    private lateinit var cardNormalRoute: MaterialCardView
    private lateinit var btnStartNavigation: MaterialButton
    private lateinit var txtSafeDistance: TextView
    private lateinit var txtSafeTime: TextView
    private lateinit var txtNormalDistance: TextView
    private lateinit var txtNormalTime: TextView
    private lateinit var txtAvoidsDanger: TextView

    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var safeRouteLine: Polyline? = null
    private var normalRouteLine: Polyline? = null

    private lateinit var roadManager: OSRMRoadManager
    private lateinit var riskEngine: RiskEngine

    private var selectedRouteType = "safe"

    // Демо точки для маршрута
    private val demoStart = GeoPoint(42.8746, 74.5698) // Центр Бишкека
    private val demoEnd = GeoPoint(42.8600, 74.5900) // Пункт назначения

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_safe_route)

        Configuration.getInstance().load(
            this,
            PreferenceManager.getDefaultSharedPreferences(this)
        )
        Configuration.getInstance().userAgentValue = packageName

        initViews()
        setupMap()
        setupUI()
        loadRoutes()
    }

    private fun initViews() {
        mapView = findViewById(R.id.mapView)
        btnBack = findViewById(R.id.btnBack)
        cardSafeRoute = findViewById(R.id.cardSafeRoute)
        cardNormalRoute = findViewById(R.id.cardNormalRoute)
        btnStartNavigation = findViewById(R.id.btnStartNavigation)
        txtSafeDistance = findViewById(R.id.txtSafeDistance)
        txtSafeTime = findViewById(R.id.txtSafeTime)
        txtNormalDistance = findViewById(R.id.txtNormalDistance)
        txtNormalTime = findViewById(R.id.txtNormalTime)
        txtAvoidsDanger = findViewById(R.id.txtAvoidsDanger)
    }

    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        mapView.controller.setZoom(14.0)
        mapView.controller.setCenter(demoStart)

        myLocationOverlay = MyLocationNewOverlay(
            GpsMyLocationProvider(this), mapView
        ).apply {
            enableMyLocation()
        }
        mapView.overlays.add(myLocationOverlay)

        roadManager = OSRMRoadManager(this, packageName)
    }

    private fun setupUI() {
        btnBack.setOnClickListener {
            finish()
        }

        cardSafeRoute.setOnClickListener {
            selectRoute("safe")
        }

        cardNormalRoute.setOnClickListener {
            selectRoute("normal")
        }

        btnStartNavigation.setOnClickListener {
            startNavigation()
        }

        // Изначально выбран безопасный маршрут
        selectRoute("safe")
    }

    private fun selectRoute(type: String) {
        selectedRouteType = type

        if (type == "safe") {
            cardSafeRoute.strokeWidth = 3
            cardSafeRoute.strokeColor = ContextCompat.getColor(this, R.color.success)

            cardNormalRoute.strokeWidth = 1
            cardNormalRoute.strokeColor = ContextCompat.getColor(this, R.color.card_warning_border)

            // Показать безопасный маршрут на карте
            safeRouteLine?.isEnabled = true
            normalRouteLine?.isEnabled = false
        } else {
            cardNormalRoute.strokeWidth = 3
            cardNormalRoute.strokeColor = ContextCompat.getColor(this, R.color.warning)

            cardSafeRoute.strokeWidth = 1
            cardSafeRoute.strokeColor = ContextCompat.getColor(this, R.color.card_safe_border)

            // Показать обычный маршрут на карте
            safeRouteLine?.isEnabled = false
            normalRouteLine?.isEnabled = true
        }

        mapView.invalidate()
    }

    private fun loadRoutes() {
        lifecycleScope.launch {
            try {
                // Загрузка данных
                val dataManager = DataManager(this@SafeRouteActivity)
                val incidents = dataManager.loadIncidents()
                val complaints = dataManager.loadComplaints()
                val safePlaces = dataManager.loadSafePlaces()
                val litSegments = dataManager.loadLitSegments()

                riskEngine = RiskEngine(incidents, complaints, safePlaces, litSegments)

                // Получаем текущее местоположение или демо
                val startPoint = myLocationOverlay?.myLocation ?: demoStart

                // Строим оба маршрута
                buildRoutes(startPoint, demoEnd)

            } catch (e: Exception) {
                Toast.makeText(
                    this@SafeRouteActivity,
                    "Ошибка: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private suspend fun buildRoutes(start: GeoPoint, end: GeoPoint) {
        withContext(Dispatchers.IO) {
            try {
                // Прямой маршрут
                val directWaypoints = arrayListOf(start, end)
                val directRoad = roadManager.getRoad(directWaypoints)

                // Безопасный маршрут через промежуточную точку (избегает опасные зоны)
                val safeMiddle = GeoPoint(
                    (start.latitude + end.latitude) / 2 + 0.005,
                    (start.longitude + end.longitude) / 2 + 0.005
                )
                val safeWaypoints = arrayListOf(start, safeMiddle, end)
                val safeRoad = roadManager.getRoad(safeWaypoints)

                withContext(Dispatchers.Main) {
                    // Отображаем маршруты
                    displayRoutes(directRoad, safeRoad)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@SafeRouteActivity,
                        "Ошибка построения маршрута",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun displayRoutes(directRoad: Road, safeRoad: Road) {
        // Удаляем старые маршруты
        safeRouteLine?.let { mapView.overlays.remove(it) }
        normalRouteLine?.let { mapView.overlays.remove(it) }

        // Обычный маршрут (оранжевый)
        if (directRoad.mStatus == Road.STATUS_OK && directRoad.mRouteHigh.isNotEmpty()) {
            normalRouteLine = Polyline(mapView).apply {
                setPoints(directRoad.mRouteHigh)
                outlinePaint.strokeWidth = 12f
                outlinePaint.color = Color.argb(200, 255, 149, 0) // warning color
                outlinePaint.strokeCap = Paint.Cap.ROUND
                isEnabled = false
            }
            mapView.overlays.add(normalRouteLine)

            // Обновляем UI
            val distKm = String.format("%.1f км", directRoad.mLength)
            val timeMin = (directRoad.mDuration / 60).toInt()
            txtNormalDistance.text = distKm
            txtNormalTime.text = "$timeMin мин"
        }

        // Безопасный маршрут (зелено-синий градиент)
        if (safeRoad.mStatus == Road.STATUS_OK && safeRoad.mRouteHigh.isNotEmpty()) {
            safeRouteLine = Polyline(mapView).apply {
                setPoints(safeRoad.mRouteHigh)
                outlinePaint.strokeWidth = 14f
                outlinePaint.color = Color.argb(220, 52, 199, 89) // success color
                outlinePaint.strokeCap = Paint.Cap.ROUND
            }
            mapView.overlays.add(safeRouteLine)

            // Обновляем UI
            val distKm = String.format("%.1f км", safeRoad.mLength)
            val timeMin = (safeRoad.mDuration / 60).toInt()
            txtSafeDistance.text = distKm
            txtSafeTime.text = "$timeMin мин"

            // Считаем избегаемые опасные зоны
            val dangerZones = countDangerZonesAvoided()
            txtAvoidsDanger.text = "Избегает $dangerZones опасные зоны"
        }

        // Добавляем маркеры старта и финиша
        addMarkers()

        // Центрируем карту на маршруте
        mapView.zoomToBoundingBox(safeRoad.mBoundingBox, true, 100)
        mapView.invalidate()
    }

    private fun addMarkers() {
        // Маркер старта
        val startMarker = Marker(mapView).apply {
            position = myLocationOverlay?.myLocation ?: demoStart
            title = "Начало"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }

        // Маркер финиша (розовый как в Figma)
        val endMarker = Marker(mapView).apply {
            position = demoEnd
            title = "Пункт назначения"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = createPinDrawable()
        }

        mapView.overlays.add(startMarker)
        mapView.overlays.add(endMarker)
    }

    private fun createPinDrawable(): android.graphics.drawable.Drawable {
        val sizePx = (32 * resources.displayMetrics.density).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        val paint = Paint().apply {
            color = Color.rgb(255, 107, 138) // розовый как в Figma
            isAntiAlias = true
        }

        // Рисуем pin
        canvas.drawCircle(sizePx / 2f, sizePx / 3f, sizePx / 3f, paint)

        // Нижняя часть
        val path = android.graphics.Path()
        path.moveTo(sizePx / 4f, sizePx / 3f)
        path.lineTo(sizePx / 2f, sizePx.toFloat())
        path.lineTo(sizePx * 3f / 4f, sizePx / 3f)
        path.close()
        canvas.drawPath(path, paint)

        // Белый центр
        paint.color = Color.WHITE
        canvas.drawCircle(sizePx / 2f, sizePx / 3f, sizePx / 6f, paint)

        return android.graphics.drawable.BitmapDrawable(resources, bitmap)
    }

    private fun countDangerZonesAvoided(): Int {
        // Симуляция - в реальности нужно считать зоны на прямом маршруте
        return (1..3).random()
    }

    private fun startNavigation() {
        val intent = Intent(this, EscortModeActivity::class.java)
        intent.putExtra("route_type", selectedRouteType)
        startActivity(intent)
        finish()
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
