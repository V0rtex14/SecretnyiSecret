package com.example.saktahahathonv1.profile

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.example.saktahahathonv1.R
import com.example.saktahahathonv1.auth.AuthManager
import com.example.saktahahathonv1.auth.LoginActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ProfileActivity : AppCompatActivity() {

    private lateinit var authManager: AuthManager

    private lateinit var btnBack: TextView
    private lateinit var txtName: TextView
    private lateinit var txtContact: TextView
    private lateinit var btnEditProfile: MaterialButton
    private lateinit var btnAddContact: MaterialButton
    private lateinit var cardPrivacy: MaterialCardView
    private lateinit var cardNotifications: MaterialCardView
    private lateinit var cardAbout: MaterialCardView
    private lateinit var switchNotifications: SwitchCompat
    private lateinit var btnLogout: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        authManager = AuthManager(this)

        setupViews()
        loadUserData()
        setupClickListeners()
    }

    private fun setupViews() {
        btnBack = findViewById(R.id.btnBack)
        txtName = findViewById(R.id.txtName)
        txtContact = findViewById(R.id.txtContact)
        btnEditProfile = findViewById(R.id.btnEditProfile)
        btnAddContact = findViewById(R.id.btnAddContact)
        cardPrivacy = findViewById(R.id.cardPrivacy)
        cardNotifications = findViewById(R.id.cardNotifications)
        cardAbout = findViewById(R.id.cardAbout)
        switchNotifications = findViewById(R.id.switchNotifications)
        btnLogout = findViewById(R.id.btnLogout)
    }

    private fun loadUserData() {
        val currentUser = authManager.getCurrentUser()

        if (currentUser != null) {
            txtName.text = currentUser.name
            txtContact.text = currentUser.email

            val prefs = getSharedPreferences("auth_prefs", MODE_PRIVATE)
            prefs.edit().apply {
                putString("user_name", currentUser.name)
                putString("user_email", currentUser.email)
                putString("user_phone", currentUser.phone)
                putLong("user_id", currentUser.id)
                apply()
            }
        } else {
            Toast.makeText(this, "Пользователь не найден", Toast.LENGTH_SHORT).show()
            logout()
            return
        }

        val prefs = getSharedPreferences("auth_prefs", MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean("notifications_enabled", true)
        switchNotifications.isChecked = notificationsEnabled
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnEditProfile.setOnClickListener {
            showEditProfileDialog()
        }

        btnAddContact.setOnClickListener {
            showAddContactDialog()
        }

        cardPrivacy.setOnClickListener {
            showPrivacySettings()
        }

        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            saveNotificationSetting(isChecked)
            Toast.makeText(
                this,
                if (isChecked) "Уведомления включены" else "Уведомления выключены",
                Toast.LENGTH_SHORT
            ).show()
        }

        cardAbout.setOnClickListener {
            showAboutDialog()
        }

        btnLogout.setOnClickListener {
            showLogoutDialog()
        }
    }

    private fun showEditProfileDialog() {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val nameInput = EditText(this).apply {
            hint = "Имя"
            setText(txtName.text)
        }

        val emailInput = EditText(this).apply {
            hint = "Email"
            setText(txtContact.text)
        }

        dialogView.addView(nameInput)
        dialogView.addView(emailInput)

        MaterialAlertDialogBuilder(this)
            .setTitle("Редактировать профиль")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { dialog, _ ->
                val newName = nameInput.text.toString().trim()
                val newEmail = emailInput.text.toString().trim()

                if (newName.isNotEmpty() && newEmail.isNotEmpty()) {
                    txtName.text = newName
                    txtContact.text = newEmail

                    val prefs = getSharedPreferences("auth_prefs", MODE_PRIVATE)
                    prefs.edit().apply {
                        putString("user_name", newName)
                        putString("user_email", newEmail)
                        apply()
                    }

                    Toast.makeText(this, "Профиль обновлён", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showAddContactDialog() {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val nameInput = EditText(this).apply {
            hint = "Имя контакта"
        }

        val phoneInput = EditText(this).apply {
            hint = "Номер телефона"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }

        val relationInput = EditText(this).apply {
            hint = "Кто это (мама, друг и т.д.)"
        }

        dialogView.addView(nameInput)
        dialogView.addView(phoneInput)
        dialogView.addView(relationInput)

        MaterialAlertDialogBuilder(this)
            .setTitle("Добавить доверенный контакт")
            .setMessage("Этот человек будет уведомлён при активации SOS")
            .setView(dialogView)
            .setPositiveButton("Добавить") { dialog, _ ->
                val name = nameInput.text.toString().trim()
                val phone = phoneInput.text.toString().trim()
                val relation = relationInput.text.toString().trim()

                if (name.isNotEmpty() && phone.isNotEmpty()) {
                    saveTrustedContact(name, phone, relation)
                    Toast.makeText(this, "Контакт \"$name\" добавлен", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Заполните имя и телефон", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun saveTrustedContact(name: String, phone: String, relation: String) {
        val prefs = getSharedPreferences("trusted_contacts", MODE_PRIVATE)
        val contacts = prefs.getStringSet("contacts", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        contacts.add("$name|$phone|$relation")
        prefs.edit().putStringSet("contacts", contacts).apply()
    }

    private fun showPrivacySettings() {
        val prefs = getSharedPreferences("auth_prefs", MODE_PRIVATE)
        val shareLocation = prefs.getBoolean("share_location", true)
        val visibleToContacts = prefs.getBoolean("visible_to_contacts", true)
        val showHistory = prefs.getBoolean("show_history", true)

        val options = arrayOf(
            "Делиться местоположением с контактами",
            "Показывать статус онлайн",
            "Сохранять историю маршрутов"
        )
        val checkedItems = booleanArrayOf(shareLocation, visibleToContacts, showHistory)

        MaterialAlertDialogBuilder(this)
            .setTitle("Приватность")
            .setMultiChoiceItems(options, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Сохранить") { dialog, _ ->
                prefs.edit().apply {
                    putBoolean("share_location", checkedItems[0])
                    putBoolean("visible_to_contacts", checkedItems[1])
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
            .setTitle("Sakta")
            .setMessage(
                "Версия: 1.0.0\n\n" +
                        "Sakta — приложение для безопасной навигации по городу Бишкек.\n\n" +
                        "Возможности:\n" +
                        "• Безопасные маршруты\n" +
                        "• Зоны опасности на карте\n" +
                        "• Режим сопровождения\n" +
                        "• Экстренный SOS-вызов\n" +
                        "• Отслеживание близких\n\n" +
                        "© 2025 Amanat Team\n" +
                        "Хакатон MVP"
            )
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showLogoutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Выйти из аккаунта?")
            .setMessage("Вы уверены, что хотите выйти?")
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
        authManager.logout()

        val prefs = getSharedPreferences("auth_prefs", MODE_PRIVATE)
        prefs.edit().clear().apply()

        Toast.makeText(this, "Вы вышли из аккаунта", Toast.LENGTH_SHORT).show()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
