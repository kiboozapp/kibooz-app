plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nthb.kibooz"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nthb.kibooz"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Ganti dengan -Pkibooz.apiBaseUrl=... (http:// hanya diizinkan ke 10.0.2.2 di build debug lewat network_security_config)
        val apiBaseUrl = providers.gradleProperty("kibooz.apiBaseUrl")
            .getOrElse("https://kibooz-api.syafahadyan.com/api/v1/")
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")

        // Build internal (demo) boleh membuka layar kamera dengan model pengganti: -Pkibooz.allowPlaceholderModels=true
        val allowPlaceholderModels = providers.gradleProperty("kibooz.allowPlaceholderModels")
            .map { it.toBoolean() }
            .getOrElse(false)
        buildConfigField("boolean", "ALLOW_PLACEHOLDER_MODELS", allowPlaceholderModels.toString())

        // Web Client ID OAuth 2.0 Google untuk Credential Manager: -Pkibooz.googleWebClientId=...
        val googleWebClientId = providers.gradleProperty("kibooz.googleWebClientId")
            .getOrElse("")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")

        // Build selain debug harus https agar token dan kata sandi tidak terkirim tanpa enkripsi. Tugas tanpa
        // nama varian seperti assemble, build dan bundle juga membangun varian rilis, jadi ikut diperiksa.
        val releaseRequested = gradle.startParameter.taskNames.any { task ->
            val name = task.substringAfterLast(':').lowercase()
            val buildsVariant = name.startsWith("assemble") || name.startsWith("bundle") || name.startsWith("package")
            name.contains("release") ||
                name in setOf("assemble", "build", "bundle", "install") ||
                (buildsVariant && !name.contains("debug") && !name.contains("androidtest"))
        }
        check(!releaseRequested || apiBaseUrl.startsWith("https://")) {
            "kibooz.apiBaseUrl harus diawali https:// untuk build rilis, nilai sekarang $apiBaseUrl"
        }
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
        buildConfig = true
    }
    androidResources {
        // Model TFLite harus tidak terkompresi agar bisa di-memory-map
        noCompress += "tflite"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")

    // Kamera dan AI di perangkat (FR-8)
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // Jaringan (FE-01)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.moshi:moshi:1.15.1")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")

    // Penyimpanan sesi (FE-03)
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Pengingat sisa scan sampah pukul 12.00
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // Mendeteksi aplikasi masuk ke latar belakang, untuk pengingat demo setelah aplikasi ditutup
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")

    // Confetti di podium peringkat saat anak Wali masuk 3 besar
    implementation("nl.dionsegijn:konfetti-xml:2.0.4")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
