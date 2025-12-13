package com.example.saktahahathonv1.tracking

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.widget.LinearLayout
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

class TrackingActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var btnBack: TextView
    private lateinit var childSelector: LinearLayout
    private lateinit var txtSelectedChild: TextView
    private lateinit var cardChildStatus: MaterialCardView
    private lateinit var txtChildRoute: TextView
    private lateinit var txtLastUpdate: TextView
    private lateinit var btnCall: MaterialButton
    private lateinit var btnMessage: MaterialButton

    private val handler = Handler(Looper.getMainLooper())
    private val trackingProvider by lazy { MockTrackingProvider(this) }
    private var trackedChildren: List<TrackedChild> = emptyList()
    private var currentChildIndex = 0
    private var currentRouteOverlay: Polyline? = null
    private val routeMarkers = mutableListOf<Marker>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tracking)

        Configuration.getInstance().load(
            this,
            PreferenceManager.getDefaultSharedPreferences(this)
        )
        Configuration.getInstance().userAgentValue = packageName

        initViews()
        setupMap()
        loadLocalData()
        setupUI()
        startLocationUpdates()
    }

    private fun initViews() {
        mapView = findViewById(R.id.mapView)
        btnBack = findViewById(R.id.btnBack)
        childSelector = findViewById(R.id.childSelector)
        txtSelectedChild = findViewById(R.id.txtSelectedChild)
        cardChildStatus = findViewById(R.id.cardChildStatus)
        txtChildRoute = findViewById(R.id.txtChildRoute)
        txtLastUpdate = findViewById(R.id.txtLastUpdate)
        btnCall = findViewById(R.id.btnCall)
        btnMessage = findViewById(R.id.btnMessage)
    }

    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        mapView.controller.setZoom(13.5)
        mapView.controller.setCenter(GeoPoint(42.8746, 74.5698))
    }

    private fun setupUI() {
        btnBack.setOnClickListener {
            finish()
        }

        childSelector.setOnClickListener {
            showChildSelector()
        }

        btnCall.setOnClickListener {
            callChild()
        }

        btnMessage.setOnClickListener {
            messageChild()
        }
    }

    private fun loadLocalData() {
        trackedChildren = trackingProvider.loadTrackedChildren()

        if (trackedChildren.isNotEmpty()) {
            currentChildIndex = 0
            updateChildInfo()
        } else {
            txtSelectedChild.text = getString(R.string.app_name)
            txtChildRoute.text = getString(R.string.not_available)
        }
    }

    private fun showChildSelector() {
        if (trackedChildren.isEmpty()) return

        val names = trackedChildren.map { "${it.name} (${it.relationship})" }.toTypedArray()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Выберите ребенка")
            .setItems(names) { _, which ->
                currentChildIndex = which
                updateChildInfo()
            }
            .show()
    }

    private fun updateChildInfo() {
        if (trackedChildren.isEmpty()) return

        val child = trackedChildren[currentChildIndex]
        txtSelectedChild.text = "${child.name} (${child.relationship})"
        val minutesAgo = ((System.currentTimeMillis() - child.lastUpdated) / 60000).coerceAtLeast(0)
        txtLastUpdate.text = "Обновлено: $minutesAgo мин назад"
        txtChildRoute.text = "Маршрут: ${child.route.size} точек"
        showRouteForChild(child)
    }

    private fun callChild() {
        if (trackedChildren.isEmpty()) return
        val child = trackedChildren[currentChildIndex]
        val intent = Intent(Intent.ACTION_DIAL)
        intent.data = Uri.parse("tel:${child.phone}")
        startActivity(intent)
    }

    private fun messageChild() {
        if (trackedChildren.isEmpty()) return
        val child = trackedChildren[currentChildIndex]
        val intent = Intent(Intent.ACTION_SENDTO)
        intent.data = Uri.parse("smsto:${child.phone}")
        intent.putExtra("sms_body", "Привет! Где ты сейчас?")
        startActivity(intent)
    }

    private fun startLocationUpdates() {
        val updateRunnable = object : Runnable {
            override fun run() {
                if (trackedChildren.isNotEmpty()) {
                    val child = trackedChildren[currentChildIndex]
                    val minutesAgo = ((System.currentTimeMillis() - child.lastUpdated) / 60000).coerceAtLeast(0)
                    txtLastUpdate.text = "Обновлено: $minutesAgo мин назад"
                }

                handler.postDelayed(this, 30000) // Обновление каждые 30 секунд
            }
        }

        handler.post(updateRunnable)
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun showRouteForChild(child: TrackedChild) {
        clearRoute()

        val routePoints = child.route.map { it.toGeoPoint() }
        if (routePoints.isEmpty()) return

        val polyline = Polyline().apply {
            outlinePaint.color = resources.getColor(R.color.primary, theme)
            outlinePaint.strokeWidth = 6f
            setPoints(routePoints)
        }

        val startMarker = Marker(mapView).apply {
            position = routePoints.first()
            title = "Старт ${child.name}"
            snippet = "Начало маршрута"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }

        val endMarker = Marker(mapView).apply {
            position = routePoints.last()
            title = "Финиш ${child.name}"
            snippet = "Текущая точка"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }

        val currentMarker = Marker(mapView).apply {
            position = child.currentLocation
            title = child.name
            snippet = "Последнее обновление маршрута"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }

        mapView.overlays.add(polyline)
        mapView.overlays.add(startMarker)
        mapView.overlays.add(endMarker)
        mapView.overlays.add(currentMarker)

        currentRouteOverlay = polyline
        routeMarkers.addAll(listOf(startMarker, endMarker, currentMarker))

        mapView.controller.setZoom(15.5)
        mapView.controller.animateTo(child.currentLocation)
        mapView.invalidate()
    }

    private fun clearRoute() {
        currentRouteOverlay?.let { mapView.overlays.remove(it) }
        routeMarkers.forEach { mapView.overlays.remove(it) }
        routeMarkers.clear()
        currentRouteOverlay = null
    }
}
