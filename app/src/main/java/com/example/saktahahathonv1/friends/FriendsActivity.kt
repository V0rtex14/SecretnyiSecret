package com.example.saktahahathonv1.friends

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.saktahahathonv1.MainActivity
import com.example.saktahahathonv1.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText

class FriendsActivity : AppCompatActivity() {

    private lateinit var recyclerFriends: RecyclerView
    private lateinit var recyclerRequests: RecyclerView
    private lateinit var fabAddFriend: FloatingActionButton
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var friendsManager: FriendsManager
    private lateinit var friendsAdapter: FriendsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friends)

        friendsManager = FriendsManager(this)

        setupToolbar()
        setupViews()
        loadFriends()
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
        recyclerFriends = findViewById(R.id.recyclerFriends)
        recyclerRequests = findViewById(R.id.recyclerRequests)
        fabAddFriend = findViewById(R.id.fabAddFriend)
        layoutEmpty = findViewById(R.id.layoutEmpty)

        recyclerFriends.layoutManager = LinearLayoutManager(this)

        fabAddFriend.setOnClickListener {
            showAddFriendDialog()
        }
    }

    private fun loadFriends() {
        val friends = friendsManager.getFriends("current_user")

        if (friends.isEmpty()) {
            layoutEmpty.visibility = View.VISIBLE
            recyclerFriends.visibility = View.GONE
        } else {
            layoutEmpty.visibility = View.GONE
            recyclerFriends.visibility = View.VISIBLE

            friendsAdapter = FriendsAdapter(
                friends = friends,
                onShowOnMap = { friend ->
                    showFriendOnMap(friend)
                },
                onRemove = { friend ->
                    showRemoveFriendDialog(friend)
                }
            )
            recyclerFriends.adapter = friendsAdapter
        }

        // Загружаем запросы
        loadFriendRequests()
    }

    private fun loadFriendRequests() {
        val requests = friendsManager.getFriendRequests("current_user")
        val cardRequests = findViewById<View>(R.id.cardRequests)

        if (requests.isEmpty()) {
            cardRequests.visibility = View.GONE
        } else {
            cardRequests.visibility = View.VISIBLE
            // TODO: Реализовать адаптер для запросов
        }
    }

    private fun showAddFriendDialog() {
        val dialogView = LayoutInflater.from(this).inflate(
            R.layout.dialog_add_friend,
            null
        )

        val inputPhone = dialogView.findViewById<TextInputEditText>(R.id.inputPhone)

        MaterialAlertDialogBuilder(this)
            .setTitle("Добавить друга")
            .setView(dialogView)
            .setPositiveButton("Отправить запрос") { dialog, _ ->
                val phone = inputPhone.text.toString().trim()
                if (phone.isNotEmpty()) {
                    sendFriendRequest(phone)
                } else {
                    Toast.makeText(this, "Введите номер телефона", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun sendFriendRequest(phone: String) {
        val request = FriendRequest(
            id = "request_${System.currentTimeMillis()}",
            fromUserId = "current_user",
            fromUserName = "Вы",
            fromUserPhone = "+996 555 000 000",
            toUserId = phone,
            timestamp = System.currentTimeMillis()
        )

        if (friendsManager.sendFriendRequest(request)) {
            Toast.makeText(this, "Запрос отправлен", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Ошибка отправки запроса", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showFriendOnMap(friend: Friend) {
        if (friend.lastLocation != null) {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("show_friend_location", true)
                putExtra("friend_lat", friend.lastLocation.lat)
                putExtra("friend_lon", friend.lastLocation.lon)
                putExtra("friend_name", friend.name)
            }
            startActivity(intent)
            finish()
        } else {
            Toast.makeText(this, "${friend.name} не делится местоположением", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRemoveFriendDialog(friend: Friend) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Удалить друга")
            .setMessage("Вы уверены, что хотите удалить ${friend.name} из друзей?")
            .setPositiveButton("Удалить") { dialog, _ ->
                if (friendsManager.removeFriend(friend.id)) {
                    Toast.makeText(this, "Друг удалён", Toast.LENGTH_SHORT).show()
                    loadFriends()
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
 * Адаптер для списка друзей
 */
class FriendsAdapter(
    private val friends: List<Friend>,
    private val onShowOnMap: (Friend) -> Unit,
    private val onRemove: (Friend) -> Unit
) : RecyclerView.Adapter<FriendsAdapter.FriendViewHolder>() {

    class FriendViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtName: TextView = view.findViewById(R.id.txtName)
        val txtStatus: TextView = view.findViewById(R.id.txtStatus)
        val txtLocation: TextView = view.findViewById(R.id.txtLocation)
        val btnShowOnMap: ImageButton = view.findViewById(R.id.btnShowOnMap)
        val btnMenu: ImageButton = view.findViewById(R.id.btnMenu)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friend, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val friend = friends[position]

        holder.txtName.text = friend.name

        // Статус
        val statusText = when (friend.status) {
            FriendStatus.ONLINE -> "Онлайн"
            FriendStatus.SHARING_LOCATION -> "Делится местоположением"
            FriendStatus.OFFLINE -> "Не в сети"
        }
        holder.txtStatus.text = statusText

        // Местоположение
        if (friend.lastLocation != null && friend.shareLocation) {
            holder.txtLocation.visibility = View.VISIBLE
            holder.txtLocation.text = friend.lastLocation.address ?: "Местоположение доступно"
            holder.btnShowOnMap.visibility = View.VISIBLE
        } else {
            holder.txtLocation.visibility = View.GONE
            holder.btnShowOnMap.visibility = View.GONE
        }

        // Показать на карте
        holder.btnShowOnMap.setOnClickListener {
            onShowOnMap(friend)
        }

        // Меню
        holder.btnMenu.setOnClickListener {
            showFriendMenu(holder.itemView, friend)
        }
    }

    override fun getItemCount() = friends.size

    private fun showFriendMenu(view: View, friend: Friend) {
        val popup = androidx.appcompat.widget.PopupMenu(view.context, view)
        popup.menuInflater.inflate(R.menu.menu_friend, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_show_profile -> {
                    Toast.makeText(view.context, "Профиль ${friend.name}", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.action_remove -> {
                    onRemove(friend)
                    true
                }
                else -> false
            }
        }

        popup.show()
    }
}
