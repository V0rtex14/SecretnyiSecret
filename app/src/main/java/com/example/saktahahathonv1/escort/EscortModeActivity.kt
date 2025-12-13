package com.example.saktahahathonv1.escort

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.saktahahathonv1.R
import com.example.saktahahathonv1.firebase.FirebaseAuthHelper
import com.example.saktahahathonv1.firebase.FirebaseEscortManager
import com.example.saktahahathonv1.location.LocationService
import com.example.saktahahathonv1.notifications.NotificationHelper
import com.example.saktahahathonv1.sos.SosActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.*

class EscortModeActivity : AppCompatActivity() {

    private lateinit var btnBack: TextView
    private lateinit var imgShield: ImageView
    private lateinit var txtEscortTitle: TextView
    private lateinit var txtEscortSubtitle: TextView
    private lateinit var txtDistanceToGoal: TextView
    private lateinit var txtCurrentZone: TextView
    private lateinit var txtSessionCode: TextView
    private lateinit var cardStatus: MaterialCardView
    private lateinit var btnFinishEscort: MaterialButton
    private lateinit var btnSos: MaterialButton
    private lateinit var btnShareCode: MaterialButton

    private val handler = Handler(Looper.getMainLooper())
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Firebase components
    private lateinit var escortManager: FirebaseEscortManager
    private lateinit var authHelper: FirebaseAuthHelper
    private lateinit var notificationHelper: NotificationHelper

    // State
    private var isEscortActive = false
    private var isOwner = true // true = создатель, false = наблюдатель
    private var currentSessionId: String? = null
    private var distanceRemaining = 1.2

    // Location Service binding
    private var locationService: LocationService? = null
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as LocationService.LocalBinder
            locationService = binder.getService()
            isServiceBound = true

            // Устанавливаем listener для локальных обновлений
            locationService?.setLocationListener { location ->
                // Обновляем UI с новой локацией
                handler.post {
                    updateLocationUI(location.latitude, location.longitude)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            locationService = null
            isServiceBound = false
        }
    }

    companion object {
        private const val REQUEST_LOCATION_PERMISSION = 1001
        const val EXTRA_MODE = "escort_mode"
        const val MODE_CREATE = "create"
        const val MODE_JOIN = "join"
        const val EXTRA_SESSION_ID = "session_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_escort_mode)

        initServices()
        initViews()
        setupUI()

        // Определяем режим (создание или присоединение)
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_CREATE
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)

        if (mode == MODE_JOIN && sessionId != null) {
            isOwner = false
            joinExistingSession(sessionId)
        } else {
            isOwner = true
            showModeSelectionDialog()
        }
    }

    private fun initServices() {
        escortManager = FirebaseEscortManager.getInstance(this)
        authHelper = FirebaseAuthHelper.getInstance(this)
        notificationHelper = NotificationHelper(this)
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        imgShield = findViewById(R.id.imgShield)
        txtEscortTitle = findViewById(R.id.txtEscortTitle)
        txtEscortSubtitle = findViewById(R.id.txtEscortSubtitle)
        txtDistanceToGoal = findViewById(R.id.txtDistanceToGoal)
        txtCurrentZone = findViewById(R.id.txtCurrentZone)
        cardStatus = findViewById(R.id.cardStatus)
        btnFinishEscort = findViewById(R.id.btnFinishEscort)
        btnSos = findViewById(R.id.btnSos)

        // Инициализируем дополнительные view
        txtSessionCode = TextView(this).apply { visibility = View.GONE }
        btnShareCode = MaterialButton(this).apply { visibility = View.GONE }
    }

    private fun setupUI() {
        btnBack.setOnClickListener {
            showFinishConfirmation()
        }

        btnFinishEscort.setOnClickListener {
            showFinishConfirmation()
        }

        btnSos.setOnClickListener {
            // Активируем SOS в Firebase тоже
            activityScope.launch {
                escortManager.activateSOS()
            }
            startActivity(Intent(this, SosActivity::class.java))
        }
    }

    private fun showModeSelectionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Режим сопровождения")
            .setMessage("Выберите действие:")
            .setPositiveButton("Начать новое") { _, _ ->
                checkPermissionsAndStart()
            }
            .setNegativeButton("Присоединиться") { _, _ ->
                showJoinSessionDialog()
            }
            .setNeutralButton("Отмена") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun showJoinSessionDialog() {
        val input = EditText(this).apply {
            hint = "Введите код сессии"
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }

        AlertDialog.Builder(this)
            .setTitle("Присоединиться к сессии")
            .setView(input)
            .setPositiveButton("Присоединиться") { _, _ ->
                val code = input.text.toString().trim().uppercase()
                if (code.length >= 6) {
                    isOwner = false
                    joinExistingSession(code)
                } else {
                    Toast.makeText(this, "Неверный код сессии", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton("Отмена") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun checkPermissionsAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ),
                REQUEST_LOCATION_PERMISSION
            )
        } else {
            startNewEscortSession()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startNewEscortSession()
            } else {
                Toast.makeText(this, "Требуется разрешение на геолокацию", Toast.LENGTH_LONG).show()
                // Всё равно запускаем, но без реального трекинга
                startNewEscortSession()
            }
        }
    }

    private fun startNewEscortSession() {
        activityScope.launch {
            try {
                // Создаём сессию в Firebase
                val result = escortManager.startEscortSession()

                result.onSuccess { sessionId ->
                    currentSessionId = sessionId
                    isEscortActive = true

                    runOnUiThread {
                        showSessionStarted(sessionId)
                        startEscortUI()
                    }

                    // Запускаем Location Service
                    startLocationTracking()

                    // Показываем уведомление
                    notificationHelper.showEscortActiveNotification(0)
                }

                result.onFailure { error ->
                    runOnUiThread {
                        Toast.makeText(
                            this@EscortModeActivity,
                            "Ошибка создания сессии: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        // Запускаем в демо-режиме
                        startDemoMode()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    startDemoMode()
                }
            }
        }
    }

    private fun joinExistingSession(sessionId: String) {
        activityScope.launch {
            try {
                val result = escortManager.joinAsObserver(sessionId)

                result.onSuccess { session ->
                    currentSessionId = sessionId
                    isEscortActive = true

                    runOnUiThread {
                        showObserverMode(session.ownerName)
                    }

                    // Подписываемся на обновления локации
                    subscribeToLocationUpdates(sessionId)

                    // Подписываемся на изменения статуса
                    subscribeToStatusUpdates(sessionId)
                }

                result.onFailure { error ->
                    runOnUiThread {
                        Toast.makeText(
                            this@EscortModeActivity,
                            "Ошибка: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@EscortModeActivity, "Сессия не найдена", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun showSessionStarted(sessionId: String) {
        txtEscortTitle.text = "Сопровождение активно"
        txtEscortSubtitle.text = "Код для наблюдателей: $sessionId"

        // Показываем диалог с кодом
        AlertDialog.Builder(this)
            .setTitle("Сессия создана!")
            .setMessage("Код сессии: $sessionId\n\nОтправьте этот код доверенным контактам, чтобы они могли следить за вашим маршрутом.")
            .setPositiveButton("Скопировать код") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Sakta Session", sessionId)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Код скопирован!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("OK", null)
            .show()
    }

    private fun showObserverMode(ownerName: String) {
        txtEscortTitle.text = "Наблюдение за $ownerName"
        txtEscortSubtitle.text = "Отслеживание в реальном времени"
        btnFinishEscort.text = "Перестать наблюдать"
        btnSos.visibility = View.GONE

        // Показываем уведомление наблюдателя
        notificationHelper.showEscortObserverNotification(ownerName)

        startEscortUI()
    }

    private fun subscribeToLocationUpdates(sessionId: String) {
        escortManager.observeLocationWithCallback(
            sessionId,
            onLocation = { location ->
                runOnUiThread {
                    updateLocationUI(location.lat, location.lon)
                }
            },
            onError = { error ->
                runOnUiThread {
                    Toast.makeText(this, "Ошибка получения локации", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun subscribeToStatusUpdates(sessionId: String) {
        escortManager.observeStatusWithCallback(
            sessionId,
            onStatus = { status ->
                runOnUiThread {
                    when (status) {
                        FirebaseEscortManager.STATUS_SOS -> {
                            showSosAlert()
                        }
                        FirebaseEscortManager.STATUS_COMPLETED -> {
                            showSessionCompleted()
                        }
                    }
                }
            }
        )
    }

    private fun showSosAlert() {
        updateZoneStatus("danger")
        txtEscortTitle.text = "🆘 SOS АКТИВИРОВАН!"

        AlertDialog.Builder(this)
            .setTitle("🆘 ЭКСТРЕННАЯ СИТУАЦИЯ!")
            .setMessage("Пользователь активировал сигнал SOS!\n\nПозвоните ему или вызовите помощь.")
            .setPositiveButton("Позвонить") { _, _ ->
                // TODO: Позвонить пользователю
            }
            .setNegativeButton("OK", null)
            .setCancelable(false)
            .show()
    }

    private fun showSessionCompleted() {
        isEscortActive = false
        imgShield.clearAnimation()

        txtEscortTitle.text = "Сопровождение завершено"
        txtEscortSubtitle.text = "Пользователь благополучно добрался"

        handler.postDelayed({
            finish()
        }, 3000)
    }

    private fun startLocationTracking() {
        // Биндим сервис
        val intent = Intent(this, LocationService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        // Запускаем foreground service
        LocationService.startTracking(this)
    }

    private fun stopLocationTracking() {
        if (isServiceBound) {
            locationService?.setLocationListener(null)
            unbindService(serviceConnection)
            isServiceBound = false
        }
        LocationService.stopTracking(this)
    }

    private fun updateLocationUI(lat: Double, lon: Double) {
        // Простое обновление UI - можно расширить
        if (lat != 0.0 && lon != 0.0) {
            txtDistanceToGoal.text = "Координаты: ${String.format("%.4f", lat)}, ${String.format("%.4f", lon)}"
        }
    }

    private fun startDemoMode() {
        isEscortActive = true
        currentSessionId = "DEMO"

        txtEscortTitle.text = "Сопровождение (демо)"
        txtEscortSubtitle.text = "Офлайн режим"

        startEscortUI()
        startDistanceUpdates()

        Toast.makeText(this, "Работает в демо-режиме (без Firebase)", Toast.LENGTH_LONG).show()
    }

    private fun startEscortUI() {
        // Анимация пульсации щита
        val pulseAnimation = AlphaAnimation(1.0f, 0.7f).apply {
            duration = 1000
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
        }
        imgShield.startAnimation(pulseAnimation)

        updateZoneStatus("safe")
    }

    private fun startDistanceUpdates() {
        val updateRunnable = object : Runnable {
            override fun run() {
                if (isEscortActive && distanceRemaining > 0) {
                    distanceRemaining -= 0.05
                    if (distanceRemaining < 0) distanceRemaining = 0.0

                    txtDistanceToGoal.text = "До цели: ${String.format("%.1f", distanceRemaining)} км"

                    if ((0..10).random() > 8) {
                        updateZoneStatus("warning")
                        handler.postDelayed({
                            if (isEscortActive) updateZoneStatus("safe")
                        }, 3000)
                    }

                    if (distanceRemaining <= 0) {
                        onArrived()
                    } else {
                        handler.postDelayed(this, 2000)
                    }
                }
            }
        }

        handler.postDelayed(updateRunnable, 2000)
    }

    private fun updateZoneStatus(status: String) {
        when (status) {
            "safe" -> {
                txtCurrentZone.text = "Текущая зона: Безопасно"
                txtCurrentZone.setTextColor(ContextCompat.getColor(this, R.color.success))
                cardStatus.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_safe_bg))
                cardStatus.strokeColor = ContextCompat.getColor(this, R.color.card_safe_border)
            }
            "warning" -> {
                txtCurrentZone.text = "Текущая зона: Средний риск"
                txtCurrentZone.setTextColor(ContextCompat.getColor(this, R.color.warning))
                cardStatus.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_warning_bg))
                cardStatus.strokeColor = ContextCompat.getColor(this, R.color.card_warning_border)

                Toast.makeText(this, "Внимание! Вы входите в зону повышенного риска", Toast.LENGTH_SHORT).show()
            }
            "danger" -> {
                txtCurrentZone.text = "Текущая зона: Опасно!"
                txtCurrentZone.setTextColor(ContextCompat.getColor(this, R.color.error))
                cardStatus.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_danger_bg))
                cardStatus.strokeColor = ContextCompat.getColor(this, R.color.card_danger_border)
            }
        }
    }

    private fun onArrived() {
        isEscortActive = false
        imgShield.clearAnimation()

        txtEscortTitle.text = getString(R.string.escort_arrived)
        txtEscortSubtitle.text = "Вы благополучно добрались"
        txtDistanceToGoal.text = "Прибыли!"

        // Завершаем сессию в Firebase
        activityScope.launch {
            escortManager.endEscortSession()
        }

        // Показываем уведомление
        notificationHelper.showRouteCompletedNotification("Начало", "Конец")
        notificationHelper.cancelEscortNotification()

        Toast.makeText(this, "Вы прибыли! Контакты уведомлены.", Toast.LENGTH_LONG).show()

        handler.postDelayed({
            finish()
        }, 3000)
    }

    private fun showFinishConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Завершить сопровождение?")
            .setMessage("Вы уверены, что хотите завершить режим сопровождения?")
            .setPositiveButton("Да, завершить") { _, _ ->
                finishEscort()
            }
            .setNegativeButton("Нет", null)
            .show()
    }

    private fun finishEscort() {
        isEscortActive = false
        imgShield.clearAnimation()

        // Завершаем сессию в Firebase
        activityScope.launch {
            if (isOwner) {
                escortManager.endEscortSession()
            } else {
                escortManager.leaveAsObserver()
            }
        }

        // Останавливаем трекинг
        if (isOwner) {
            stopLocationTracking()
        }

        // Убираем уведомления
        notificationHelper.cancelEscortNotification()

        Toast.makeText(this, "Сопровождение завершено", Toast.LENGTH_SHORT).show()
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        showFinishConfirmation()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        activityScope.cancel()
        escortManager.cleanup()

        if (isServiceBound) {
            unbindService(serviceConnection)
        }
    }
}
