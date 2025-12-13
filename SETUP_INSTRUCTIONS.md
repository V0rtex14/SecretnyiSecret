# Sakta - Инструкции по установке и настройке

## Описание проекта

**Sakta** — приложение для безопасной навигации по городу Бишкек. Предназначено для женщин, студентов, людей, которые ходят одни ночью, и родителей, следящих за детьми.

### Основные функции:
- Безопасные маршруты с учётом зон риска
- Карта с зонами опасности
- Режим сопровождения "Проводить домой"
- SOS-система с экстренным вызовом
- Отслеживание детей
- Доверенные контакты

---

## Требования

### Системные требования:
- Android Studio Arctic Fox (2020.3.1) или новее
- JDK 11 или выше
- Android SDK 24+ (минимальная версия)
- Android SDK 34 (целевая версия)
- Gradle 8.x

### Устройство для тестирования:
- Android 7.0 (API 24) или выше
- GPS-модуль
- Доступ к интернету

---

## Зависимости (build.gradle)

Убедитесь, что в файле `app/build.gradle` присутствуют следующие зависимости:

```gradle
dependencies {
    // Core Android
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.activity:activity-ktx:1.8.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'

    // Material Design 3
    implementation 'com.google.android.material:material:1.11.0'

    // OSMDroid (OpenStreetMap)
    implementation 'org.osmdroid:osmdroid-android:6.1.18'

    // OSMBonusPack (маршруты OSRM)
    implementation 'com.github.MKergall:osmbonuspack:6.9.0'

    // Google Play Services Location
    implementation 'com.google.android.gms:play-services-location:21.0.1'

    // Kotlin Coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'

    // JSON
    implementation 'com.google.code.gson:gson:2.10.1'
}
```

### Репозитории (settings.gradle или build.gradle)

Добавьте JitPack для OSMBonusPack:

```gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

---

## Структура проекта

```
app/src/main/
├── java/com/example/saktahahathonv1/
│   ├── MainActivity.kt              # Главный экран с картой
│   ├── auth/
│   │   ├── LoginActivity.kt         # Вход
│   │   ├── RegisterActivity.kt      # Регистрация
│   │   └── AuthManager.kt           # Управление авторизацией
│   ├── sos/
│   │   └── SosActivity.kt           # Экран SOS Emergency
│   ├── saferoute/
│   │   └── SafeRouteActivity.kt     # Безопасный маршрут
│   ├── escort/
│   │   └── EscortModeActivity.kt    # Режим сопровождения
│   ├── tracking/
│   │   └── TrackingActivity.kt      # Отслеживание детей
│   ├── history/
│   │   └── HistoryActivity.kt       # История маршрутов
│   └── profile/
│       └── ProfileActivity.kt       # Профиль и настройки
├── res/
│   ├── layout/                      # XML разметки экранов
│   ├── drawable/                    # Иконки и фоны
│   ├── values/
│   │   ├── colors.xml               # Цветовая схема
│   │   ├── strings.xml              # Текстовые ресурсы
│   │   ├── styles.xml               # Стили компонентов
│   │   └── themes.xml               # Темы приложения
│   └── menu/
│       └── bottom_navigation_menu.xml
└── AndroidManifest.xml
```

---

## Установка

### 1. Клонирование репозитория

```bash
git clone <repository-url>
cd SaktaHAHATHONV1
```

### 2. Открытие в Android Studio

1. Откройте Android Studio
2. File → Open → выберите папку проекта
3. Дождитесь синхронизации Gradle

### 3. Синхронизация зависимостей

```bash
./gradlew build
```

или в Android Studio: File → Sync Project with Gradle Files

### 4. Запуск

1. Подключите устройство или запустите эмулятор
2. Нажмите Run (Shift+F10)

---

## Разрешения приложения

Приложение запрашивает следующие разрешения:

| Разрешение | Назначение |
|------------|------------|
| `INTERNET` | Загрузка карт OSM |
| `ACCESS_FINE_LOCATION` | Точная геолокация |
| `ACCESS_COARSE_LOCATION` | Приблизительная геолокация |
| `ACCESS_BACKGROUND_LOCATION` | Фоновая геолокация |
| `VIBRATE` | Вибрация при SOS |
| `CALL_PHONE` | Звонок в экстренные службы |
| `SEND_SMS` | Отправка SMS контактам |
| `POST_NOTIFICATIONS` | Уведомления |

---

## Тестовые данные

### Демо-аккаунт:
- Email: любой (создайте при регистрации)
- Пароль: любой

### Демо-локации (Бишкек):
- Центр: 42.8746° N, 74.5698° E
- Ала-Тоо (площадь): 42.8754° N, 74.6094° E

### Зоны риска (демо):
- Высокий риск: заброшенные стройки, тёмные переулки
- Средний риск: отдалённые районы
- Низкий риск: освещённые улицы, центр города

---

## Цветовая схема

### Основные цвета:
- Primary: `#6C5CE7` (фиолетовый)
- Background: `#0D0D1A` (тёмно-синий)
- Surface: `#1A1A2E` (карточки)
- SOS: `#FF3B30` (красный)

### Статусы:
- Safe (зелёный): `#34C759`
- Warning (жёлтый): `#FFD60A`
- Danger (красный): `#FF3B30`

---

## Известные ограничения (MVP)

1. **Маршруты**: используется публичный OSRM сервер, возможны задержки
2. **Зоны риска**: статичные демо-данные для Бишкека
3. **Отслеживание детей**: симуляция (нет реального трекинга)
4. **Запись аудио**: визуальная симуляция
5. **SMS/Звонки**: требуют реальных разрешений от пользователя

---

## Сборка релизной версии

```bash
./gradlew assembleRelease
```

APK будет в: `app/build/outputs/apk/release/`

---

## Команда

**Amanat Team** — Хакатон 2025

---

## Контакты

По вопросам разработки обращайтесь к команде проекта.
