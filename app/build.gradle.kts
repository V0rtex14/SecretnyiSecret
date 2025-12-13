// app/build.gradle.kts

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
    // Firebase - раскомментировать после добавления google-services.json
    // id("com.google.gms.google-services")
}

android {
    namespace = "com.example.saktahahathonv1"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.saktahahathonv1"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.12.0")

    // Lifecycle & Coroutines
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // OSMDroid - Основная библиотека для карт
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // OSMDroid Bonus Pack - Для роутинга (OSRM)
    implementation("com.github.MKergall:osmbonuspack:6.9.0")

    // Retrofit - Для API запросов
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Gson - Парсинг JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // Room - Локальная БД для кеширования
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")

    // Location services
    implementation("com.google.android.gms:play-services-location:21.1.0")

    // Image loading (для кастомных иконок)
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // ==================== FIREBASE ====================
    // Firebase BoM (Bill of Materials) - управляет версиями
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))

    // Firebase Auth (анонимная авторизация для MVP)
    implementation("com.google.firebase:firebase-auth-ktx")

    // Firebase Realtime Database (для Escort Mode)
    implementation("com.google.firebase:firebase-database-ktx")

    // Firebase Analytics (опционально)
    implementation("com.google.firebase:firebase-analytics-ktx")

    // ==================== TESTING ====================
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
