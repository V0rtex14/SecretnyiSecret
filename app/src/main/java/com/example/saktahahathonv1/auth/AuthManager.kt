package com.example.saktahahathonv1.auth

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileWriter

/**
 * Менеджер аутентификации - работает с файлом users.json
 */
class AuthManager(private val context: Context) {

    private val gson = Gson()
    private val usersFile: File
        get() = File(context.filesDir, "users.json")

    init {
        // Инициализация файла пользователей
        if (!usersFile.exists()) {
            try {
                // Пытаемся скопировать из assets
                val usersJson = context.assets.open("users.json").bufferedReader().use { it.readText() }
                FileWriter(usersFile).use { it.write(usersJson) }
                android.util.Log.d("AuthManager", "Loaded users from assets")
            } catch (e: Exception) {
                // Создаем пустой файл - пользователь должен зарегистрироваться
                FileWriter(usersFile).use { it.write("[]") }
                android.util.Log.d("AuthManager", "Created empty users.json - user must register")
            }
        }
    }

    /**
     * Регистрация нового пользователя
     */
    fun register(
        name: String,
        email: String,
        phone: String,
        password: String
    ): AuthResult {
        // Валидация
        if (name.isBlank()) return AuthResult.Error("Введите имя")
        if (email.isBlank()) return AuthResult.Error("Введите email")
        if (!isValidEmail(email)) return AuthResult.Error("Некорректный email")
        if (phone.isBlank()) return AuthResult.Error("Введите номер телефона")
        if (password.length < 6) return AuthResult.Error("Пароль должен быть минимум 6 символов")

        val users = loadUsers().toMutableList()

        // Проверка на существующий email
        if (users.any { it.email.equals(email, ignoreCase = true) }) {
            return AuthResult.Error("Пользователь с таким email уже существует")
        }

        // Проверка на существующий телефон
        if (users.any { it.phone == phone }) {
            return AuthResult.Error("Пользователь с таким номером уже существует")
        }

        // Создаем нового пользователя
        val newUser = User(
            id = System.currentTimeMillis(),
            name = name,
            email = email.lowercase(),
            phone = phone,
            passwordHash = hashPassword(password),
            registrationDate = System.currentTimeMillis(),
            gender = null,
            emergencyContact = null
        )

        users.add(newUser)
        saveUsers(users)

        // Сохраняем текущего пользователя
        saveCurrentUser(newUser)

        android.util.Log.d("AuthManager", "User registered: ${newUser.email}")
        return AuthResult.Success(newUser)
    }

    /**
     * Вход пользователя
     */
    fun login(emailOrPhone: String, password: String): AuthResult {
        if (emailOrPhone.isBlank()) return AuthResult.Error("Введите email или телефон")
        if (password.isBlank()) return AuthResult.Error("Введите пароль")

        val users = loadUsers()

        if (users.isEmpty()) {
            return AuthResult.Error("Нет зарегистрированных пользователей. Пожалуйста, зарегистрируйтесь.")
        }

        val passwordHash = hashPassword(password)

        val user = users.find {
            (it.email.equals(emailOrPhone, ignoreCase = true) || it.phone == emailOrPhone) &&
                    it.passwordHash == passwordHash
        }

        return if (user != null) {
            saveCurrentUser(user)
            android.util.Log.d("AuthManager", "User logged in: ${user.email}")
            AuthResult.Success(user)
        } else {
            AuthResult.Error("Неверный email/телефон или пароль")
        }
    }

    /**
     * Выход из аккаунта (НЕ удаляет пользователя из users.json)
     */
    fun logout() {
        val currentUser = getCurrentUser()
        context.getSharedPreferences("auth", Context.MODE_PRIVATE)
            .edit()
            .remove("current_user_id")
            .apply()

        android.util.Log.d("AuthManager", "User logged out: ${currentUser?.email ?: "unknown"}")
    }

    /**
     * Получить текущего пользователя
     */
    fun getCurrentUser(): User? {
        val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
        val userId = prefs.getLong("current_user_id", -1)

        if (userId == -1L) return null

        val users = loadUsers()
        return users.find { it.id == userId }
    }

    /**
     * Проверка авторизации
     */
    fun isLoggedIn(): Boolean {
        return getCurrentUser() != null
    }

    /**
     * Обновить данные пользователя
     */
    fun updateUser(updatedUser: User): Boolean {
        val users = loadUsers().toMutableList()
        val index = users.indexOfFirst { it.id == updatedUser.id }

        if (index == -1) return false

        users[index] = updatedUser
        saveUsers(users)

        // Обновляем текущего пользователя если это он
        if (getCurrentUser()?.id == updatedUser.id) {
            saveCurrentUser(updatedUser)
        }

        android.util.Log.d("AuthManager", "User updated: ${updatedUser.email}")
        return true
    }

    // ===== Приватные методы =====

    private fun loadUsers(): List<User> {
        return try {
            val json = usersFile.readText()
            val type = object : TypeToken<List<User>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e("AuthManager", "Failed to load users", e)
            emptyList()
        }
    }

    private fun saveUsers(users: List<User>) {
        try {
            val json = gson.toJson(users)
            usersFile.writeText(json)
            android.util.Log.d("AuthManager", "Saved ${users.size} users to ${usersFile.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.e("AuthManager", "Failed to save users", e)
            e.printStackTrace()
        }
    }

    private fun saveCurrentUser(user: User) {
        context.getSharedPreferences("auth", Context.MODE_PRIVATE)
            .edit()
            .putLong("current_user_id", user.id)
            .apply()
    }

    private fun hashPassword(password: String): String {
        // Простое хеширование для демо (в продакшене использовать BCrypt или Argon2)
        return password.hashCode().toString()
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}

// ===== Модели =====

data class User(
    val id: Long,
    val name: String,
    val email: String,
    val phone: String,
    val passwordHash: String,
    val registrationDate: Long,
    val gender: String?,
    val emergencyContact: String?
)

sealed class AuthResult {
    data class Success(val user: User) : AuthResult()
    data class Error(val message: String) : AuthResult()
}