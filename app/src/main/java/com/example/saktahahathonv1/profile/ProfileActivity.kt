package com.example.saktahahathonv1.profile

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.example.saktahahathonv1.R
import com.example.saktahahathonv1.auth.LoginActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ProfileActivity : AppCompatActivity() {

    private lateinit var txtName: TextView
    private lateinit var txtContact: TextView
    private lateinit var cardPrivacy: MaterialCardView
    private lateinit var cardNotifications: MaterialCardView
    private lateinit var cardAbout: MaterialCardView
    private lateinit var switchNotifications: SwitchCompat
    private lateinit var btnLogout: com.google.android.material.button.MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        setupToolbar()
        setupViews()
        loadUserData()
        setupClickListeners()
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
        txtName = findViewById(R.id.txtName)
        txtContact = findViewById(R.id.txtContact)
        cardPrivacy = findViewById(R.id.cardPrivacy)
        cardNotifications = findViewById(R.id.cardNotifications)
        cardAbout = findViewById(R.id.cardAbout)
        switchNotifications = findViewById(R.id.switchNotifications)
        btnLogout = findViewById(R.id.btnLogout)
    }

    private fun loadUserData() {
        // Загружаем данные пользователя
        val prefs = getSharedPreferences("auth_prefs", MODE_PRIVATE)
        val name = prefs.getString("user_name", "Пользователь") ?: "Пользователь"
        val email = prefs.getString("user_email", "user@example.com") ?: "user@example.com"

        txtName.text = name
        txtContact.text = email

        // Загружаем настройки
        val notificationsEnabled = prefs.getBoolean("notifications_enabled", true)
        switchNotifications.isChecked = notificationsEnabled
    }

    private fun setupClickListeners() {
        // Приватность
        cardPrivacy.setOnClickListener {
            showPrivacySettings()
        }

        // Уведомления
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            saveNotificationSetting(isChecked)
            Toast.makeText(
                this,
                if (isChecked) "Уведомления включены" else "Уведомления выключены",
                Toast.LENGTH_SHORT
            ).show()
        }

        // О приложении
        cardAbout.setOnClickListener {
            showAboutDialog()
        }

        // Выход
        btnLogout.setOnClickListener {
            showLogoutDialog()
        }
    }

    private fun showPrivacySettings() {
        val prefs = getSharedPreferences("auth_prefs", MODE_PRIVATE)
        val shareLocation = prefs.getBoolean("share_location", true)
        val visibleToFriends = prefs.getBoolean("visible_to_friends", true)

        val options = arrayOf(
            "Делиться местоположением",
            "Видимость для друзей",
            "Показывать историю маршрутов"
        )
        val checkedItems = booleanArrayOf(
            shareLocation,
            visibleToFriends,
            prefs.getBoolean("show_history", true)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle("Настройки приватности")
            .setMultiChoiceItems(options, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Сохранить") { dialog, _ ->
                prefs.edit().apply {
                    putBoolean("share_location", checkedItems[0])
                    putBoolean("visible_to_friends", checkedItems[1])
                    putBoolean("show_history", checkedItems[2])
                    apply()
                }
                Toast.makeText(this, "Настройки сохранены", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun saveNotificationSetting(enabled: Boolean) {
        val prefs = getSharedPreferences("auth_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("notifications_enabled", enabled).apply()
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("SafeWalk")
            .setMessage(
                "Версия: 1.0.0\n\n" +
                "SafeWalk - приложение для безопасной навигации по городу Бишкек.\n\n" +
                "Приложение помогает:\n" +
                "• Строить безопасные маршруты\n" +
                "• Избегать опасных зон\n" +
                "• Делиться местоположением с друзьями\n" +
                "• Быстро вызывать помощь\n\n" +
                "© 2024 SafeWalk Team"
            )
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showLogoutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Выход")
            .setMessage("Вы уверены, что хотите выйти из аккаунта?")
            .setPositiveButton("Выйти") { dialog, _ ->
                logout()
                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun logout() {
        // Очищаем данные сессии
        val prefs = getSharedPreferences("auth_prefs", MODE_PRIVATE)
        prefs.edit().clear().apply()

        // Переходим на экран входа
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
