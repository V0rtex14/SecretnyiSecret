package com.example.saktahahathonv1.map


import android.content.Context
import android.graphics.*
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay

/**
 * Менеджер стилей карты с улучшенной визуализацией
 */
object MapStyleManager {

    enum class MapTheme {
        STANDARD,    // Стандартный OSM
        DARK,        // Тёмная тема
        SAFE_MODE    // Режим безопасности (цветовое кодирование)
    }

    /**
     * Применить тему к карте
     */
    fun applyTheme(mapView: MapView, theme: MapTheme, context: Context) {
        when (theme) {
            MapTheme.STANDARD -> applyStandardTheme(mapView)
            MapTheme.DARK -> applyDarkTheme(mapView, context)
            MapTheme.SAFE_MODE -> applySafeModeTheme(mapView, context)
        }
    }

    private fun applyStandardTheme(mapView: MapView) {
        // Используем качественный tile source
        mapView.setTileSource(object : XYTileSource(
            "Mapnik",
            0, 19, 256, ".png",
            arrayOf(
                "https://a.tile.openstreetmap.org/",
                "https://b.tile.openstreetmap.org/",
                "https://c.tile.openstreetmap.org/"
            )
        ) {})

        mapView.overlayManager.tilesOverlay.loadingBackgroundColor = Color.parseColor("#E8E8E8")
    }

    private fun applyDarkTheme(mapView: MapView, context: Context) {
        // Тёмные тайлы (если есть альтернативный источник)
        // Или применяем фильтр к стандартным тайлам

        mapView.setTileSource(TileSourceFactory.MAPNIK)

        // Применяем тёмный фильтр
        val tilesOverlay = mapView.overlayManager.tilesOverlay
        tilesOverlay.setColorFilter(getDarkModeColorFilter())
        tilesOverlay.loadingBackgroundColor = Color.parseColor("#1A1A1A")
    }

    private fun applySafeModeTheme(mapView: MapView, context: Context) {
        // Спокойные цвета для фокуса на безопасности
        mapView.setTileSource(TileSourceFactory.MAPNIK)

        val tilesOverlay = mapView.overlayManager.tilesOverlay
        tilesOverlay.setColorFilter(getSafeModeColorFilter())
        tilesOverlay.loadingBackgroundColor = Color.parseColor("#F5F5F5")
    }

    /**
     * Цветовой фильтр для тёмного режима
     */
    private fun getDarkModeColorFilter(): ColorMatrixColorFilter {
        val matrix = ColorMatrix()

        // Инверсия + снижение яркости
        val invertMatrix = ColorMatrix(floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        ))

        val brightnessMatrix = ColorMatrix(floatArrayOf(
            0.7f, 0f, 0f, 0f, 0f,
            0f, 0.7f, 0f, 0f, 0f,
            0f, 0f, 0.7f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))

        matrix.postConcat(invertMatrix)
        matrix.postConcat(brightnessMatrix)

        return ColorMatrixColorFilter(matrix)
    }

    /**
     * Фильтр для режима безопасности (приглушённые цвета)
     */
    private fun getSafeModeColorFilter(): ColorMatrixColorFilter {
        val matrix = ColorMatrix()
        matrix.setSaturation(0.6f) // Снижаем насыщенность

        return ColorMatrixColorFilter(matrix)
    }
}

/**
 * Кастомные маркеры для разных типов объектов
 */
object CustomMarkerFactory {

    /**
     * Создать маркер для безопасного места
     */
    fun createSafePlaceMarker(context: Context, type: com.example.saktahahathonv1.map.SafePlaceType): Bitmap {
        val size = 80
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Фон (круг)
        val bgColor = when (type) {
            com.example.saktahahathonv1.map.SafePlaceType.POLICE -> Color.parseColor("#2196F3")
            com.example.saktahahathonv1.map.SafePlaceType.HOSPITAL -> Color.parseColor("#4CAF50")
            com.example.saktahahathonv1.map.SafePlaceType.SHOP24 -> Color.parseColor("#FF9800")
            com.example.saktahahathonv1.map.SafePlaceType.CAFE24 -> Color.parseColor("#9C27B0")
        }

        paint.color = bgColor
        paint.style = Paint.Style.FILL
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4, paint)

        // Белая обводка
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4, paint)

        // Иконка (текст/символ)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 32f
        paint.textAlign = Paint.Align.CENTER

        val symbol = when (type) {
            com.example.saktahahathonv1.map.SafePlaceType.POLICE -> "🛡"
            com.example.saktahahathonv1.map.SafePlaceType.HOSPITAL -> "+"
            com.example.saktahahathonv1.map.SafePlaceType.SHOP24 -> "🏪"
            com.example.saktahahathonv1.map.SafePlaceType.CAFE24 -> "☕"

        }

        canvas.drawText(symbol, size / 2f, size / 2f + 10, paint)

        return bitmap
    }

    /**
     * Создать маркер для инцидента
     */
    fun createIncidentMarker(context: Context, severity: Int): Bitmap {
        val size = 60
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Цвет по severity
        val color = when {
            severity >= 4 -> Color.parseColor("#D32F2F")
            severity >= 3 -> Color.parseColor("#F57C00")
            else -> Color.parseColor("#FBC02D")
        }

        // Внешнее кольцо (пульсация)
        paint.color = ColorUtils.setAlphaComponent(color, 60)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2, paint)

        // Основной круг
        paint.color = color
        canvas.drawCircle(size / 2f, size / 2f, size / 3f, paint)

        // Восклицательный знак
        paint.color = Color.WHITE
        paint.textSize = 24f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("!", size / 2f, size / 2f + 8, paint)

        return bitmap
    }

    /**
     * Создать маркер для жалобы пользователя
     */
    fun createComplaintMarker(context: Context, weight: Double, isFemale: Boolean): Bitmap {
        val size = 50
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Цвет: розовый для женщин, голубой для мужчин
        val baseColor = if (isFemale)
            Color.parseColor("#E91E63")
        else
            Color.parseColor("#2196F3")

        // Прозрачность по весу
        val alpha = (100 + (weight / 5.0 * 155)).toInt().coerceIn(100, 255)
        paint.color = ColorUtils.setAlphaComponent(baseColor, alpha)
        paint.style = Paint.Style.FILL

        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4, paint)

        // Обводка
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4, paint)

        return bitmap
    }
}

/**
 * Утилиты для работы с цветом
 */
object ColorUtils {

    fun setAlphaComponent(color: Int, alpha: Int): Int {
        require(alpha in 0..255) { "Alpha must be between 0 and 255" }
        return (color and 0x00ffffff) or (alpha shl 24)
    }

    /**
     * Получить цвет по уровню риска
     */
    fun getRiskColor(risk: Double): Int {
        return when {
            risk < 0.5 -> Color.parseColor("#4CAF50")  // Зелёный
            risk < 1.0 -> Color.parseColor("#FFEB3B")  // Жёлтый
            risk < 1.5 -> Color.parseColor("#FF9800")  // Оранжевый
            else -> Color.parseColor("#F44336")        // Красный
        }
    }

    /**
     * Получить градиент для полилинии маршрута
     */
    fun createRouteGradient(startColor: Int, endColor: Int, steps: Int): IntArray {
        val colors = IntArray(steps)

        val startR = Color.red(startColor)
        val startG = Color.green(startColor)
        val startB = Color.blue(startColor)

        val endR = Color.red(endColor)
        val endG = Color.green(endColor)
        val endB = Color.blue(endColor)

        for (i in 0 until steps) {
            val ratio = i.toFloat() / (steps - 1)
            val r = (startR + ratio * (endR - startR)).toInt()
            val g = (startG + ratio * (endG - startG)).toInt()
            val b = (startB + ratio * (endB - startB)).toInt()

            colors[i] = Color.rgb(r, g, b)
        }

        return colors
    }
}