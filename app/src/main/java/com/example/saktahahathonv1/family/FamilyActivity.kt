package com.example.saktahahathonv1.family

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.saktahahathonv1.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import android.widget.TextView
import com.example.saktahahathonv1.tracking.MockTrackingProvider

class FamilyActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var recyclerChildren: RecyclerView
    private lateinit var layoutEmpty: View
    private lateinit var adapter: ChildrenAdapter
    private lateinit var toolbar: MaterialToolbar
    private lateinit var btnAddChild: MaterialButton
    private lateinit var fabAddSafeZone: FloatingActionButton

    private val children = mutableListOf<Child>()
    private val childMarkers = mutableMapOf<String, Marker>()
    private val safeZoneOverlays = mutableMapOf<String, Polygon>()
    private val trackingProvider by lazy { MockTrackingProvider(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        setContentView(R.layout.activity_family)

        initViews()
        setupMap()
        setupRecyclerView()
        setupListeners()
        loadMockData()
    }

    private fun initViews() {
        mapView = findViewById(R.id.mapView)
        recyclerChildren = findViewById(R.id.recyclerChildren)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        toolbar = findViewById(R.id.toolbar)
        btnAddChild = findViewById(R.id.btnAddChild)
        fabAddSafeZone = findViewById(R.id.fabAddSafeZone)
    }

    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(13.0)
        mapView.controller.setCenter(GeoPoint(42.8746, 74.5698)) // Бишкек
    }

    private fun setupRecyclerView() {
        adapter = ChildrenAdapter(
            children = children,
            onShowOnMap = { child ->
                child.location?.let { location ->
                    mapView.controller.animateTo(location)
                    mapView.controller.setZoom(16.0)
                }
            },
            onNotify = { child ->
                Toast.makeText(
                    this,
                    "Отправлено уведомление для ${child.name}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
        recyclerChildren.layoutManager = LinearLayoutManager(this)
        recyclerChildren.adapter = adapter
    }

    private fun setupListeners() {
        toolbar.setNavigationOnClickListener {
            finish()
        }

        btnAddChild.setOnClickListener {
            showAddChildDialog()
        }

        fabAddSafeZone.setOnClickListener {
            Toast.makeText(
                this,
                "Нажмите на карте, чтобы установить безопасную зону",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showAddChildDialog() {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_add_child, null)

        val editName = dialogView.findViewById<TextInputEditText>(R.id.editName)
        val editPhone = dialogView.findViewById<TextInputEditText>(R.id.editPhone)
        val seekbarRadius = dialogView.findViewById<SeekBar>(R.id.seekbarRadius)
        val txtRadius = dialogView.findViewById<TextView>(R.id.txtRadius)

        seekbarRadius.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                txtRadius.text = "${progress}м"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
            .apply {
                dialogView.findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener {
                    dismiss()
                }

                dialogView.findViewById<MaterialButton>(R.id.btnAdd).setOnClickListener {
                    val name = editName.text.toString()
                    val phone = editPhone.text.toString()
                    val radius = seekbarRadius.progress.toDouble()

                    if (name.isNotBlank() && phone.isNotBlank()) {
                        addChild(name, phone, radius)
                        dismiss()
                    } else {
                        Toast.makeText(
                            this@FamilyActivity,
                            "Заполните все поля",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                show()
            }
    }

    private fun addChild(name: String, phone: String, safeZoneRadius: Double) {
        val center = mapView.mapCenter as GeoPoint
        val child = Child(
            id = "child_${System.currentTimeMillis()}",
            name = name,
            phone = phone,
            location = center,
            safeZoneCenter = center,
            safeZoneRadius = safeZoneRadius,
            isInSafeZone = true
        )

        children.add(child)
        adapter.updateChildren(children)
        updateEmptyState()
        addChildToMap(child)

        Toast.makeText(this, "Ребенок добавлен", Toast.LENGTH_SHORT).show()
    }

    private fun addChildToMap(child: Child) {
        // Добавляем маркер ребенка
        child.location?.let { location ->
            val marker = Marker(mapView)
            marker.position = location
            marker.title = child.name
            marker.snippet = child.getStatusText()
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            mapView.overlays.add(marker)
            childMarkers[child.id] = marker
        }

        // Добавляем безопасную зону
        val safeZone = Polygon(mapView)
        safeZone.points = Polygon.pointsAsCircle(
            child.safeZoneCenter,
            child.safeZoneRadius
        )
        safeZone.fillPaint.color = Color.parseColor("#404CAF50")
        safeZone.outlinePaint.color = Color.parseColor("#4CAF50")
        safeZone.outlinePaint.strokeWidth = 3f
        mapView.overlays.add(safeZone)
        safeZoneOverlays[child.id] = safeZone

        mapView.invalidate()
    }

    private fun loadMockData() {
        val trackedChildren = trackingProvider.loadTrackedChildren()

        val mockChildren = trackedChildren.map { trackedChild ->
            trackedChild.toFamilyChild()
        }

        children.clear()
        children.addAll(mockChildren)
        adapter.updateChildren(children)
        updateEmptyState()

        // Добавляем детей на карту
        children.forEach { addChildToMap(it) }
    }

    private fun updateEmptyState() {
        if (children.isEmpty()) {
            recyclerChildren.visibility = View.GONE
            layoutEmpty.visibility = View.VISIBLE
        } else {
            recyclerChildren.visibility = View.VISIBLE
            layoutEmpty.visibility = View.GONE
        }
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
