package com.example.saktahahathonv1.map

import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.MapView
import android.graphics.Color
import kotlinx.coroutines.*
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.math.atan2

/**
 * Система отслеживания друзей
 * Показывает их маршрут, текущее положение и уровень безопасности
 */
class FriendTracker(
    private val mapView: MapView,
    private val riskEngine: RiskEngine,
    private val coroutineScope: CoroutineScope
) {

    private var friendMarker: Marker? = null
    private var friendRoute: Polyline? = null
    private var friendJob: Job? = null

    // Демо друг
    private var demoFriend: Friend? = null
    private var currentRouteIndex = 0

    /**
     * Запустить демо отслеживание друга
     */
    fun startDemoFriendTracking(
        friendName: String,
        startPoint: GeoPoint,
        endPoint: GeoPoint,
        route: List<GeoPoint>
    ) {
        stopTracking()

        demoFriend = Friend(
            id = "demo_friend",
            name = friendName,
            currentLocation = startPoint,
            route = route,
            destination = endPoint,
            status = FriendStatus.IN_TRANSIT
        )

        // Отображаем маршрут друга
        displayFriendRoute(route)

        // Запускаем анимацию движения
        friendJob = coroutineScope.launch {
            animateFriendMovement()
        }
    }

    /**
     * Отображение маршрута друга на карте
     */
    private fun displayFriendRoute(route: List<GeoPoint>) {
        // Удаляем старый маршрут
        friendRoute?.let { mapView.overlays.remove(it) }

        // Рисуем маршрут друга (фиолетовая линия)
        friendRoute = Polyline(mapView).apply {
            setPoints(route)
            outlinePaint.color = Color.argb(180, 156, 39, 176) // Фиолетовый
            outlinePaint.strokeWidth = 10f
            title = "Маршрут друга"
        }

        mapView.overlays.add(friendRoute)
        mapView.invalidate()
    }

    /**
     * Анимация движения друга по маршруту
     */
    private suspend fun animateFriendMovement() {
        val friend = demoFriend ?: return

        currentRouteIndex = 0

        while (currentRouteIndex < friend.route.size) {
            val currentPoint = friend.route[currentRouteIndex]

            // Обновляем позицию друга
            friend.currentLocation = currentPoint

            // Обновляем маркер на карте
            updateFriendMarker(currentPoint)

            // Проверяем безопасность текущего участка
            checkFriendSafety(currentPoint)

            currentRouteIndex++

            // Задержка между шагами (симуляция движения)
            delay(2000) // 2 секунды между точками
        }

        // Друг прибыл
        friend.status = FriendStatus.ARRIVED
        updateFriendMarker(friend.destination)
    }

    /**
     * Обновление маркера друга на карте
     */
    private fun updateFriendMarker(location: GeoPoint) {
        val friend = demoFriend ?: return

        // Удаляем старый маркер
        friendMarker?.let { mapView.overlays.remove(it) }

        // Создаём новый маркер
        friendMarker = Marker(mapView).apply {
            position = location
            title = friend.name

            // Информация о друге
            snippet = buildString {
                append("Статус: ${getStatusText(friend.status)}\n")

                if (friend.status == FriendStatus.IN_TRANSIT) {
                    val remaining = friend.route.size - currentRouteIndex
                    val progress = (currentRouteIndex.toFloat() / friend.route.size * 100).toInt()

                    append("Прогресс: $progress%\n")
                    append("Осталось точек: $remaining\n")

                    // Уровень безопасности
                    val risk = riskEngine.riskAtPoint(location)
                    val safety = when {
                        risk < 0.5 -> "✅ Безопасно"
                        risk < 1.5 -> "⚠️ Средний риск"
                        else -> "⛔ Опасная зона!"
                    }
                    append("Безопасность: $safety")
                }
            }

            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

            // Цвет маркера по статусу
            icon = createFriendMarkerIcon(friend.status)
        }

        mapView.overlays.add(friendMarker)
        mapView.invalidate()
    }

    /**
     * Проверка безопасности текущего участка
     */
    private fun checkFriendSafety(location: GeoPoint) {
        val risk = riskEngine.riskAtPoint(location)

        if (risk >= 1.5) {
            // Высокий риск - можно отправить уведомление
            onFriendInDanger(location, risk)
        }
    }

    /**
     * Обработка опасной зоны
     */
    private fun onFriendInDanger(location: GeoPoint, risk: Double) {
        // TODO: Отправить push-уведомление
        // TODO: Предложить альтернативный маршрут

        println("⚠️ Друг в опасной зоне! Риск: $risk")
    }

    /**
     * Получить статистику маршрута друга
     */
    fun getFriendRouteStatistics(): FriendRouteStats? {
        val friend = demoFriend ?: return null

        val totalPoints = friend.route.size
        val completedPoints = currentRouteIndex
        val remainingPoints = totalPoints - completedPoints

        // Оценка риска пройденного пути
        val completedRoute = friend.route.take(completedPoints)
        val evaluation = if (completedRoute.isNotEmpty()) {
            riskEngine.evaluateRoute(completedRoute)
        } else {
            null
        }

        // Оценка риска оставшегося пути
        val remainingRoute = friend.route.drop(currentRouteIndex)
        val remainingEval = if (remainingRoute.isNotEmpty()) {
            riskEngine.evaluateRoute(remainingRoute)
        } else {
            null
        }

        return FriendRouteStats(
            friendName = friend.name,
            progress = (completedPoints.toFloat() / totalPoints * 100).toInt(),
            completedDistance = calculateRouteDistance(completedRoute),
            remainingDistance = calculateRouteDistance(remainingRoute),
            averageRiskCompleted = evaluation?.averageRisk ?: 0.0,
            averageRiskRemaining = remainingEval?.averageRisk ?: 0.0,
            currentStatus = friend.status,
            currentRisk = riskEngine.riskAtPoint(friend.currentLocation)
        )
    }

    /**
     * Остановить отслеживание
     */
    fun stopTracking() {
        friendJob?.cancel()
        friendMarker?.let { mapView.overlays.remove(it) }
        friendRoute?.let { mapView.overlays.remove(it) }
        friendMarker = null
        friendRoute = null
        demoFriend = null
        currentRouteIndex = 0
        mapView.invalidate()
    }

    // ===== УТИЛИТЫ =====

    private fun createFriendMarkerIcon(status: FriendStatus): android.graphics.drawable.Drawable {
        val color = when (status) {
            FriendStatus.IN_TRANSIT -> Color.rgb(156, 39, 176) // Фиолетовый
            FriendStatus.ARRIVED -> Color.rgb(76, 175, 80)     // Зелёный
            FriendStatus.STOPPED -> Color.rgb(255, 152, 0)     // Оранжевый
            FriendStatus.DANGER -> Color.rgb(244, 67, 54)      // Красный
        }

        return createCircleDrawable(color, 20)
    }

    private fun createCircleDrawable(color: Int, sizeDp: Int): android.graphics.drawable.Drawable {
        val context = mapView.context
        val sizePx = (sizeDp * context.resources.displayMetrics.density).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(
            sizePx, sizePx,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)

        val paint = android.graphics.Paint().apply {
            this.color = color
            isAntiAlias = true
        }

        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)

        // Белая обводка
        paint.style = android.graphics.Paint.Style.STROKE
        paint.color = Color.WHITE
        paint.strokeWidth = 3f
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - 2, paint)

        return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
    }

    private fun getStatusText(status: FriendStatus): String {
        return when (status) {
            FriendStatus.IN_TRANSIT -> "В пути"
            FriendStatus.ARRIVED -> "Прибыл"
            FriendStatus.STOPPED -> "Остановился"
            FriendStatus.DANGER -> "В опасности!"
        }
    }

    private fun calculateRouteDistance(route: List<GeoPoint>): Double {
        var totalDist = 0.0

        for (i in 0 until route.size - 1) {
            totalDist += distanceBetween(route[i], route[i + 1])
        }

        return totalDist
    }

    private fun distanceBetween(p1: GeoPoint, p2: GeoPoint): Double {
        val R = 6371000.0
        val lat1 = Math.toRadians(p1.latitude)
        val lat2 = Math.toRadians(p2.latitude)
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return R * c
    }
}

// ===== МОДЕЛИ =====

data class Friend(
    val id: String,
    val name: String,
    var currentLocation: GeoPoint,
    val route: List<GeoPoint>,
    val destination: GeoPoint,
    var status: FriendStatus
)

enum class FriendStatus {
    IN_TRANSIT,  // В пути
    ARRIVED,     // Прибыл
    STOPPED,     // Остановился
    DANGER       // В опасности
}

data class FriendRouteStats(
    val friendName: String,
    val progress: Int,                // %
    val completedDistance: Double,    // метры
    val remainingDistance: Double,    // метры
    val averageRiskCompleted: Double,
    val averageRiskRemaining: Double,
    val currentStatus: FriendStatus,
    val currentRisk: Double
)