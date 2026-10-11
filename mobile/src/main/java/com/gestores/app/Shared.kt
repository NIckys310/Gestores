package com.gestores.app

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
        GestureDef("toques", "🫳", "Toques en la mesa", "Da 3 toquecitos seguidos en la mesa con el celular"),
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
    fun get(id: String) = all.firstOrNull { it.id == id }
    fun label(id: String) = get(id)?.let { "${it.emoji} ${it.name}" } ?: id
}

/**
 * Qué hace cada gesto. Todo pasa dentro del celular, nada se conecta con otros aparatos.
 * type: none | app (abre una app del reloj) | face (siguiente esfera) | chrono | flashlight | media | volume | vibrate_mode
 */
@Serializable
data class Rule(val type: String = "none", val value: String = "", val label: String = "Sin acción")

@Serializable
data class Config(val rules: Map<String, Rule> = emptyMap())

val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun Context.prefs() = getSharedPreferences("gestores", Context.MODE_PRIVATE)

/** Acciones que se le pueden poner a un gesto. */
object Actions {
    val presets: List<Rule> get() = listOf(
        Rule("face", "", "⌚ Cambiar de esfera"),
        Rule("chrono", "", "⏱️ Cronómetro: iniciar / pausar"),
        Rule("flashlight", "", "🔦 Linterna"),
        Rule("media", "playpause", "🎵 Música: play / pausa"),
        Rule("media", "next", "⏭️ Música: siguiente"),
        Rule("media", "prev", "⏮️ Música: anterior"),
        Rule("volume", "up", "🔊 Subir volumen"),
        Rule("volume", "down", "🔉 Bajar volumen"),
        Rule("vibrate_mode", "", "📳 Timbre ↔ vibración"),
    ) + WatchApps.all.map { Rule("app", it.id, "${it.emoji} Abrir ${it.name}") } + Rule()
}

/** Reglas guardadas en el celular. */
object ConfigStore {
    private val defaults = Config(
        rules = mapOf(
            "alzar" to Rule("app", "clima", "☀️ Abrir Clima"),
            "bajar" to Rule("media", "playpause", "🎵 Música: play / pausa"),
            "girar" to Rule("face", "", "⌚ Cambiar de esfera"),
            "sacudir" to Rule("flashlight", "", "🔦 Linterna"),
            "doble_golpe" to Rule("chrono", "", "⏱️ Cronómetro: iniciar / pausar"),
            "doble_giro" to Rule("media", "next", "⏭️ Música: siguiente"),
            "toques" to Rule("app", "pasos", "👣 Abrir Pasos"),
        ),
    )

    fun load(ctx: Context): Config =
        ctx.prefs().getString("config2", null)?.let { runCatching { json.decodeFromString(Config.serializer(), it) }.getOrNull() } ?: defaults

    fun save(ctx: Context, cfg: Config) {
        ctx.prefs().edit().putString("config2", json.encodeToString(Config.serializer(), cfg)).apply()
    }
}

@Serializable
data class LogEvent(val time: Long, val gesture: String, val result: String)

/** Historial de los últimos gestos. */
object EventLog {
    private val ser = kotlinx.serialization.builtins.ListSerializer(LogEvent.serializer())
    fun list(ctx: Context): List<LogEvent> =
        ctx.prefs().getString("log", null)?.let { runCatching { json.decodeFromString(ser, it) }.getOrNull() } ?: emptyList()
    fun add(ctx: Context, gesture: String, result: String) {
        val l = (listOf(LogEvent(System.currentTimeMillis(), gesture, result)) + list(ctx)).take(20)
        ctx.prefs().edit().putString("log", json.encodeToString(ser, l)).apply()
    }
}
