package com.example.saktahahathonv1.location

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.example.saktahahathonv1.firebase.FirebaseEscortManager
import com.example.saktahahathonv1.notifications.NotificationHelper
import com.google.android.gms.location.*
import kotlinx.coroutines.*

/**
 * Foreground Service для отслеживания местоположения в режиме сопровождения
 * Работает в фоне и отправляет обновления в Firebase
 */
class LocationService : Service() {

    private val binder = LocalBinder()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var escortManager: FirebaseEscortManager

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isTracking = false

    // Listeners для внешних компонентов
    private var locationListener: ((Location) -> Unit)? = null

    companion object {
        const val NOTIFICATION_ID = 1002
        const val ACTION_START_TRACKING = "ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "ACTION_STOP_TRACKING"
        const val ACTION_UPDATE_OBSERVERS = "ACTION_UPDATE_OBSERVERS"

        private const val LOCATION_UPDATE_INTERVAL = 5000L // 5 секунд
        private const val LOCATION_FASTEST_INTERVAL = 3000L // минимум 3 секунды

        fun startTracking(context: Context) {
            val intent = Intent(context, LocationService::class.java).apply {
                action = ACTION_START_TRACKING
            }
            context.startForegroundService(intent)
        }

        fun stopTracking(context: Context) {
            val intent = Intent(context, LocationService::class.java).apply {
                action = ACTION_STOP_TRACKING
            }
            context.startService(intent)
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): LocationService = this@LocationService
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        notificationHelper = NotificationHelper(this)
        escortManager = FirebaseEscortManager.getInstance(this)
        setupLocationCallback()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> startLocationTracking()
            ACTION_STOP_TRACKING -> stopLocationTracking()
            ACTION_UPDATE_OBSERVERS -> {
                val count = intent.getIntExtra("observer_count", 0)
                updateNotification(count)
            }
        }
        return START_STICKY
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    handleLocationUpdate(location)
                }
            }
        }
    }

    private fun startLocationTracking() {
        if (isTracking) return

        // Проверка разрешений
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }

        // Запуск как Foreground Service
        val notification = notificationHelper.getLocationTrackingNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Настройка запросов локации
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_UPDATE_INTERVAL
        ).apply {
            setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL)
            setWaitForAccurateLocation(false)
        }.build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        isTracking = true
    }

    private fun stopLocationTracking() {
        if (!isTracking) {
            stopSelf()
            return
        }

        fusedLocationClient.removeLocationUpdates(locationCallback)
        isTracking = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleLocationUpdate(location: Location) {
        // Отправить в Firebase
        serviceScope.launch {
            escortManager.updateLocation(
                lat = location.latitude,
                lon = location.longitude,
                accuracy = location.accuracy,
                speed = location.speed,
                bearing = location.bearing
            )
        }

        // Уведомить локальных слушателей
        locationListener?.invoke(location)
    }

    private fun updateNotification(observerCount: Int) {
        notificationHelper.showEscortActiveNotification(observerCount)
    }

    /**
     * Установить слушатель для локальных обновлений (UI)
     */
    fun setLocationListener(listener: ((Location) -> Unit)?) {
        locationListener = listener
    }

    /**
     * Получить последнее известное местоположение
     */
    fun getLastLocation(callback: (Location?) -> Unit) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            callback(null)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            callback(location)
        }.addOnFailureListener {
            callback(null)
        }
    }

    /**
     * Проверить, идёт ли отслеживание
     */
    fun isCurrentlyTracking(): Boolean = isTracking

    override fun onDestroy() {
        super.onDestroy()
        if (isTracking) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        serviceScope.cancel()
    }
}
