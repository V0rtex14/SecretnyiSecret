package com.example.saktahahathonv1.saferoute

import android.graphics.Color
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.saktahahathonv1.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

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

    private var selectedRoute = "safe"
    private var safeRouteOverlay: Polyline? = null
    private var normalRouteOverlay: Polyline? = null

    // Демо точки в Бишкеке
    private val startPoint = GeoPoint(42.8746, 74.5698) // Центр
    private val endPoint = GeoPoint(42.8850, 74.5850) // Конечная точка

    // Опасные зоны (демо)
    private val dangerZones = listOf(
        GeoPoint(42.8780, 74.5750),
        GeoPoint(42.8800, 74.5800)
    )

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
        setupRoutes()
        setupClickListeners()
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

        // Центрируем карту между точками
        val centerLat = (startPoint.latitude + endPoint.latitude) / 2
        val centerLon = (startPoint.longitude + endPoint.longitude) / 2
        val center = GeoPoint(centerLat, centerLon)

        mapView.controller.setZoom(14.0)
        mapView.controller.setCenter(center)

        // Добавляем маркеры
        addMarkers()
    }

    private fun addMarkers() {
        // Стартовый маркер
        val startMarker = Marker(mapView).apply {
            position = startPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Начало маршрута"
            snippet = "Ваше местоположение"
        }
        mapView.overlays.add(startMarker)

        // Конечный маркер
        val endMarker = Marker(mapView).apply {
            position = endPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Пункт назначения"
            snippet = "Конечная точка"
        }
        mapView.overlays.add(endMarker)

        // Маркеры опасных зон
        dangerZones.forEach { zone ->
            val dangerMarker = Marker(mapView).apply {
                position = zone
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = "Опасная зона"
                snippet = "Рекомендуется избегать"
            }
            mapView.overlays.add(dangerMarker)
        }
    }

    private fun setupRoutes() {
        // Безопасный маршрут (обход опасных зон)
        val safeRoutePoints = listOf(
            startPoint,
            GeoPoint(42.8760, 74.5680),
            GeoPoint(42.8780, 74.5650),
            GeoPoint(42.8820, 74.5700),
            GeoPoint(42.8840, 74.5780),
            endPoint
        )

        safeRouteOverlay = Polyline().apply {
            setPoints(safeRoutePoints)
            outlinePaint.color = Color.parseColor("#34C759") // Зелёный
            outlinePaint.strokeWidth = 12f
        }
        mapView.overlays.add(safeRouteOverlay)

        // Обычный маршрут (прямой, проходит через опасную зону)
        val normalRoutePoints = listOf(
            startPoint,
            GeoPoint(42.8770, 74.5730),
            dangerZones[0],
            dangerZones[1],
            endPoint
        )

        normalRouteOverlay = Polyline().apply {
            setPoints(normalRoutePoints)
            outlinePaint.color = Color.parseColor("#80FFD60A") // Жёлтый полупрозрачный
            outlinePaint.strokeWidth = 10f
        }
        mapView.overlays.add(normalRouteOverlay)

        // Данные о маршрутах
        txtSafeDistance.text = "2.3 км"
        txtSafeTime.text = "28 мин"
        txtNormalDistance.text = "1.8 км"
        txtNormalTime.text = "22 мин"
        txtAvoidsDanger.text = "Избегает ${dangerZones.size} опасные зоны"

        // По умолчанию выбран безопасный маршрут
        selectSafeRoute()

        mapView.invalidate()
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        cardSafeRoute.setOnClickListener {
            selectSafeRoute()
        }

        cardNormalRoute.setOnClickListener {
            selectNormalRoute()
        }

        btnStartNavigation.setOnClickListener {
            startNavigation()
        }
    }

    private fun selectSafeRoute() {
        selectedRoute = "safe"

        // Визуальное выделение карточки
        cardSafeRoute.strokeWidth = 3
        cardNormalRoute.strokeWidth = 1

        // Обновляем видимость маршрутов
        safeRouteOverlay?.outlinePaint?.alpha = 255
        normalRouteOverlay?.outlinePaint?.alpha = 80

        btnStartNavigation.text = "Начать безопасный маршрут"
        mapView.invalidate()
    }

    private fun selectNormalRoute() {
        selectedRoute = "normal"

        // Визуальное выделение карточки
        cardSafeRoute.strokeWidth = 1
        cardNormalRoute.strokeWidth = 3

        // Обновляем видимость маршрутов
        safeRouteOverlay?.outlinePaint?.alpha = 80
        normalRouteOverlay?.outlinePaint?.alpha = 255

        btnStartNavigation.text = "Начать обычный маршрут"
        mapView.invalidate()

        // Предупреждение
        Toast.makeText(
            this,
            "Внимание: этот маршрут проходит через опасные зоны",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun startNavigation() {
        val routeType = if (selectedRoute == "safe") "Безопасный" else "Обычный"
        Toast.makeText(
            this,
            "$routeType маршрут начат. Следуйте указаниям.",
            Toast.LENGTH_SHORT
        ).show()

        // Здесь можно запустить пошаговую навигацию
        // Для демо просто показываем сообщение
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
