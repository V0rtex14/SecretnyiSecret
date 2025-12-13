package com.example.saktahahathonv1.firebase

import android.content.Context
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Менеджер режима сопровождения через Firebase Realtime Database
 * Обеспечивает реальное отслеживание между устройствами
 */
class FirebaseEscortManager(private val context: Context) {

    private val database: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance().apply {
            setPersistenceEnabled(true) // Офлайн поддержка
        }
    }
    private val escortsRef: DatabaseReference by lazy { database.getReference("escorts") }
    private val authHelper: FirebaseAuthHelper by lazy { FirebaseAuthHelper.getInstance(context) }

    private var currentEscortId: String? = null
    private var locationListener: ValueEventListener? = null
    private var statusListener: ValueEventListener? = null

    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_SOS = "sos"
        const val STATUS_PAUSED = "paused"

        @Volatile
        private var instance: FirebaseEscortManager? = null

        fun getInstance(context: Context): FirebaseEscortManager {
            return instance ?: synchronized(this) {
                instance ?: FirebaseEscortManager(context.applicationContext).also { instance = it }
            }
        }
    }

    // =================== DATA CLASSES ===================

    data class EscortSession(
        val id: String = "",
        val ownerId: String = "",
        val ownerName: String = "",
        val status: String = STATUS_ACTIVE,
        val createdAt: Long = System.currentTimeMillis(),
        val route: RouteData? = null,
        val observers: Map<String, ObserverInfo> = emptyMap()
    )

    data class RouteData(
        val startLat: Double = 0.0,
        val startLon: Double = 0.0,
        val endLat: Double = 0.0,
        val endLon: Double = 0.0,
        val startAddress: String = "",
        val endAddress: String = ""
    )

    data class LocationUpdate(
        val lat: Double = 0.0,
        val lon: Double = 0.0,
        val accuracy: Float = 0f,
        val timestamp: Long = System.currentTimeMillis(),
        val speed: Float = 0f,
        val bearing: Float = 0f
    )

    data class ObserverInfo(
        val name: String = "",
        val joinedAt: Long = System.currentTimeMillis()
    )

    // =================== СОЗДАТЕЛЬ СЕССИИ ===================

    /**
     * Создать новую сессию сопровождения
     * @return ID сессии для отправки наблюдателям
     */
    suspend fun startEscortSession(
        route: RouteData? = null,
        observerIds: List<String> = emptyList()
    ): Result<String> {
        return try {
            val escortId = UUID.randomUUID().toString().take(8).uppercase()
            val userId = authHelper.getOrCreateUserId()
            val userName = authHelper.getUserName()

            val session = EscortSession(
                id = escortId,
                ownerId = userId,
                ownerName = userName,
                status = STATUS_ACTIVE,
                createdAt = System.currentTimeMillis(),
                route = route
            )

            escortsRef.child(escortId).setValue(session).await()

            // Добавить начальную локацию (пустую)
            escortsRef.child(escortId).child("location").setValue(
                LocationUpdate(timestamp = System.currentTimeMillis())
            ).await()

            currentEscortId = escortId
            Result.success(escortId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Обновить местоположение в активной сессии
     */
    suspend fun updateLocation(lat: Double, lon: Double, accuracy: Float = 0f, speed: Float = 0f, bearing: Float = 0f) {
        currentEscortId?.let { escortId ->
            val update = LocationUpdate(
                lat = lat,
                lon = lon,
                accuracy = accuracy,
                timestamp = System.currentTimeMillis(),
                speed = speed,
                bearing = bearing
            )
            try {
                escortsRef.child(escortId).child("location").setValue(update).await()
            } catch (e: Exception) {
                // Игнорируем ошибки - будет повтор
            }
        }
    }

    /**
     * Обновить местоположение (callback версия для Java/простоты)
     */
    fun updateLocationAsync(lat: Double, lon: Double, accuracy: Float = 0f) {
        currentEscortId?.let { escortId ->
            val update = LocationUpdate(
                lat = lat,
                lon = lon,
                accuracy = accuracy,
                timestamp = System.currentTimeMillis()
            )
            escortsRef.child(escortId).child("location").setValue(update)
        }
    }

    /**
     * Активировать SOS в текущей сессии
     */
    suspend fun activateSOS(): Result<Unit> {
        return try {
            currentEscortId?.let { escortId ->
                escortsRef.child(escortId).child("status").setValue(STATUS_SOS).await()
                Result.success(Unit)
            } ?: Result.failure(Exception("Нет активной сессии"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Завершить сессию сопровождения
     */
    suspend fun endEscortSession(): Result<Unit> {
        return try {
            currentEscortId?.let { escortId ->
                escortsRef.child(escortId).child("status").setValue(STATUS_COMPLETED).await()
                currentEscortId = null
                Result.success(Unit)
            } ?: Result.failure(Exception("Нет активной сессии"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Получить текущий ID сессии
     */
    fun getCurrentEscortId(): String? = currentEscortId

    // =================== НАБЛЮДАТЕЛЬ ===================

    /**
     * Присоединиться к сессии как наблюдатель
     */
    suspend fun joinAsObserver(escortId: String): Result<EscortSession> {
        return try {
            val snapshot = escortsRef.child(escortId).get().await()
            if (!snapshot.exists()) {
                return Result.failure(Exception("Сессия не найдена"))
            }

            val session = snapshot.getValue(EscortSession::class.java)
                ?: return Result.failure(Exception("Ошибка чтения сессии"))

            if (session.status == STATUS_COMPLETED) {
                return Result.failure(Exception("Сессия уже завершена"))
            }

            // Добавить себя как наблюдателя
            val userId = authHelper.getOrCreateUserId()
            val userName = authHelper.getUserName()

            escortsRef.child(escortId).child("observers").child(userId).setValue(
                ObserverInfo(name = userName, joinedAt = System.currentTimeMillis())
            ).await()

            currentEscortId = escortId
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Подписаться на обновления локации (Flow)
     */
    fun observeLocation(escortId: String): Flow<LocationUpdate> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val location = snapshot.getValue(LocationUpdate::class.java)
                location?.let { trySend(it) }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        escortsRef.child(escortId).child("location").addValueEventListener(listener)
        locationListener = listener

        awaitClose {
            escortsRef.child(escortId).child("location").removeEventListener(listener)
        }
    }

    /**
     * Подписаться на изменения статуса (Flow)
     */
    fun observeStatus(escortId: String): Flow<String> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.getValue(String::class.java)
                status?.let { trySend(it) }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        escortsRef.child(escortId).child("status").addValueEventListener(listener)
        statusListener = listener

        awaitClose {
            escortsRef.child(escortId).child("status").removeEventListener(listener)
        }
    }

    /**
     * Подписаться на обновления локации (callback версия)
     */
    fun observeLocationWithCallback(
        escortId: String,
        onLocation: (LocationUpdate) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val location = snapshot.getValue(LocationUpdate::class.java)
                location?.let { onLocation(it) }
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.toException())
            }
        }

        escortsRef.child(escortId).child("location").addValueEventListener(listener)
        locationListener = listener
    }

    /**
     * Подписаться на изменения статуса (callback версия)
     */
    fun observeStatusWithCallback(
        escortId: String,
        onStatus: (String) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.getValue(String::class.java)
                status?.let { onStatus(it) }
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.toException())
            }
        }

        escortsRef.child(escortId).child("status").addValueEventListener(listener)
        statusListener = listener
    }

    /**
     * Покинуть сессию как наблюдатель
     */
    fun leaveAsObserver() {
        currentEscortId?.let { escortId ->
            val userId = authHelper.getOrCreateUserId()
            escortsRef.child(escortId).child("observers").child(userId).removeValue()

            // Убрать listeners
            locationListener?.let {
                escortsRef.child(escortId).child("location").removeEventListener(it)
            }
            statusListener?.let {
                escortsRef.child(escortId).child("status").removeEventListener(it)
            }

            locationListener = null
            statusListener = null
            currentEscortId = null
        }
    }

    // =================== УТИЛИТЫ ===================

    /**
     * Проверить существование сессии
     */
    suspend fun sessionExists(escortId: String): Boolean {
        return try {
            val snapshot = escortsRef.child(escortId).get().await()
            snapshot.exists()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Получить информацию о сессии
     */
    suspend fun getSessionInfo(escortId: String): EscortSession? {
        return try {
            val snapshot = escortsRef.child(escortId).get().await()
            snapshot.getValue(EscortSession::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Очистить все listeners и сбросить состояние
     */
    fun cleanup() {
        currentEscortId?.let { escortId ->
            locationListener?.let {
                escortsRef.child(escortId).child("location").removeEventListener(it)
            }
            statusListener?.let {
                escortsRef.child(escortId).child("status").removeEventListener(it)
            }
        }
        locationListener = null
        statusListener = null
        currentEscortId = null
    }
}
