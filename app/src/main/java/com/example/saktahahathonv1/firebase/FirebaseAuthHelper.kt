package com.example.saktahahathonv1.firebase

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Помощник для Firebase Authentication
 * Поддерживает анонимную авторизацию для быстрого MVP
 */
class FirebaseAuthHelper(private val context: Context) {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("sakta_auth", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_PHONE = "user_phone"
        private const val KEY_DEVICE_ID = "device_id"

        @Volatile
        private var instance: FirebaseAuthHelper? = null

        fun getInstance(context: Context): FirebaseAuthHelper {
            return instance ?: synchronized(this) {
                instance ?: FirebaseAuthHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Получить текущего пользователя Firebase
     */
    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    /**
     * Получить ID текущего пользователя
     */
    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid ?: prefs.getString(KEY_USER_ID, null)
    }

    /**
     * Получить уникальный идентификатор устройства (для offline работы)
     */
    fun getDeviceId(): String {
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        return deviceId
    }

    /**
     * Анонимная авторизация (для быстрого MVP)
     */
    suspend fun signInAnonymously(): Result<String> {
        return try {
            val result = auth.signInAnonymously().await()
            val userId = result.user?.uid
            if (userId != null) {
                prefs.edit().putString(KEY_USER_ID, userId).apply()
                Result.success(userId)
            } else {
                Result.failure(Exception("Не удалось получить ID пользователя"))
            }
        } catch (e: Exception) {
            // Fallback на локальный ID если Firebase недоступен
            val localId = getDeviceId()
            prefs.edit().putString(KEY_USER_ID, localId).apply()
            Result.success(localId)
        }
    }

    /**
     * Авторизация по телефону (callback-based для простоты интеграции)
     */
    fun signInAnonymouslyWithCallback(onComplete: (String?) -> Unit) {
        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                val userId = result.user?.uid
                userId?.let {
                    prefs.edit().putString(KEY_USER_ID, it).apply()
                }
                onComplete(userId)
            }
            .addOnFailureListener {
                // Fallback на локальный ID
                val localId = getDeviceId()
                prefs.edit().putString(KEY_USER_ID, localId).apply()
                onComplete(localId)
            }
    }

    /**
     * Проверить, авторизован ли пользователь
     */
    fun isSignedIn(): Boolean {
        return auth.currentUser != null || prefs.getString(KEY_USER_ID, null) != null
    }

    /**
     * Сохранить имя пользователя локально
     */
    fun setUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name).apply()
    }

    /**
     * Получить имя пользователя
     */
    fun getUserName(): String {
        return prefs.getString(KEY_USER_NAME, null) ?: "Пользователь"
    }

    /**
     * Сохранить телефон пользователя
     */
    fun setUserPhone(phone: String) {
        prefs.edit().putString(KEY_USER_PHONE, phone).apply()
    }

    /**
     * Получить телефон пользователя
     */
    fun getUserPhone(): String? {
        return prefs.getString(KEY_USER_PHONE, null)
    }

    /**
     * Выйти из аккаунта
     */
    fun signOut() {
        auth.signOut()
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_PHONE)
            .apply()
    }

    /**
     * Получить или создать ID для текущего сеанса
     * Работает даже без Firebase
     */
    fun getOrCreateUserId(): String {
        // Сначала проверяем Firebase
        auth.currentUser?.uid?.let { return it }

        // Затем локальное хранилище
        prefs.getString(KEY_USER_ID, null)?.let { return it }

        // Создаём новый локальный ID
        val newId = "local_${UUID.randomUUID()}"
        prefs.edit().putString(KEY_USER_ID, newId).apply()
        return newId
    }
}
