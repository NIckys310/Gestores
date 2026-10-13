package com.gestores.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * GESTORES · el celular simula un smartwatch.
 * La única pantalla es el reloj (WatchHome); todas las apps se abren dentro de él.
 */
class MainActivity : ComponentActivity() {
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Learner.init(this)
        WatchState.init(this)
        Alarms.channels(this)
        Alarms.rescheduleAll(this)
        PhoneBus.sensitivity.value = PhoneGestureService.loadSensitivity(this)
        if (Build.VERSION.SDK_INT >= 33 && !has(Manifest.permission.POST_NOTIFICATIONS)) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)   // para alarmas y temporizador
        }
        setContent {
            var mode by rememberSaveable { mutableStateOf(ThemePrefs.loadMode(this)) }
            val dark = isDarkMode(mode)
            LaunchedEffect(dark) {
                val t = android.graphics.Color.TRANSPARENT
                val bars = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            GestoresTheme(dark) {
                WatchHome(mode, onMode = { mode = it; ThemePrefs.saveMode(this, it) })
            }
        }
    }

    override fun onResume() { super.onResume(); WatchState.visible = true }
    override fun onPause() { super.onPause(); WatchState.visible = false }
}
