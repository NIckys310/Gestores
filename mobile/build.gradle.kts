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
        applicationId = "com.gestores.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "4.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // Llave PRIVADA de producción: vive fuera del repositorio y su contraseña nunca se escribe aquí.
    // Se lee de ~/.gradle/gradle.properties (GE_STORE_FILE, GE_STORE_PASSWORD_DPAPI, GE_KEY_ALIAS)
    // o de variables de entorno con el mismo nombre. Ver docs/SIGNING.md
    val keyStoreFile = providers.gradleProperty("GE_STORE_FILE").orElse(providers.environmentVariable("GE_STORE_FILE")).orNull
    signingConfigs {
        if (keyStoreFile != null) create("production") {
            storeFile = file(keyStoreFile)
            storePassword = readKeyPassword("GE")
            keyAlias = providers.gradleProperty("GE_KEY_ALIAS").orElse(providers.environmentVariable("GE_KEY_ALIAS")).get()
            keyPassword = storePassword
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("production")
            isMinifyEnabled = true          // R8: quita código sin usar y lo ofusca
            isShrinkResources = true        // quita imágenes y textos sin usar
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    // Pruebas automáticas en la computadora (sin celular): las clases de Android devuelven valores vacíos
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    // ViewModel: el estado de la pantalla del reloj sobrevive a girar el celular
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Pruebas automáticas
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    // Pruebas de interfaz en el emulador o el celular
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

/**
 * Contraseña de la llave de firma, sin escribirla nunca en texto plano:
 * - En este PC: GE_STORE_PASSWORD_DPAPI guarda la contraseña cifrada con DPAPI de Windows;
 *   solo el mismo usuario de Windows en este mismo equipo puede descifrarla.
 * - En GitHub Actions: la variable secreta GE_STORE_PASSWORD.
 */
fun readKeyPassword(prefix: String): String {
    providers.environmentVariable("${prefix}_STORE_PASSWORD").orNull?.let { return it }
    val encrypted = providers.gradleProperty("${prefix}_STORE_PASSWORD_DPAPI").orNull
        ?: error("Falta ${prefix}_STORE_PASSWORD_DPAPI en ~/.gradle/gradle.properties (ver SIGNING.md)")
    val ps = "Add-Type -AssemblyName System.Security; " +
        "[Text.Encoding]::UTF8.GetString([Security.Cryptography.ProtectedData]::Unprotect(" +
        "[Convert]::FromBase64String('$encrypted'), \$null, 'CurrentUser'))"
    return providers.exec { commandLine("powershell", "-NoProfile", "-NonInteractive", "-Command", ps) }
        .standardOutput.asText.get().trim()
}
