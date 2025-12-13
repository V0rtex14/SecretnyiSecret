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

    // Демо данные детей
    private val children = listOf(
        Child("Алина", "дочь", "+996 555 777 888", GeoPoint(42.8750, 74.5720)),
        Child("Темирлан", "сын", "+996 555 999 000", GeoPoint(42.8700, 74.5650))
    )
    private var currentChildIndex = 0

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

        val child = children[currentChildIndex]
        mapView.controller.setZoom(15.0)
        mapView.controller.setCenter(child.location)
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

        updateChildInfo()
    }

    private fun showChildSelector() {
        val names = children.map { "${it.name} (${it.relationship})" }.toTypedArray()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Выберите ребенка")
            .setItems(names) { _, which ->
                currentChildIndex = which
                updateChildInfo()
                mapView.controller.setCenter(children[which].location)
            }
            .show()
    }

    private fun updateChildInfo() {
        val child = children[currentChildIndex]
        txtSelectedChild.text = "${child.name} (${child.relationship})"
    }

    private fun callChild() {
        val child = children[currentChildIndex]
        val intent = Intent(Intent.ACTION_DIAL)
        intent.data = Uri.parse("tel:${child.phone}")
        startActivity(intent)
    }

    private fun messageChild() {
        val child = children[currentChildIndex]
        val intent = Intent(Intent.ACTION_SENDTO)
        intent.data = Uri.parse("smsto:${child.phone}")
        intent.putExtra("sms_body", "Привет! Где ты сейчас?")
        startActivity(intent)
    }

    private fun startLocationUpdates() {
        val updateRunnable = object : Runnable {
            override fun run() {
                // Симуляция обновления местоположения
                val minutes = (1..5).random()
                txtLastUpdate.text = "Обновлено: $minutes мин назад"

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

    data class Child(
        val name: String,
        val relationship: String,
        val phone: String,
        val location: GeoPoint
    )
}
