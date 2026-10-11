package com.gestores.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Datos pequeños que se muestran en la esfera del reloj. */
data class Complications(
    val battery: Int? = null,
    val charging: Boolean = false,
    val steps: Int? = null,          // desde que se encendió el celular (o desde medianoche si se puede)
    val tempC: Int? = null,
    val weather: String = "",        // emoji del clima
    val nextAlarm: String? = null,
)

fun Context.has(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

@Composable
fun rememberComplications(): Complications {
    val ctx = LocalContext.current
    var data by remember { mutableStateOf(Complications()) }

    // Batería y alarma: cada minuto
    LaunchedEffect(Unit) {
        while (true) {
            val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = b?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1) }
            val charging = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1).let { it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL }
            val alarm = ctx.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime
                ?.let { SimpleDateFormat("EEE HH:mm", Locale("es")).format(Date(it)) }
            data = data.copy(battery = level, charging = charging, nextAlarm = alarm)
            delay(60_000)
        }
    }

    // Pasos del día (sensor contador de pasos)
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val total = e.values[0].toInt()
                val p = ctx.prefs()
                val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                if (p.getString("steps_day", null) != today) p.edit().putString("steps_day", today).putInt("steps_base", total).apply()
                data = data.copy(steps = (total - p.getInt("steps_base", total)).coerceAtLeast(0))
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null && ctx.has(Manifest.permission.ACTIVITY_RECOGNITION)) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm?.unregisterListener(listener) }
    }

    // Clima (Open-Meteo, gratis y sin clave): cada 30 minutos
    LaunchedEffect(Unit) {
        while (true) {
            weatherNow(ctx)?.let { (t, e) -> data = data.copy(tempC = t, weather = e) }
            delay(30 * 60_000)
        }
    }
    return data
}

@SuppressLint("MissingPermission")
private suspend fun weatherNow(ctx: Context): Pair<Int, String>? {
    if (!ctx.has(Manifest.permission.ACCESS_COARSE_LOCATION)) return null
    val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
    val loc = lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time } ?: return null
    val url = "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m,weather_code".format(Locale.US, loc.latitude, loc.longitude)
    val full = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { java.net.URL(url).readText() }.getOrNull() } ?: return null
    val obj = runCatching { json.parseToJsonElement(full) }.getOrNull() ?: return null
    val cur = (obj as? kotlinx.serialization.json.JsonObject)?.get("current") as? kotlinx.serialization.json.JsonObject ?: return null
    val t = cur["temperature_2m"]?.toString()?.toDoubleOrNull()?.toInt() ?: return null
    val code = cur["weather_code"]?.toString()?.toIntOrNull() ?: 0
    val e = when (code) { 0 -> "☀️"; 1, 2 -> "🌤️"; 3 -> "☁️"; 45, 48 -> "🌫️"; in 51..67 -> "🌧️"; in 71..77 -> "❄️"; in 80..82 -> "🌦️"; in 95..99 -> "⛈️"; else -> "🌡️" }
    return t to e
}
