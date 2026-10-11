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
        GestureDef("doble_giro", "🔁", "Doble giro", "Gira la muñeca dos veces seguidas, rápido"),
        GestureDef("toques", "🫳", "Toques en la mesa", "Da 3 toquecitos seguidos en la mesa con el celular o el reloj"),
    )
    /** Dibujar en el aire: la IA los aprende con tu forma de dibujar (5 ejemplos cada uno). */
    val shapes = listOf(
        GestureDef("dib_circulo", "⭕", "Dibujar círculo", "Dibuja un círculo en el aire con la mano"),
        GestureDef("dib_v", "✔️", "Dibujar V", "Dibuja una V (como un chulo) en el aire"),
        GestureDef("dib_z", "⚡", "Dibujar Z", "Dibuja una Z en el aire"),
    )
    /** 8 gestos propios; el nombre lo pones tú. */
    val ai get() = (1..8).map { n ->
        val id = "ia_$n"
        GestureDef(id, "🧠", Learner.nameOf(id) ?: "Gesto IA $n", "Un movimiento tuyo que la IA aprende")
    }
    val all get() = builtIn + shapes + ai
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
    val pcAddress: String = "",       // ej. 192.168.1.10:8765
    val pcToken: String = "gestores",
)

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
