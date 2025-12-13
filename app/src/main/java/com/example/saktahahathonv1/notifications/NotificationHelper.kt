package com.example.saktahahathonv1.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.saktahahathonv1.MainActivity
import com.example.saktahahathonv1.R
import com.example.saktahahathonv1.sos.SosActivity

/**
 * Централизованное управление уведомлениями приложения Sakta
 * Поддерживает каналы для SOS, Escort Mode и общих уведомлений
 */
class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ESCORT = "sakta_escort_channel"
        const val CHANNEL_SOS = "sakta_sos_channel"
        const val CHANNEL_GENERAL = "sakta_general_channel"
        const val CHANNEL_LOCATION = "sakta_location_channel"

        const val NOTIFICATION_ID_ESCORT = 1001
        const val NOTIFICATION_ID_SOS = 999
        const val NOTIFICATION_ID_LOCATION = 1002
        const val NOTIFICATION_ID_ROUTE = 1003
    }

    init {
        createNotificationChannels()
    }

    /**
     * Создание всех каналов уведомлений (Android 8.0+)
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_SOS,
                    "Экстренные уведомления",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Уведомления SOS и экстренных ситуаций"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                    setShowBadge(true)
                    enableLights(true)
                    lightColor = 0xFFFF3B30.toInt()
                },

                NotificationChannel(
                    CHANNEL_ESCORT,
                    "Режим сопровождения",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Уведомления о статусе сопровождения"
                    enableVibration(true)
                    setShowBadge(true)
                },

                NotificationChannel(
                    CHANNEL_LOCATION,
                    "Отслеживание местоположения",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Фоновое отслеживание для режима сопровождения"
                    setShowBadge(false)
                },

                NotificationChannel(
                    CHANNEL_GENERAL,
                    "Общие уведомления",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Информационные уведомления приложения"
                }
            )

            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannels(channels)
        }
    }

    /**
     * Показать уведомление SOS (высокий приоритет, нельзя смахнуть)
     */
    fun showSosNotification(latitude: Double? = null, longitude: Double? = null) {
        val intent = Intent(context, SosActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val locationText = if (latitude != null && longitude != null) {
            "\nКоординаты: %.4f, %.4f".format(latitude, longitude)
        } else ""

        val notification = NotificationCompat.Builder(context, CHANNEL_SOS)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle("🆘 SOS АКТИВИРОВАН")
            .setContentText("Местоположение отправлено контактам$locationText")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Экстренный сигнал отправлен вашим доверенным контактам.$locationText\n\nНажмите для отмены или дополнительных действий."))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .setColor(0xFFFF3B30.toInt())
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        notifyIfPermitted(NOTIFICATION_ID_SOS, notification)
    }

    /**
     * Отменить SOS уведомление
     */
    fun cancelSosNotification() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_SOS)
    }

    /**
     * Показать уведомление режима сопровождения (для владельца)
     */
    fun showEscortActiveNotification(observerCount: Int = 0) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val observerText = if (observerCount > 0) {
            "За вами наблюдают: $observerCount чел."
        } else {
            "Ожидание подключения наблюдателей..."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ESCORT)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("Режим сопровождения активен")
            .setContentText(observerText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setColor(0xFF5B4CFF.toInt())
            .build()

        notifyIfPermitted(NOTIFICATION_ID_ESCORT, notification)
    }

    /**
     * Показать уведомление для наблюдателя (следит за другим пользователем)
     */
    fun showEscortObserverNotification(userName: String, status: String = "в безопасности") {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ESCORT)
            .setSmallIcon(R.drawable.ic_tracking)
            .setContentTitle("Вы наблюдаете за $userName")
            .setContentText("Статус: $status")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setColor(0xFF34C759.toInt())
            .build()

        notifyIfPermitted(NOTIFICATION_ID_ESCORT, notification)
    }

    /**
     * Отменить уведомление сопровождения
     */
    fun cancelEscortNotification() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_ESCORT)
    }

    /**
     * Уведомление фонового отслеживания локации (для Foreground Service)
     */
    fun getLocationTrackingNotification(): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_LOCATION)
            .setSmallIcon(R.drawable.ic_location_small)
            .setContentTitle("Sakta отслеживает маршрут")
            .setContentText("Ваше местоположение передаётся наблюдателям")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setColor(0xFF5B4CFF.toInt())
            .build()
    }

    /**
     * Показать уведомление о завершении маршрута
     */
    fun showRouteCompletedNotification(fromAddress: String, toAddress: String) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_GENERAL)
            .setSmallIcon(R.drawable.ic_check_circle)
            .setContentTitle("Маршрут завершён!")
            .setContentText("$fromAddress → $toAddress")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(0xFF34C759.toInt())
            .build()

        notifyIfPermitted(NOTIFICATION_ID_ROUTE, notification)
    }

    /**
     * Показать общее информационное уведомление
     */
    fun showGeneralNotification(title: String, message: String, notificationId: Int = System.currentTimeMillis().toInt()) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_GENERAL)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(0xFF5B4CFF.toInt())
            .build()

        notifyIfPermitted(notificationId, notification)
    }

    /**
     * Проверить разрешение и показать уведомление
     */
    private fun notifyIfPermitted(notificationId: Int, notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
                NotificationManagerCompat.from(context).notify(notificationId, notification)
            }
        } else {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    /**
     * Проверить, включены ли уведомления
     */
    fun areNotificationsEnabled(): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
