package com.gestores.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Rutas de los mensajes entre reloj y celular. */
object Proto {
    const val PATH_GESTURE = "/gestores/gesture"   // reloj -> celular: "se hizo este gesto"
    const val PATH_ACK = "/gestores/ack"           // celular -> reloj: "esto pasó"
    const val PATH_CONFIG = "/gestores/config"     // celular -> reloj: reglas y datos del PC
}

data class GestureDef(val id: String, val emoji: String, val name: String, val hint: String)

/** Gestos que entiende la app. Los "ia_" los aprende la IA con tus propios movimientos. */
object Gestures {
    val builtIn = listOf(
        GestureDef("alzar", "⬆️", "Alzar rápido", "Sube la mano rápido hacia arriba"),
        GestureDef("bajar", "⬇️", "Bajar rápido", "Baja la mano rápido"),
        GestureDef("girar", "🔄", "Girar muñeca", "Gira la muñeca rápido, como abriendo una perilla"),
        GestureDef("sacudir", "👋", "Sacudir", "Sacude la mano varias veces"),
        GestureDef("doble_golpe", "✊", "Doble golpe", "Da dos golpes secos con la mano (por ejemplo, en la mesa)"),
    )
    val ai = listOf(
        GestureDef("ia_1", "🧠", "Gesto IA 1", "Un movimiento tuyo que la IA aprende"),
        GestureDef("ia_2", "🧠", "Gesto IA 2", "Un movimiento tuyo que la IA aprende"),
        GestureDef("ia_3", "🧠", "Gesto IA 3", "Un movimiento tuyo que la IA aprende"),
    )
    val all = builtIn + ai
    fun get(id: String) = (all + Triggers.all).firstOrNull { it.id == id }
    fun label(id: String) = get(id)?.let { "${it.emoji} ${it.name}" } ?: id
}

/**
 * Qué hacer con cada gesto.
 * type: none | open_app | open_url | camera | flashlight | media | volume | webhook | pc
 */
@Serializable
data class Rule(val type: String = "none", val value: String = "", val label: String = "Sin acción", val extra: String = "")

@Serializable
data class Config(
    val rules: Map<String, Rule> = emptyMap(),
    val pcAddress: String = "",                          // ej. 192.168.1.10:8765
    val pcToken: String = "gestores",
    val confirm: Boolean = true,                         // preguntar antes de ejecutar
    val days: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),   // 1 = lunes … 7 = domingo
    val startHour: Int = 0,                              // desde (0 a 23)
    val endHour: Int = 24,                               // hasta (1 a 24)
    val sensitivity: Float = 1f,                         // 0.6 poco sensible … 1.6 muy sensible
    val vibration: Int = 2,                              // 0 apagada · 1 suave · 2 media · 3 fuerte
    val clearAfterSec: Int = 15,                         // borrar avisos solos (0 = nunca)
) {
    /** ¿Los gestos están permitidos ahora según los días y el horario? */
    fun isActiveNow(now: java.time.LocalDateTime = java.time.LocalDateTime.now()): Boolean {
        if (now.dayOfWeek.value !in days) return false
        if (startHour == endHour) return true
        val h = now.hour
        return if (startHour < endHour) h in startHour until endHour else (h >= startHour || h < endHour)
    }

    fun scheduleText(): String {
        val d = when (days.sorted()) {
            listOf(1, 2, 3, 4, 5, 6, 7) -> "Todos los días"
            listOf(1, 2, 3, 4, 5) -> "Entre semana"
            listOf(6, 7) -> "Fines de semana"
            emptyList<Int>() -> "Ningún día"
            else -> days.sorted().joinToString(" ") { DAY_LETTERS[it - 1] }
        }
        val h = if ((startHour == 0 && endHour == 24) || startHour == endHour) "todo el día"
                else "de %02d:00 a %02d:00".format(startHour, endHour % 24)
        return "$d, $h"
    }

    companion object { val DAY_LETTERS = listOf("L", "M", "X", "J", "V", "S", "D") }
}

/** Vibración con la fuerza elegida (0 apagada · 1 suave · 2 media · 3 fuerte). */
fun vibrateLevel(ctx: Context, level: Int, double: Boolean = false) {
    if (level <= 0) return
    val amp = when (level) { 1 -> 70; 2 -> 160; else -> 255 }
    val ms = when (level) { 1 -> 50L; 2 -> 80L; else -> 140L }
    val v: android.os.Vibrator = if (android.os.Build.VERSION.SDK_INT >= 31)
        ctx.getSystemService(android.os.VibratorManager::class.java).defaultVibrator
    else @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
    val effect = if (double) android.os.VibrationEffect.createWaveform(longArrayOf(0, ms, 90, ms), intArrayOf(0, amp, 0, amp), -1)
                 else android.os.VibrationEffect.createOneShot(ms, amp)
    v.vibrate(effect)
}

val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Llamadas HTTP: PC y dispositivos inteligentes (Home Assistant, Shelly, Tasmota, IFTTT…). */
object Http {
    suspend fun call(url: String, method: String = "GET", body: String? = null): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            c.requestMethod = method
            c.connectTimeout = 5000
            c.readTimeout = 6000
            if (body != null && method != "GET") {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            c.disconnect()
            (code < 400) to text.trim().take(80).ifBlank { if (code < 400) "Listo" else "Error $code" }
        }.getOrElse { false to "Sin conexión con ${runCatching { URL(url).host }.getOrDefault(url)}" }
    }

    fun pcUrl(cfg: Config, cmd: String, fallbackAddress: String = ""): String {
        val addr = cfg.pcAddress.ifBlank { fallbackAddress }.removePrefix("http://").removePrefix("https://").trimEnd('/')
        return "http://$addr/run?cmd=${URLEncoder.encode(cmd, "UTF-8")}&token=${URLEncoder.encode(cfg.pcToken, "UTF-8")}"
    }
}

/** Opciones listas para el PC (las ejecuta pc/gestores_pc.py). */
object PcActions {
    val presets = listOf(
        "key:right" to "PC: siguiente diapositiva",
        "key:left" to "PC: diapositiva anterior",
        "key:playpause" to "PC: play / pausa",
        "key:next" to "PC: siguiente canción",
        "key:prev" to "PC: canción anterior",
        "key:volup" to "PC: subir volumen",
        "key:voldown" to "PC: bajar volumen",
        "key:mute" to "PC: silenciar",
        "open:https://guardian-fauna.netlify.app" to "PC: abrir Guardián",
        "lock" to "PC: bloquear pantalla",
    )
}

fun Context.prefs() = getSharedPreferences("gestores", Context.MODE_PRIVATE)

/** Reglas guardadas en el celular. Cada vez que cambian se envían al reloj. */
object ConfigStore {
    private val defaults = Config(
        rules = mapOf(
            "alzar" to Rule("camera", "", "Abrir la cámara"),
            "bajar" to Rule("media", "playpause", "Música: play / pausa"),
            "girar" to Rule("media", "next", "Música: siguiente"),
            "sacudir" to Rule("flashlight", "", "Linterna"),
            "doble_golpe" to Rule("pc", "key:right", "PC: siguiente diapositiva"),
            // Botones de música del reloj
            "w_play" to Rule("flashlight", "", "Linterna"),
            "w_next" to Rule("pc", "key:right", "PC: siguiente diapositiva"),
            "w_prev" to Rule("pc", "key:left", "PC: diapositiva anterior"),
            "w_next2" to Rule("camera", "", "Abrir la cámara"),
            "w_prev2" to Rule("pc", "open:https://guardian-fauna.netlify.app", "PC: abrir Guardián"),
            "w_volup" to Rule("pc", "key:volup", "PC: subir volumen"),
            "w_voldown" to Rule("pc", "key:voldown", "PC: bajar volumen"),
        ),
    )

    fun load(ctx: Context): Config =
        ctx.prefs().getString("config", null)?.let { runCatching { json.decodeFromString(Config.serializer(), it) }.getOrNull() } ?: defaults

    fun save(ctx: Context, cfg: Config) {
        ctx.prefs().edit().putString("config", json.encodeToString(Config.serializer(), cfg)).apply()
        syncToWatch(ctx, cfg)
    }

    /** Envía las reglas y la dirección del PC al reloj. */
    fun syncToWatch(ctx: Context, cfg: Config) {
        val req = com.google.android.gms.wearable.PutDataMapRequest.create(Proto.PATH_CONFIG).apply {
            dataMap.putString("json", json.encodeToString(Config.serializer(), cfg))
            dataMap.putLong("time", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        com.google.android.gms.wearable.Wearable.getDataClient(ctx).putDataItem(req)
    }
}

@Serializable
data class LogEvent(val time: Long, val gesture: String, val result: String)

/** Historial de los últimos gestos recibidos. */
object EventLog {
    private val ser = kotlinx.serialization.builtins.ListSerializer(LogEvent.serializer())
    fun list(ctx: Context): List<LogEvent> =
        ctx.prefs().getString("log", null)?.let { runCatching { json.decodeFromString(ser, it) }.getOrNull() } ?: emptyList()
    fun clear(ctx: Context) = ctx.prefs().edit().remove("log").apply()
    fun add(ctx: Context, gesture: String, result: String) {
        val l = (listOf(LogEvent(System.currentTimeMillis(), gesture, result)) + list(ctx)).take(20)
        ctx.prefs().edit().putString("log", json.encodeToString(ser, l)).apply()
    }
}

/** Botones de música de cualquier reloj (Honor, Huawei, Xiaomi, Amazfit…) usados como controles. */
object Triggers {
    val all = listOf(
        GestureDef("w_play", "⏯️", "Botón play / pausa", "Botón central de música del reloj"),
        GestureDef("w_next", "⏭️", "Botón siguiente", "Un toque en 'siguiente'"),
        GestureDef("w_next2", "⏭️⏭️", "Siguiente ×2", "Dos toques rápidos en 'siguiente'"),
        GestureDef("w_prev", "⏮️", "Botón anterior", "Un toque en 'anterior'"),
        GestureDef("w_prev2", "⏮️⏮️", "Anterior ×2", "Dos toques rápidos en 'anterior'"),
        GestureDef("w_volup", "🔊", "Volumen +", "Subir volumen desde el reloj"),
        GestureDef("w_voldown", "🔉", "Volumen −", "Bajar volumen desde el reloj"),
    )
}
