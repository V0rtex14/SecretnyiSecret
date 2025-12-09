package com.example.saktahahathonv1.data

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.saktahahathonv1.map.*
import org.osmdroid.util.GeoPoint
import kotlinx.coroutines.*

class RouteSelectionDialog(
    private val context: Context,
    private val onRouteSelected: (RouteOption) -> Unit
) {

    private val dialog: Dialog = Dialog(context, android.R.style.Theme_Material_Light_Dialog_MinWidth)

    private lateinit var inputFrom: AutoCompleteTextView
    private lateinit var inputTo: AutoCompleteTextView
    private lateinit var btnSwap: ImageButton
    private lateinit var btnMyLocation: ImageButton
    private lateinit var btnBuild: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var routesRecycler: RecyclerView
    private lateinit var layoutRoutes: LinearLayout

    private var fromPoint: GeoPoint? = null
    private var toPoint: GeoPoint? = null
    private var availableRoutes = listOf<RouteOption>()

    private val popularPlaces = listOf(
        PlaceSuggestion("Площадь Ала-Тоо", GeoPoint(42.8746, 74.6098)),
        PlaceSuggestion("ТЦ Bishkek Park", GeoPoint(42.8654, 74.6130)),
        PlaceSuggestion("Ошский базар", GeoPoint(42.8554, 74.6095)),
        PlaceSuggestion("ТЦ Ала-Арча", GeoPoint(42.8810, 74.5950)),
        PlaceSuggestion("Вокзал", GeoPoint(42.8765, 74.6050)),
        PlaceSuggestion("ЦУМ", GeoPoint(42.8732, 74.6095))
    )

    fun show() {
        setupDialog()
        dialog.show()
    }

    private fun setupDialog() {
        val view = createDialogView()

        dialog.setContentView(view)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        setupAutoComplete()
        setupButtons()
        setupRecyclerView()
    }

    private fun createDialogView(): View {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        val title = TextView(context).apply {
            text = "Построить маршрут"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 40)
        }
        layout.addView(title)

        inputFrom = AutoCompleteTextView(context).apply {
            hint = "Откуда"
            setPadding(20, 20, 20, 20)
        }
        layout.addView(inputFrom, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val buttonsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 10)
        }

        btnSwap = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_sort_by_size)
        }
        buttonsLayout.addView(btnSwap, 48, 48)

        val spacer = View(context)
        buttonsLayout.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))

        btnMyLocation = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_mylocation)
        }
        buttonsLayout.addView(btnMyLocation, 48, 48)

        layout.addView(buttonsLayout)

        inputTo = AutoCompleteTextView(context).apply {
            hint = "Куда"
            setPadding(20, 20, 20, 20)
        }
        layout.addView(inputTo, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        btnBuild = Button(context).apply {
            text = "Найти маршруты"
        }
        val buildParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        buildParams.topMargin = 40
        layout.addView(btnBuild, buildParams)

        progressBar = ProgressBar(context).apply {
            visibility = View.GONE
        }
        val progressParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        progressParams.topMargin = 20
        layout.addView(progressBar, progressParams)

        layoutRoutes = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        val routesTitle = TextView(context).apply {
            text = "Выберите маршрут:"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 20, 0, 20)
        }
        layoutRoutes.addView(routesTitle)

        routesRecycler = RecyclerView(context)
        layoutRoutes.addView(routesRecycler, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            600
        ))

        layout.addView(layoutRoutes)

        return layout
    }

    private fun setupAutoComplete() {
        val adapter = ArrayAdapter(
            context,
            android.R.layout.simple_dropdown_item_1line,
            popularPlaces.map { it.name }
        )

        inputFrom.setAdapter(adapter)
        inputTo.setAdapter(adapter)

        inputFrom.threshold = 1
        inputTo.threshold = 1

        inputFrom.setOnItemClickListener { _, _, position, _ ->
            fromPoint = popularPlaces[position].location
        }

        inputTo.setOnItemClickListener { _, _, position, _ ->
            toPoint = popularPlaces[position].location
        }
    }

    private fun setupButtons() {
        btnSwap.setOnClickListener {
            val tempText = inputFrom.text.toString()
            inputFrom.setText(inputTo.text.toString())
            inputTo.setText(tempText)

            val tempPoint = fromPoint
            fromPoint = toPoint
            toPoint = tempPoint
        }

        btnMyLocation.setOnClickListener {
            inputFrom.setText("Моё местоположение")
            fromPoint = GeoPoint(42.8746, 74.5698)
        }

        btnBuild.setOnClickListener {
            buildRoutes()
        }
    }

    private fun setupRecyclerView() {
        routesRecycler.layoutManager = LinearLayoutManager(context)
        layoutRoutes.visibility = View.GONE
    }

    private fun buildRoutes() {
        val from = fromPoint
        val to = toPoint

        if (from == null || to == null) {
            Toast.makeText(context, "Укажите оба адреса", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        btnBuild.isEnabled = false

        CoroutineScope(Dispatchers.Main).launch {
            try {
                delay(1500)

                availableRoutes = createDemoRoutes(from, to)
                displayRoutes()

            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                progressBar.visibility = View.GONE
                btnBuild.isEnabled = true
            }
        }
    }

    private fun displayRoutes() {
        layoutRoutes.visibility = View.VISIBLE

        val adapter = RoutesAdapter(availableRoutes) { selectedRoute ->
            onRouteSelected(selectedRoute)
            dialog.dismiss()
        }

        routesRecycler.adapter = adapter
    }

    private fun createDemoRoutes(from: GeoPoint, to: GeoPoint): List<RouteOption> {
        val distance = calculateDistance(from, to)

        return listOf(
            RouteOption(
                route = RouteData(
                    points = listOf(from, to),
                    distance = distance,
                    duration = distance / 1.4,
                    roadType = RoadType.DIRECT
                ),
                evaluation = ExtendedRouteEvaluation(
                    baseEvaluation = RouteEvaluation(distance, 0.8, 0.5, emptyList()),
                    adjustedRisk = 0.6,
                    totalScore = 1.2,
                    lightCoverage = 60.0,
                    crowdCoverage = 40.0,
                    roadQuality = "Прямой путь"
                ),
                type = RouteType.DIRECT,
                description = "Кратчайший путь"
            ),
            RouteOption(
                route = RouteData(
                    points = listOf(from, to),
                    distance = distance * 1.2,
                    duration = distance * 1.2 / 1.4,
                    roadType = RoadType.MAJOR_LIT
                ),
                evaluation = ExtendedRouteEvaluation(
                    baseEvaluation = RouteEvaluation(distance * 1.2, 0.4, 0.3, emptyList()),
                    adjustedRisk = 0.3,
                    totalScore = 0.8,
                    lightCoverage = 95.0,
                    crowdCoverage = 80.0,
                    roadQuality = "Главная освещённая"
                ),
                type = RouteType.SAFEST,
                description = "Самый безопасный"
            )
        )
    }

    private fun calculateDistance(p1: GeoPoint, p2: GeoPoint): Double {
        val R = 6371000.0
        val lat1 = Math.toRadians(p1.latitude)
        val lat2 = Math.toRadians(p2.latitude)
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return R * c
    }
}

class RoutesAdapter(
    private val routes: List<RouteOption>,
    private val onRouteClick: (RouteOption) -> Unit
) : RecyclerView.Adapter<RoutesAdapter.RouteViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
        val cardView = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.WHITE)
        }
        return RouteViewHolder(cardView)
    }

    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
        holder.bind(routes[position], onRouteClick)
    }

    override fun getItemCount() = routes.size

    class RouteViewHolder(private val view: LinearLayout) : RecyclerView.ViewHolder(view) {
        fun bind(route: RouteOption, onClick: (RouteOption) -> Unit) {
            view.removeAllViews()

            val title = TextView(view.context).apply {
                text = route.description
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            view.addView(title)

            val info = TextView(view.context).apply {
                text = "${(route.route.distance / 1000).format(2)} км • ${(route.route.duration / 60).toInt()} мин"
                textSize = 14f
                setTextColor(Color.GRAY)
            }
            view.addView(info)

            val riskText = when {
                route.evaluation.adjustedRisk < 0.5 -> "✅ Безопасно"
                route.evaluation.adjustedRisk < 1.5 -> "⚠️ Средний риск"
                else -> "⛔ Высокий риск"
            }

            val risk = TextView(view.context).apply {
                text = riskText
                textSize = 14f
            }
            view.addView(risk)

            view.setOnClickListener { onClick(route) }
        }

        private fun Double.format(digits: Int) = "%.${digits}f".format(this)
    }
}

data class PlaceSuggestion(
    val name: String,
    val location: GeoPoint
)