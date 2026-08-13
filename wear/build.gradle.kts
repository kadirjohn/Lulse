plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.kadirjohn.lulse.wear"
    compileSdk = 36

    defaultConfig {
        // Linked Wear paketleme: telefonla aynı applicationId (docs 04).
        applicationId = "com.kadirjohn.lulse"
        minSdk = 30 // Wear OS 3+; Galaxy Watch6 Classic destekli
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Cihaza kurulabilir release APK için debug keystore (phone app ile aynı).
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }

    // WearableListenerService intent-filter (BIND_LISTENER) deprecated uyarısı —
    // Wear OS hâlâ service'i keşfetmek için bu intent-filter'ı kullanıyor.
    lint {
        abortOnError = false
        disable += "WearableBindListener"
    }
}

dependencies {
    // Wear OS Data Layer — phone ile iletişim.
    implementation(libs.play.services.wearable)
    // Compose for Wear OS — round display, material components.
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    // Paylaşılan protocol modelleri (mesaj serileştirme).
    implementation(project(":shared"))
    implementation(libs.kotlinx.serialization.json)

    // Samsung Health Sensor SDK — manuel indirilen AAR (docs 02).
    // wear/libs/samsung-health-sensor-api.aar yoksa Gradle sync patlamasın:
    // stub HealthSensorSource ile derlenir, gerçek HR için AAR gerekir.
    val samsungAar = file("libs/samsung-health-sensor-api.aar")
    if (samsungAar.exists()) {
        implementation(files(samsungAar))
    }
}