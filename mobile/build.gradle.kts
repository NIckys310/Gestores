plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.gestores.app"
    compileSdk = 35
    defaultConfig {
        // El reloj y el celular DEBEN tener el mismo applicationId para hablar entre sí
        applicationId = "com.gestores.app"
        minSdk = 26
        targetSdk = 33
        versionCode = 2
        versionName = "2.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    // Comunicación reloj <-> celular (Wearable Data Layer)
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.compose.material3:material3")
    // Botones de música del reloj (Honor, Xiaomi, Amazfit…) como controles extra
    implementation("androidx.media:media:1.7.0")
    // Leer el QR del PC con el escáner de Google (no necesita permiso de cámara)
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("androidx.compose.material:material-icons-extended")
}
