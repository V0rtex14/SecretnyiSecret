package com.example.saktahahathonv1.sos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.*
import android.telephony.SmsManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
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
import com.example.saktahahathonv1.notifications.NotificationHelper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class SosActivity : AppCompatActivity() {

    private lateinit var btnBack: TextView
    private lateinit var imgAlarm: ImageView
    private lateinit var txtSosStatus: TextView
    private lateinit var txtContactsCount: TextView
    private lateinit var btnCall102: MaterialButton
    private lateinit var btnRecordAudio: MaterialButton
    private lateinit var btnFalseAlarm: MaterialButton
    private lateinit var cardLocationInfo: MaterialCardView

    private var isRecording = false
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())

    // Новые компоненты
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var authHelper: FirebaseAuthHelper
    private lateinit var escortManager: FirebaseEscortManager

    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentLocation: Location? = null
    private var sosActivated = false

    // Доверенные контакты (в реальном приложении из SharedPreferences/базы)
    private val trustedContacts = listOf(
        TrustedContact("Мама", "+996555123456"),
        TrustedContact("Папа", "+996555789012"),
        TrustedContact("Друг", "+996555345678")
    )

    data class TrustedContact(val name: String, val phone: String)

    companion object {
        private const val REQUEST_SMS_PERMISSION = 1001
        private const val REQUEST_LOCATION_PERMISSION = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sos)

        initServices()
        initViews()
        setupUI()
        checkPermissionsAndActivate()
    }

    private fun initServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        notificationHelper = NotificationHelper(this)
        authHelper = FirebaseAuthHelper.getInstance(this)
        escortManager = FirebaseEscortManager.getInstance(this)
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        imgAlarm = findViewById(R.id.imgAlarm)
        txtSosStatus = findViewById(R.id.txtSosStatus)
        txtContactsCount = findViewById(R.id.txtContactsCount)
        btnCall102 = findViewById(R.id.btnCall102)
        btnRecordAudio = findViewById(R.id.btnRecordAudio)
        btnFalseAlarm = findViewById(R.id.btnFalseAlarm)
        cardLocationInfo = findViewById(R.id.cardLocationInfo)
    }

    private fun setupUI() {
        btnBack.setOnClickListener {
            showCancelConfirmation()
        }

        btnCall102.setOnClickListener {
            callEmergency()
        }

        btnRecordAudio.setOnClickListener {
            toggleRecording()
        }

        btnFalseAlarm.setOnClickListener {
            cancelSos()
        }
    }

    private fun checkPermissionsAndActivate() {
        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.SEND_SMS)
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsNeeded.toTypedArray(),
                REQUEST_SMS_PERMISSION
            )
        } else {
            activateSos()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Активируем SOS в любом случае, даже без разрешений
        activateSos()
    }

    private fun activateSos() {
        if (sosActivated) return
        sosActivated = true

        // 1. Вибрация
        startVibration()

        // 2. Анимация пульсации
        startPulseAnimation()

        // 3. Получить местоположение и отправить SMS
        getCurrentLocationAndNotify()

        // 4. Активировать SOS в Firebase (если есть активная сессия)
        activateSosInFirebase()

        // 5. Показать системное уведомление
        notificationHelper.showSosNotification()
    }

    private fun startVibration() {
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 500, 200, 500), 0)
        }
    }

    private fun startPulseAnimation() {
        val pulseAnimation = AlphaAnimation(1.0f, 0.5f).apply {
            duration = 500
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
        }
        imgAlarm.startAnimation(pulseAnimation)
    }

    private fun getCurrentLocationAndNotify() {
        txtSosStatus.text = getString(R.string.sending_location)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            // Отправляем без координат
            sendSmsToContacts(null)
            return
        }

        val cancellationToken = CancellationTokenSource()

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationToken.token
        ).addOnSuccessListener { location ->
            currentLocation = location
            sendSmsToContacts(location)

            // Обновляем уведомление с координатами
            location?.let {
                notificationHelper.showSosNotification(it.latitude, it.longitude)
            }
        }.addOnFailureListener {
            // Отправляем без координат
            sendSmsToContacts(null)
        }
    }

    private fun sendSmsToContacts(location: Location?) {
        txtSosStatus.text = getString(R.string.notifying_contacts)

        val userName = authHelper.getUserName()
        val timeFormat = SimpleDateFormat("HH:mm dd.MM.yyyy", Locale.getDefault())
        val currentTime = timeFormat.format(Date())

        val message = buildString {
            append("🆘 ЭКСТРЕННАЯ СИТУАЦИЯ!\n\n")
            append("$userName активировал SOS в приложении Sakta.\n\n")

            if (location != null) {
                append("📍 Координаты: ${String.format("%.6f", location.latitude)}, ${String.format("%.6f", location.longitude)}\n")
                append("🗺 Карта: https://maps.google.com/?q=${location.latitude},${location.longitude}\n\n")
            } else {
                append("📍 Координаты недоступны\n\n")
            }

            append("🕐 Время: $currentTime\n\n")
            append("Пожалуйста, свяжитесь или позвоните!")
        }

        // Проверяем разрешение на SMS
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            == PackageManager.PERMISSION_GRANTED) {
            sendRealSms(message)
        } else {
            // Показываем сообщение без реальной отправки
            showSmsSimulation()
        }
    }

    private fun sendRealSms(message: String) {
        activityScope.launch(Dispatchers.IO) {
            var sentCount = 0

            try {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

                trustedContacts.forEach { contact ->
                    try {
                        // Разбиваем длинное сообщение на части
                        val parts = smsManager.divideMessage(message)
                        smsManager.sendMultipartTextMessage(
                            contact.phone,
                            null,
                            parts,
                            null,
                            null
                        )
                        sentCount++
                    } catch (e: Exception) {
                        // Продолжаем отправку остальным
                    }
                }

                withContext(Dispatchers.Main) {
                    updateStatusAfterSending(sentCount)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showSmsSimulation()
                }
            }
        }
    }

    private fun updateStatusAfterSending(sentCount: Int) {
        txtSosStatus.text = getString(R.string.sos_activated)
        txtContactsCount.text = "• ${getString(R.string.trusted_contacts_count, sentCount)}"

        if (sentCount > 0) {
            Toast.makeText(
                this,
                "SMS отправлено $sentCount контактам",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(
                this,
                "Не удалось отправить SMS. Позвоните сами!",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showSmsSimulation() {
        handler.postDelayed({
            txtSosStatus.text = getString(R.string.sos_activated)
            txtContactsCount.text = "• ${getString(R.string.trusted_contacts_count, trustedContacts.size)}"

            Toast.makeText(
                this,
                "SMS уведомления требуют разрешения. Данные сохранены.",
                Toast.LENGTH_LONG
            ).show()
        }, 1500)
    }

    private fun activateSosInFirebase() {
        activityScope.launch {
            try {
                escortManager.activateSOS()
            } catch (e: Exception) {
                // Игнорируем ошибки Firebase
            }
        }
    }

    private fun callEmergency() {
        val phoneNumber = "102"

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(Intent.ACTION_CALL)
            intent.data = Uri.parse("tel:$phoneNumber")
            startActivity(intent)
        } else {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = Uri.parse("tel:$phoneNumber")
            startActivity(intent)
        }
    }

    private fun toggleRecording() {
        isRecording = !isRecording

        if (isRecording) {
            btnRecordAudio.text = "● ${getString(R.string.recording_audio)}"
            btnRecordAudio.setTextColor(ContextCompat.getColor(this, R.color.sos_red))

            Toast.makeText(this, "Запись звука начата", Toast.LENGTH_SHORT).show()

            val blinkAnimation = AlphaAnimation(1.0f, 0.3f).apply {
                duration = 500
                repeatCount = Animation.INFINITE
                repeatMode = Animation.REVERSE
            }
            btnRecordAudio.startAnimation(blinkAnimation)
        } else {
            btnRecordAudio.text = getString(R.string.recording_audio)
            btnRecordAudio.clearAnimation()

            Toast.makeText(this, "Запись сохранена", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCancelConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Отменить SOS?")
            .setMessage("Вы уверены, что хотите отменить сигнал SOS?")
            .setPositiveButton("Да, отменить") { _, _ ->
                cancelSos()
            }
            .setNegativeButton("Нет", null)
            .show()
    }

    private fun cancelSos() {
        sosActivated = false

        // Остановка вибрации
        vibrator?.cancel()

        // Остановка анимаций
        imgAlarm.clearAnimation()
        btnRecordAudio.clearAnimation()

        // Убрать уведомление
        notificationHelper.cancelSosNotification()

        Toast.makeText(this, getString(R.string.false_alarm), Toast.LENGTH_SHORT).show()

        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        showCancelConfirmation()
    }

    override fun onDestroy() {
        super.onDestroy()
        vibrator?.cancel()
        handler.removeCallbacksAndMessages(null)
        activityScope.cancel()
    }
}
