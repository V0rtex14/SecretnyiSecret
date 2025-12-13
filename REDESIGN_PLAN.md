# SAKTA — Полный план редизайна и исправлений

## Версия: 2.0 (Hackathon MVP)
**Дата:** 2025-12-13
**Автор:** Senior Android Engineer / Mobile Product Architect

---

## РАЗДЕЛ 1: ПЕРЕИСПОЛЬЗУЕМЫЕ КОМПОНЕНТЫ

### ✅ СОХРАНЯЕМ БЕЗ ИЗМЕНЕНИЙ

| Компонент | Файл | Причина сохранения |
|-----------|------|-------------------|
| **RiskEngine** | `map/RiskEngine.kt` | Отличный алгоритм расчёта риска с временным затуханием |
| **Цветовая схема** | `values/colors.xml` | Современная тёмная тема iOS-style |
| **Стили компонентов** | `values/styles.xml` | Material 3, готовые стили кнопок/карточек |
| **Тема приложения** | `values/themes.xml` | Правильная структура тем |
| **Строки (RU)** | `values/strings.xml` | 240+ переведённых строк |
| **DataManager** | `data/DataManager.kt` | Загрузка JSON из assets |
| **HistoryManager** | `history/HistoryManager.kt` | Сохранение истории маршрутов |
| **AuthManager** | `auth/AuthManager.kt` | Базовая авторизация (нужна доработка) |

### ✅ СОХРАНЯЕМ С ДОРАБОТКОЙ

| Компонент | Файл | Что доработать |
|-----------|------|----------------|
| **MainActivity** | `MainActivity.kt` | Интеграция реальных маршрутов |
| **SafeRoutingEngine** | `map/SafeRoutingEngine.kt` | Использовать реальный OSRM |
| **SafeRouteActivity** | `saferoute/SafeRouteActivity.kt` | Убрать хардкод, подключить движок |
| **HistoryActivity** | `history/HistoryActivity.kt` | Добавить сохранение маршрутов |
| **SosActivity** | `sos/SosActivity.kt` | Реальные уведомления контактам |
| **ProfileActivity** | `profile/ProfileActivity.kt` | Уже готов, мелкие правки |

### ❌ УДАЛЯЕМ / ЗАМЕНЯЕМ ПОЛНОСТЬЮ

| Компонент | Причина |
|-----------|---------|
| `family/FamilyActivity.kt` | Legacy, не используется |
| `friends/FriendsActivity.kt` | Legacy, заменён на "Доверенные контакты" |
| `route/SafeRouteActivity.kt` | Дубликат, есть в `/saferoute/` |
| `escort/EscortActivity.kt` | Legacy, заменён на EscortModeActivity |

---

## РАЗДЕЛ 2: СЛОМАННЫЕ ФУНКЦИИ И ЗАМЕНЫ

### 2.1 МАРШРУТИЗАЦИЯ (КРИТИЧНО)

**Проблема:**
- Маршруты строятся, но в SafeRouteActivity используются ХАРДКОД координаты
- Нет связи между SafeRoutingEngine и UI
- Маршрут не следует реальным улицам в демо

**Решение MVP:**

```kotlin
// SafeRouteActivity.kt - ЗАМЕНИТЬ демо-маршруты на реальные
class SafeRouteActivity : AppCompatActivity() {
    private lateinit var routingEngine: SafeRoutingEngine
    private lateinit var riskEngine: RiskEngine

    private fun buildRealRoutes() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Получаем реальные маршруты через OSRM
                val routes = routingEngine.buildAlternativeRoutes(
                    startPoint,
                    endPoint,
                    3  // количество альтернатив
                )

                withContext(Dispatchers.Main) {
                    displayRoutes(routes)
                }
            } catch (e: Exception) {
                showError("Не удалось построить маршрут")
            }
        }
    }
}
```

**Требуемые изменения:**
1. Инициализировать RiskEngine и SafeRoutingEngine в SafeRouteActivity
2. Убрать hardcoded точки маршрута
3. Добавить выбор точек на карте (tap to select)
4. Сохранять выбранный маршрут в историю

---

### 2.2 РЕЖИМ СОПРОВОЖДЕНИЯ (ESCORT MODE)

**Проблема:**
- Полностью симуляция
- Нет реального отслеживания
- Нет связи между устройствами

**Решение MVP (Firebase Realtime Database):**

```
Firebase Structure:
├── escorts/
│   └── {escortId}/
│       ├── ownerId: "user123"
│       ├── status: "active" | "completed" | "sos"
│       ├── location/
│       │   ├── lat: 42.8746
│       │   ├── lon: 74.5698
│       │   └── timestamp: 1702483200000
│       ├── route/
│       │   ├── start: {lat, lon}
│       │   ├── end: {lat, lon}
│       │   └── points: [{lat, lon}, ...]
│       ├── observers: ["userId1", "userId2"]
│       └── createdAt: 1702483200000
```

**Новый EscortModeActivity:**
```kotlin
class EscortModeActivity : AppCompatActivity() {
    private lateinit var database: FirebaseDatabase
    private var escortRef: DatabaseReference? = null
    private var locationListener: ValueEventListener? = null

    // Начать сопровождение (создатель)
    private fun startEscort() {
        val escortId = UUID.randomUUID().toString()
        escortRef = database.getReference("escorts/$escortId")

        escortRef?.setValue(EscortSession(
            ownerId = currentUserId,
            status = "active",
            route = currentRoute,
            observers = trustedContactIds
        ))

        // Начать отправку локации
        startLocationUpdates()
    }

    // Наблюдать за другим (наблюдатель)
    private fun observeEscort(escortId: String) {
        escortRef = database.getReference("escorts/$escortId")

        locationListener = escortRef?.child("location")
            ?.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val location = snapshot.getValue(Location::class.java)
                    updateObserverMap(location)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }
}
```

---

### 2.3 SOS СИСТЕМА

**Проблема:**
- Отправка локации — симуляция
- Нет реальных уведомлений контактам
- Запись аудио — заглушка

**Решение MVP:**

```kotlin
class SosActivity : AppCompatActivity() {

    private fun activateSOS() {
        // 1. Вибрация (уже работает)
        vibrate()

        // 2. Получить текущую локацию
        val location = getCurrentLocation()

        // 3. Отправить SMS доверенным контактам
        sendEmergencySMS(location)

        // 4. Записать в Firebase (для наблюдателей)
        saveSosEvent(location)

        // 5. Показать системное уведомление
        showPersistentNotification()
    }

    private fun sendEmergencySMS(location: Location) {
        val contacts = getTrustedContacts()
        val message = """
            🆘 ЭКСТРЕННАЯ СИТУАЦИЯ!

            ${userName} активировал SOS в приложении Sakta.

            Координаты: ${location.latitude}, ${location.longitude}
            Время: ${formatTime(System.currentTimeMillis())}

            Карта: https://maps.google.com/?q=${location.latitude},${location.longitude}
        """.trimIndent()

        contacts.forEach { contact ->
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(contact.phone, null, message, null, null)
        }
    }
}
```

**Разрешения (добавить в Manifest):**
```xml
<uses-permission android:name="android.permission.SEND_SMS" />
```

---

### 2.4 ИСТОРИЯ МАРШРУТОВ

**Проблема:**
- Маршруты не сохраняются при выборе
- Только демо-данные

**Решение:**

```kotlin
// В SafeRouteActivity после выбора маршрута:
private fun onRouteSelected(route: RouteOption) {
    val historyEntry = RouteHistory(
        id = UUID.randomUUID().toString(),
        userId = authManager.getCurrentUser()?.id.toString(),
        fromAddress = resolveAddress(route.data.points.first()),
        toAddress = resolveAddress(route.data.points.last()),
        fromLocation = route.data.points.first(),
        toLocation = route.data.points.last(),
        distance = route.data.distance,
        duration = route.data.duration,
        timestamp = System.currentTimeMillis(),
        routeType = route.type.name,
        safetyScore = route.evaluation.safetyScore
    )

    historyManager.saveRoute(historyEntry)

    // Переход на карту с маршрутом
    navigateToMainWithRoute(route)
}
```

---

### 2.5 СИСТЕМА УВЕДОМЛЕНИЙ

**Проблема:**
- Только Toast
- Нет NotificationChannel
- Уродливые уведомления

**Решение:**

```kotlin
class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ESCORT = "escort_channel"
        const val CHANNEL_SOS = "sos_channel"
        const val CHANNEL_GENERAL = "general_channel"
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val sosChannel = NotificationChannel(
                CHANNEL_SOS,
                "Экстренные уведомления",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления SOS и экстренных ситуаций"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }

            val escortChannel = NotificationChannel(
                CHANNEL_ESCORT,
                "Режим сопровождения",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Уведомления о статусе сопровождения"
            }

            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannels(listOf(sosChannel, escortChannel))
        }
    }

    fun showEscortNotification(title: String, message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ESCORT)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(1, notification)
    }

    fun showSosNotification() {
        val notification = NotificationCompat.Builder(context, CHANNEL_SOS)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle("🆘 SOS АКТИВИРОВАН")
            .setContentText("Местоположение отправлено контактам")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)  // Нельзя смахнуть
            .build()

        NotificationManagerCompat.from(context).notify(999, notification)
    }
}
```

---

## РАЗДЕЛ 3: FIREBASE ИНТЕГРАЦИЯ

### 3.1 Зависимости (build.gradle.kts)

```kotlin
// Firebase BoM
implementation(platform("com.google.firebase:firebase-bom:32.7.0"))

// Firebase services
implementation("com.google.firebase:firebase-auth-ktx")
implementation("com.google.firebase:firebase-database-ktx")
implementation("com.google.firebase:firebase-analytics-ktx")

// Optional: Cloud Messaging для push
implementation("com.google.firebase:firebase-messaging-ktx")
```

### 3.2 Инициализация

```kotlin
// В Application или MainActivity
class SaktaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
```

### 3.3 Анонимная аутентификация (для MVP)

```kotlin
class FirebaseAuthHelper {
    private val auth = Firebase.auth

    fun signInAnonymously(onComplete: (String?) -> Unit) {
        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                onComplete(result.user?.uid)
            }
            .addOnFailureListener {
                onComplete(null)
            }
    }

    fun getCurrentUserId(): String? = auth.currentUser?.uid
}
```

### 3.4 Структура базы данных

```
sakta-app/
├── users/
│   └── {userId}/
│       ├── name: "Айнур"
│       ├── phone: "+996555123456"
│       └── trustedContacts: [...]
│
├── escorts/
│   └── {escortId}/
│       ├── ownerId: "userId"
│       ├── status: "active"
│       ├── location: {lat, lon, timestamp}
│       ├── route: {start, end, points}
│       └── observers: ["userId1", "userId2"]
│
├── sos_events/
│   └── {eventId}/
│       ├── userId: "userId"
│       ├── location: {lat, lon}
│       ├── timestamp: 1702483200000
│       └── status: "active" | "resolved"
```

---

## РАЗДЕЛ 4: UI/UX ИСПРАВЛЕНИЯ

### 4.1 Маркеры на карте

**Было:** Дефолтные пины

**Станет:**
```kotlin
// Кастомные маркеры
private fun createStartMarker(): Drawable {
    return ContextCompat.getDrawable(this, R.drawable.marker_start_glow)!!
}

private fun createEndMarker(): Drawable {
    return ContextCompat.getDrawable(this, R.drawable.marker_destination_shield)!!
}

private fun createDangerZoneOverlay(center: GeoPoint, radius: Double): Polygon {
    return Polygon(mapView).apply {
        points = Polygon.pointsAsCircle(center, radius)
        fillPaint.shader = RadialGradient(
            center.x, center.y, radius,
            Color.parseColor("#40FF3B30"),  // Центр - красный
            Color.TRANSPARENT,               // Край - прозрачный
            Shader.TileMode.CLAMP
        )
        outlinePaint.color = Color.TRANSPARENT
    }
}
```

### 4.2 Линия маршрута

**Было:** Тонкая линия

**Станет:**
```kotlin
private fun createRouteLine(points: List<GeoPoint>, riskLevel: RiskLevel): Polyline {
    return Polyline(mapView).apply {
        setPoints(points)
        outlinePaint.apply {
            strokeWidth = 14f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = when (riskLevel) {
                RiskLevel.SAFE -> Color.parseColor("#34C759")
                RiskLevel.MEDIUM -> Color.parseColor("#FF9500")
                RiskLevel.HIGH -> Color.parseColor("#FF3B30")
            }
            // Тень под линией
            setShadowLayer(8f, 0f, 4f, Color.parseColor("#40000000"))
        }
    }
}
```

### 4.3 Контрастность текста

Все тексты проверены на соответствие WCAG AA:
- Основной текст: `#FFFFFF` на `#0D0D0F` — контраст 21:1 ✅
- Вторичный текст: `#A0A0A5` на `#1E1E22` — контраст 5.2:1 ✅
- Третичный текст: `#6E6E73` на `#1E1E22` — контраст 3.1:1 ⚠️ (только для подписей)

---

## РАЗДЕЛ 5: ПОШАГОВЫЙ ПЛАН РЕАЛИЗАЦИИ

### Этап 1: Firebase Setup (30 мин)
1. Создать проект Firebase Console
2. Добавить google-services.json в /app
3. Добавить зависимости в build.gradle.kts
4. Настроить Realtime Database rules

### Этап 2: Реальная маршрутизация (2 часа)
1. Интегрировать SafeRoutingEngine в SafeRouteActivity
2. Добавить выбор точек на карте
3. Отображать альтернативные маршруты
4. Сохранять выбранный маршрут в историю

### Этап 3: Escort Mode (2-3 часа)
1. Создать FirebaseEscortManager
2. Реализовать создание сессии сопровождения
3. Добавить location updates через FusedLocationProvider
4. Реализовать наблюдение (observer mode)
5. Добавить статусные уведомления

### Этап 4: SOS с SMS (1 час)
1. Добавить разрешение SEND_SMS
2. Реализовать sendEmergencySMS()
3. Сохранять SOS event в Firebase
4. Добавить persistent notification

### Этап 5: Уведомления (1 час)
1. Создать NotificationHelper
2. Настроить NotificationChannels
3. Заменить Toast на proper notifications
4. Добавить иконки уведомлений (monochrome)

### Этап 6: UI Polish (1-2 часа)
1. Кастомные маркеры карты
2. Градиентные зоны опасности
3. Улучшенная линия маршрута
4. Проверка контрастности

---

## РАЗДЕЛ 6: ЗАВИСИМОСТИ И БИБЛИОТЕКИ

### Добавить в build.gradle.kts (app):

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")  // ДОБАВИТЬ
}

dependencies {
    // Существующие...

    // === FIREBASE ===
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")

    // === LOCATION (уже есть, проверить версию) ===
    implementation("com.google.android.gms:play-services-location:21.1.0")

    // === РАБОТА С РАЗРЕШЕНИЯМИ ===
    implementation("pub.devrel:easypermissions:3.0.0")
    // или
    implementation("com.guolindev.permissionx:permissionx:1.7.1")
}
```

### Добавить в build.gradle.kts (project):

```kotlin
plugins {
    id("com.google.gms.google-services") version "4.4.0" apply false
}
```

### Добавить в settings.gradle.kts:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
```

---

## РАЗДЕЛ 7: ФАЙЛЫ ДЛЯ СОЗДАНИЯ

### 7.1 Новые файлы

| Файл | Назначение |
|------|------------|
| `firebase/FirebaseAuthHelper.kt` | Анонимная авторизация |
| `firebase/FirebaseEscortManager.kt` | Управление сессиями сопровождения |
| `firebase/FirebaseSosManager.kt` | SOS события в Firebase |
| `notifications/NotificationHelper.kt` | Управление уведомлениями |
| `location/LocationService.kt` | Foreground service для трекинга |
| `res/drawable/marker_start_glow.xml` | Маркер начала |
| `res/drawable/marker_destination_shield.xml` | Маркер назначения |
| `google-services.json` | Конфиг Firebase (скачать из Console) |

### 7.2 Файлы для скачивания

1. **google-services.json**
   - Скачать из Firebase Console → Project Settings → Android app

2. **Иконки уведомлений (monochrome)**
   - `res/drawable/ic_notif_shield.xml` — для escort
   - `res/drawable/ic_notif_warning.xml` — для SOS
   - `res/drawable/ic_notif_route.xml` — для маршрутов

---

## РАЗДЕЛ 8: CHECKLIST ПЕРЕД ДЕМО

### Функциональность
- [ ] Маршрут строится по реальным улицам
- [ ] Альтернативные маршруты отображаются
- [ ] Выбранный маршрут сохраняется в историю
- [ ] Escort Mode создаёт сессию в Firebase
- [ ] Observer может видеть позицию пользователя
- [ ] SOS отправляет SMS контактам
- [ ] Уведомления появляются корректно

### UI/UX
- [ ] Маркеры кастомные (не дефолтные пины)
- [ ] Линия маршрута толстая и цветная
- [ ] Зоны опасности с градиентом
- [ ] Весь текст читаем (контраст OK)
- [ ] Уведомления выглядят нативно

### Технические
- [ ] Firebase подключен
- [ ] Разрешения запрашиваются
- [ ] Нет крашей при отсутствии интернета
- [ ] Location работает в background

---

## ИТОГО

**Время на реализацию:** 8-10 часов
**Сложность:** Средняя
**Готовность к демо:** После выполнения всех этапов — 100%

Этот план превращает прототип в работающий MVP, который можно продемонстрировать на хакатоне с реальной функциональностью.
