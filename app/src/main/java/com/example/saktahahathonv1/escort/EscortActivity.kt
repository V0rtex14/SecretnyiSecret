package com.example.saktahahathonv1.escort

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.example.saktahahathonv1.R
import com.example.saktahahathonv1.map.SafeRoutingEngine
import com.example.saktahahathonv1.map.RiskEngine
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class EscortActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var txtDistance: TextView
    private lateinit var txtTime: TextView
    private lateinit var txtDestination: TextView
    private lateinit var txtInstruction: TextView
    private lateinit var txtStepDistance: TextView
    private lateinit var imgDirection: ImageView
    private lateinit var progressRoute: ProgressBar
    private lateinit var btnClose: ImageButton
    private lateinit var btnShareLocation: MaterialButton
    private lateinit var btnSOS: MaterialButton
    private lateinit var fabMyLocation: FloatingActionButton

    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var routeLine: Polyline? = null
    private lateinit var routingEngine: SafeRoutingEngine
    private lateinit var destination: GeoPoint

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_escort)

        setupMap()
        setupViews()
        setupRoute()
    }

    private fun setupMap() {
        mapView = findViewById(R.id.mapView)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(16.0)

        // Центр Бишкека
        mapView.controller.setCenter(GeoPoint(42.8746, 74.5698))

        // My Location
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView)
            myLocationOverlay?.enableMyLocation()
            myLocationOverlay?.enableFollowLocation()
            mapView.overlays.add(myLocationOverlay)
        }
    }

    private fun setupViews() {
        txtDistance = findViewById(R.id.txtDistance)
        txtTime = findViewById(R.id.txtTime)
        txtDestination = findViewById(R.id.txtDestination)
        txtInstruction = findViewById(R.id.txtInstruction)
        txtStepDistance = findViewById(R.id.txtStepDistance)
        imgDirection = findViewById(R.id.imgDirection)
        progressRoute = findViewById(R.id.progressRoute)
        btnClose = findViewById(R.id.btnClose)
        btnShareLocation = findViewById(R.id.btnShareLocation)
        btnSOS = findViewById(R.id.btnSOS)
        fabMyLocation = findViewById(R.id.fabMyLocation)

        btnClose.setOnClickListener {
            showStopEscortDialog()
        }

        btnShareLocation.setOnClickListener {
            shareLocation()
        }

        btnSOS.setOnClickListener {
            triggerSOS()
        }

        fabMyLocation.setOnClickListener {
            myLocationOverlay?.myLocation?.let {
                mapView.controller.animateTo(it)
            }
        }
    }

    private fun setupRoute() {
        // Получаем пункт назначения из Intent
        val destLat = intent.getDoubleExtra("dest_lat", 42.8766)
        val destLon = intent.getDoubleExtra("dest_lon", 74.5708)
        val destAddress = intent.getStringExtra("dest_address") ?: "Пункт назначения"

        destination = GeoPoint(destLat, destLon)
        txtDestination.text = destAddress

        // Инициализируем routing engine
        val roadManager = OSRMRoadManager(this, "SafeWalk/1.0")
        val riskEngine = RiskEngine(emptyList(), emptyList(), emptyList(), emptyList())
        routingEngine = SafeRoutingEngine(riskEngine, roadManager)

        // Строим маршрут
        buildRoute()

        // Добавляем маркер назначения
        addDestinationMarker()
    }

    private fun buildRoute() {
        val currentLocation = myLocationOverlay?.myLocation ?: GeoPoint(42.8746, 74.5698)

        lifecycleScope.launch {
            try {
                val route = routingEngine.buildDirectRouteOSRM(currentLocation, destination)

                if (route != null) {
                    // Рисуем маршрут
                    routeLine = Polyline(mapView).apply {
                        setPoints(route.points)
                        outlinePaint.color = Color.parseColor("#6B4EFF")
                        outlinePaint.strokeWidth = 12f
                    }
                    mapView.overlays.add(routeLine)
                    mapView.invalidate()

                    // Обновляем UI
                    txtDistance.text = String.format("%.1f км", route.distance / 1000)
                    txtTime.text = String.format("%.0f мин", route.duration / 60)

                    // Симуляция навигации
                    simulateNavigation()
                } else {
                    Toast.makeText(this@EscortActivity, "Не удалось построить маршрут", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@EscortActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addDestinationMarker() {
        val marker = Marker(mapView).apply {
            position = destination
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = txtDestination.text.toString()
        }
        mapView.overlays.add(marker)
    }

    private fun simulateNavigation() {
        // Симуляция пошаговой навигации
        val instructions = listOf(
            "Продолжайте прямо" to "200 м",
            "Поверните направо на ул. Ибраимова" to "150 м",
            "Продолжайте прямо" to "300 м",
            "Поверните налево на пр. Чуй" to "100 м",
            "Вы прибыли в пункт назначения" to "0 м"
        )

        var currentStep = 0
        val totalSteps = instructions.size

        // Обновляем прогресс
        progressRoute.max = 100
        progressRoute.progress = (currentStep * 100) / totalSteps

        // Показываем первую инструкцию
        txtInstruction.text = instructions[currentStep].first
        txtStepDistance.text = "через ${instructions[currentStep].second}"

        // Здесь должна быть реальная логика отслеживания GPS
        // Пока просто показываем инструкции
    }

    private fun shareLocation() {
        val message = "Я иду по маршруту в SafeWalk. Мое текущее местоположение: " +
                "https://maps.google.com/?q=${myLocationOverlay?.myLocation?.latitude},${myLocationOverlay?.myLocation?.longitude}"

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        startActivity(Intent.createChooser(shareIntent, "Поделиться местоположением"))
    }

    private fun triggerSOS() {
        MaterialAlertDialogBuilder(this)
            .setTitle("SOS")
            .setMessage("Вызвать экстренную помощь?")
            .setPositiveButton("Вызвать") { _, _ ->
                Toast.makeText(this, "SOS активирован!", Toast.LENGTH_SHORT).show()
                // Здесь должна быть логика вызова помощи
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showStopEscortDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Остановить сопровождение")
            .setMessage("Вы уверены, что хотите остановить сопровождение?")
            .setPositiveButton("Остановить") { _, _ ->
                finish()
            }
            .setNegativeButton("Продолжить", null)
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
