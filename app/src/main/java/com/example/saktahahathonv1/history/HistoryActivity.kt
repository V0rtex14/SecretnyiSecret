package com.example.saktahahathonv1.history

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.saktahahathonv1.MainActivity
import com.example.saktahahathonv1.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {

    private lateinit var recyclerHistory: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var txtTotalRoutes: TextView
    private lateinit var txtTotalDistance: TextView
    private lateinit var txtAvgSafety: TextView
    private lateinit var historyManager: HistoryManager
    private lateinit var historyAdapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        historyManager = HistoryManager(this)

        setupToolbar()
        setupViews()
        loadHistory()
        loadStats()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupViews() {
        recyclerHistory = findViewById(R.id.recyclerHistory)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        txtTotalRoutes = findViewById(R.id.txtTotalRoutes)
        txtTotalDistance = findViewById(R.id.txtTotalDistance)
        txtAvgSafety = findViewById(R.id.txtAvgSafety)

        recyclerHistory.layoutManager = LinearLayoutManager(this)
    }

    private fun loadHistory() {
        val history = historyManager.getHistory("current_user")

        if (history.isEmpty()) {
            layoutEmpty.visibility = View.VISIBLE
            recyclerHistory.visibility = View.GONE
        } else {
            layoutEmpty.visibility = View.GONE
            recyclerHistory.visibility = View.VISIBLE

            historyAdapter = HistoryAdapter(
                routes = history,
                onRouteClick = { route ->
                    showRouteOnMap(route)
                },
                onFavoriteClick = { route ->
                    historyManager.toggleFavorite(route.id)
                    loadHistory()
                    loadStats()
                },
                onDeleteClick = { route ->
                    showDeleteDialog(route)
                }
            )
            recyclerHistory.adapter = historyAdapter
        }
    }

    private fun loadStats() {
        val stats = historyManager.getStats("current_user")

        txtTotalRoutes.text = stats.totalRoutes.toString()
        txtTotalDistance.text = String.format("%.1f км", stats.totalDistance / 1000)
        txtAvgSafety.text = String.format("%.1f", stats.averageSafetyScore)
    }

    private fun showRouteOnMap(route: RouteHistory) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("show_route_from_history", true)
            putExtra("from_lat", route.fromLocation.latitude)
            putExtra("from_lon", route.fromLocation.longitude)
            putExtra("to_lat", route.toLocation.latitude)
            putExtra("to_lon", route.toLocation.longitude)
        }
        startActivity(intent)
        finish()
    }

    private fun showDeleteDialog(route: RouteHistory) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Удалить маршрут")
            .setMessage("Вы уверены, что хотите удалить этот маршрут из истории?")
            .setPositiveButton("Удалить") { dialog, _ ->
                if (historyManager.deleteRoute(route.id)) {
                    Toast.makeText(this, "Маршрут удалён", Toast.LENGTH_SHORT).show()
                    loadHistory()
                    loadStats()
                } else {
                    Toast.makeText(this, "Ошибка удаления", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}

/**
 * Адаптер для списка истории маршрутов
 */
class HistoryAdapter(
    private val routes: List<RouteHistory>,
    private val onRouteClick: (RouteHistory) -> Unit,
    private val onFavoriteClick: (RouteHistory) -> Unit,
    private val onDeleteClick: (RouteHistory) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtDate: TextView = view.findViewById(R.id.txtDate)
        val txtFrom: TextView = view.findViewById(R.id.txtFrom)
        val txtTo: TextView = view.findViewById(R.id.txtTo)
        val txtDistance: TextView = view.findViewById(R.id.txtDistance)
        val txtDuration: TextView = view.findViewById(R.id.txtDuration)
        val txtSafety: TextView = view.findViewById(R.id.txtSafety)
        val txtRouteType: TextView = view.findViewById(R.id.txtRouteType)
        val btnFavorite: ImageButton = view.findViewById(R.id.btnFavorite)
        val btnMenu: ImageButton = view.findViewById(R.id.btnMenu)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_route_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val route = routes[position]

        // Дата
        holder.txtDate.text = dateFormat.format(Date(route.timestamp))

        // Маршрут
        holder.txtFrom.text = route.fromAddress
        holder.txtTo.text = route.toAddress

        // Расстояние
        holder.txtDistance.text = String.format("%.1f км", route.distance / 1000)

        // Время
        val minutes = (route.duration / 60).toInt()
        holder.txtDuration.text = "$minutes мин"

        // Безопасность
        holder.txtSafety.text = String.format("%.1f", route.safetyScore)

        // Тип маршрута
        holder.txtRouteType.text = route.routeType

        // Избранное
        holder.btnFavorite.setImageResource(
            if (route.isFavorite) android.R.drawable.star_big_on
            else android.R.drawable.star_big_off
        )

        // Клик по карточке
        holder.itemView.setOnClickListener {
            onRouteClick(route)
        }

        // Избранное
        holder.btnFavorite.setOnClickListener {
            onFavoriteClick(route)
        }

        // Меню
        holder.btnMenu.setOnClickListener {
            showRouteMenu(holder.itemView, route)
        }
    }

    override fun getItemCount() = routes.size

    private fun showRouteMenu(view: View, route: RouteHistory) {
        val popup = androidx.appcompat.widget.PopupMenu(view.context, view)
        popup.menuInflater.inflate(R.menu.menu_route_history, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_show_on_map -> {
                    onRouteClick(route)
                    true
                }
                R.id.action_delete -> {
                    onDeleteClick(route)
                    true
                }
                else -> false
            }
        }

        popup.show()
    }
}
