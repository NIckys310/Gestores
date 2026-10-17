package com.gestores.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Una muestra durante un movimiento: aceleración lineal x,y,z, aceleración "hacia arriba", giro y tiempo. */
class Sample(val x: Float, val y: Float, val z: Float, val up: Float, val gyro: Float, val t: Long) {
    val mag get() = sqrt(x * x + y * y + z * z)
}

/**
 * Convierte los datos del acelerómetro y el giroscopio en "movimientos".
 * Un movimiento empieza cuando la mano se mueve con fuerza y termina cuando se queda quieta.
 */
class MotionSegmenter(private val onSegment: (List<Sample>) -> Unit) {
    var sensitivity = 1f
    private val gravity = FloatArray(3)
    private val upDir = FloatArray(3)
    private var gravityReady = false
    private var gyroMag = 0f
    private var active = false
    private var start = 0L
    private var last = 0L
    private var quiet = 0L
    private var cooldownUntil = 0L
    private val samples = ArrayList<Sample>()

    fun onGyro(x: Float, y: Float, z: Float) { gyroMag = sqrt(x * x + y * y + z * z) }

    fun onAccel(x: Float, y: Float, z: Float, timeNanos: Long) {
        val t = timeNanos / 1_000_000
        if (!gravityReady) { gravity[0] = x; gravity[1] = y; gravity[2] = z; gravityReady = true }
        // Aceleración del movimiento = lectura - gravedad (la gravedad se calcula ANTES de usar esta lectura)
        val lx = x - gravity[0]; val ly = y - gravity[1]; val lz = z - gravity[2]
        val energy = sqrt(lx * lx + ly * ly + lz * lz)
        val dt = if (last == 0L) 20L else (t - last).coerceIn(1, 100)
        last = t

        if (!active) {
            val inCooldown = t < cooldownUntil
            if (inCooldown || (energy < 4.5f / sensitivity && gyroMag < 3.5f / sensitivity)) {
                // En reposo (o justo después de un gesto) se actualiza la gravedad con un filtro paso bajo
                val a = if (inCooldown) 0.2f else 0.05f
                gravity[0] += a * (x - gravity[0]); gravity[1] += a * (y - gravity[1]); gravity[2] += a * (z - gravity[2])
                return
            }
            active = true; start = t; quiet = 0; samples.clear()
            val g = sqrt(gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2]).coerceAtLeast(0.1f)
            for (i in 0..2) upDir[i] = gravity[i] / g   // en reposo, el acelerómetro apunta "hacia arriba"
        }
        val up = lx * upDir[0] + ly * upDir[1] + lz * upDir[2]
        samples += Sample(lx, ly, lz, up, gyroMag, t - start)
        quiet = if (energy < 1.6f && gyroMag < 1.2f) quiet + dt else 0
        if (quiet >= 220 || t - start > 2500) finish(t)
    }

    private fun finish(t: Long) {
        active = false
        cooldownUntil = t + 700
        while (samples.size > 2 && samples.last().mag < 1.6f) samples.removeAt(samples.lastIndex)
        val dur = samples.lastOrNull()?.t ?: 0
        if (dur in 80..2500 && samples.size >= 4) onSegment(samples.toList())
    }
}

/** Reconoce los gestos básicos con reglas físicas (picos de aceleración, giro e inversiones). */
object BasicClassifier {
    fun classify(s: List<Sample>, sensitivity: Float): String? {
        if (s.isEmpty()) return null
        val k = sensitivity
        // Umbral de cada gesto = fuerza base × tu calibración ÷ sensibilidad
        fun t(id: String, base: Float) = base * Calibration.factor(id) / k
        val maxUp = s.maxOf { abs(it.up) }
        // Hacia dónde EMPIEZA el movimiento vertical: arriba = alzar, abajo = bajar
        val firstUp = s.firstOrNull { abs(it.up) > 6f / k }?.up ?: 0f
        val gyroPeak = s.maxOf { it.gyro }
        val dur = s.last().t

        // picos fuertes separados (golpes o toques en la mesa)
        val peakThr = minOf(t("doble_golpe", 15f), t("toques", 15f))
        var peaks = 0; var armed = true; var lastPeak = -1000L
        for (p in s) {
            if (armed && p.mag > peakThr && p.t - lastPeak > 80) { peaks++; armed = false; lastPeak = p.t }
            if (p.mag < 7f) armed = true
        }
        // giros separados (doble giro de muñeca)
        var gyroPeaks = 0; var gArmed = true
        for (p in s) {
            if (gArmed && p.gyro > t("doble_giro", 7f)) { gyroPeaks++; gArmed = false }
            if (p.gyro < 2.5f) gArmed = true
        }
        // inversiones de dirección en el eje dominante (sacudidas)
        val ex = s.sumOf { (it.x * it.x).toDouble() }; val ey = s.sumOf { (it.y * it.y).toDouble() }; val ez = s.sumOf { (it.z * it.z).toDouble() }
        val axis: (Sample) -> Float = when (maxOf(ex, ey, ez)) { ex -> { p -> p.x }; ey -> { p -> p.y }; else -> { p -> p.z } }
        var reversals = 0; var sign = 0
        for (p in s) {
            val v = axis(p)
            if (abs(v) > t("sacudir", 7f)) { val sg = if (v > 0) 1 else -1; if (sign != 0 && sg != sign) reversals++; sign = sg }
        }

        return when {
            peaks >= 3 && dur < 1500 -> "toques"
            peaks >= 2 && dur < 900 -> "doble_golpe"
            reversals >= 4 -> "sacudir"
            gyroPeaks >= 2 && maxUp < 8f / k -> "doble_giro"
            gyroPeak > t("girar", 7f) && maxUp < 8f / k -> "girar"
            maxUp > t("alzar", 9f) && firstUp > 0f -> "alzar"
            maxUp > t("bajar", 9f) && firstUp < 0f -> "bajar"
            else -> null
        }
    }
}

/**
 * CALIBRACIÓN GUIADA
 * Haces cada gesto 3 veces; se mide tu fuerza típica y el umbral se ajusta
 * para que el gesto se reconozca con el 65 % de esa fuerza (ni muy fácil ni muy difícil).
 */
object Calibration {
    private val base = mapOf("alzar" to 9f, "bajar" to 9f, "girar" to 7f, "doble_giro" to 7f, "sacudir" to 7f, "doble_golpe" to 15f, "toques" to 15f)
    val calibrable get() = base.keys.toList()
    private val factors = mutableMapOf<String, Float>()
    // Solo se guardan las preferencias (no el Context): así no hay fugas de memoria
    private var prefs: SharedPreferences? = null
    private val ser = MapSerializer(String.serializer(), Float.serializer())

    fun init(c: Context) {
        if (prefs != null) return
        prefs = c.prefs()
        prefs?.getString("calibration", null)?.let { runCatching { factors.putAll(json.decodeFromString(ser, it)) } }
    }

    fun factor(id: String) = factors[id] ?: 1f
    fun all(): Map<String, Float> = factors.toMap()
    fun setAll(m: Map<String, Float>) { factors.clear(); factors.putAll(m); save() }
    fun reset(id: String) { factors.remove(id); save() }

    /** La "fuerza" de un movimiento según el tipo de gesto. */
    fun metric(id: String, s: List<Sample>): Float = when (id) {
        "alzar", "bajar" -> s.maxOf { abs(it.up) }
        "girar", "doble_giro" -> s.maxOf { it.gyro }
        "sacudir" -> s.maxOf { maxOf(abs(it.x), abs(it.y), abs(it.z)) }
        else -> s.maxOf { it.mag }
    }

    /** Guarda la calibración con tus muestras y devuelve el nuevo factor (1 = normal). */
    fun calibrate(id: String, samples: List<List<Sample>>): Float {
        val b = base[id] ?: return 1f
        val validas = samples.filter { it.isNotEmpty() }
        if (validas.isEmpty()) return factor(id)            // sin muestras no se cambia nada
        val avg = validas.map { metric(id, it) }.average().toFloat()
        val f = (avg * 0.65f / b).coerceIn(0.5f, 1.8f)
        factors[id] = f
        save()
        return f
    }

    private fun save() { prefs?.edit()?.putString("calibration", json.encodeToString(ser, factors))?.apply() }

    /** Borra toda la calibración en memoria (para las pruebas automáticas). */
    internal fun resetForTests() { factors.clear() }
}

/**
 * IA que aprende gestos personalizados.
 * Guarda varias muestras de cada gesto y reconoce uno nuevo comparándolo con
 * Dynamic Time Warping (DTW) y el vecino más cercano (k-NN).
 * El umbral de cada gesto se calcula solo, según lo parecidas que son sus muestras.
 */
object Learner {
    private const val N = 32
    private const val BAND = 6
    private var prefs: SharedPreferences? = null      // sin Context guardado: no hay fugas de memoria
    private val templates = mutableMapOf<String, MutableList<List<FloatArray>>>()
    private val ser = MapSerializer(String.serializer(), ListSerializer(ListSerializer(ListSerializer(Float.serializer()))))

    private const val MAX_SAMPLES = 12
    private val names = mutableMapOf<String, String>()
    private val nameSer = MapSerializer(String.serializer(), String.serializer())

    fun init(ctx: Context) {
        Calibration.init(ctx)
        if (prefs != null) return
        val p = ctx.prefs()
        prefs = p
        p.getString("ai_names", null)?.let { runCatching { names.putAll(json.decodeFromString(nameSer, it)) } }
        p.getString("templates", null)?.let { s ->
            runCatching { json.decodeFromString(ser, s) }.getOrNull()?.forEach { (id, list) ->
                templates[id] = list.map { seq -> seq.map { it.toFloatArray() } }.toMutableList()
            }
        }
    }

    fun count(id: String) = templates[id]?.size ?: 0

    fun add(id: String, seg: List<Sample>) {
        if (seg.isEmpty()) return
        val list = templates.getOrPut(id) { mutableListOf() }
        list += resample(seg)
        while (list.size > MAX_SAMPLES) list.removeAt(0)   // se queda con las más recientes
        save()
    }

    /** Aprende mientras usas: un gesto reconocido con buena confianza se guarda como nuevo ejemplo. */
    fun learnFromUse(id: String, seg: List<Sample>, confidence: Float) {
        if (confidence >= 0.7f && count(id) in 3 until MAX_SAMPLES) add(id, seg)
    }

    fun nameOf(id: String): String? = names[id]
    fun rename(id: String, name: String) {
        if (name.isBlank()) names.remove(id) else names[id] = name.trim().take(24)
        saveNames()
    }

    /** Guarda en el celular solo si la app ya inició (en las pruebas automáticas no hay celular). */
    private fun saveNames() {
        prefs?.edit()?.putString("ai_names", json.encodeToString(nameSer, names))?.apply()
    }

    /** Exporta los gestos aprendidos, sus nombres y la calibración (para guardarlos o pasarlos a otro celular). */
    fun exportJson(): String {
        val plain = templates.mapValues { (_, list) -> list.map { seq -> seq.map { it.toList() } } }
        return json.encodeToString(Backup.serializer(), Backup(1, plain, names.toMap(), Calibration.all()))
    }

    /** Importa una copia hecha con exportJson. Devuelve cuántos gestos se cargaron. */
    fun importJson(text: String): Int {
        val b = json.decodeFromString(Backup.serializer(), text)
        templates.clear()
        b.templates.forEach { (id, list) -> templates[id] = list.map { seq -> seq.map { it.toFloatArray() } }.toMutableList() }
        names.clear(); names.putAll(b.names)
        saveNames()
        Calibration.setAll(b.calibration)
        save()
        return templates.size
    }

    fun clear(id: String) { templates.remove(id); save() }

    /** Devuelve el gesto aprendido más parecido y la confianza (0 a 1), o null si ninguno se parece. */
    fun match(seg: List<Sample>): Pair<String, Float>? {
        if (seg.size < 2 || templates.values.none { it.isNotEmpty() }) return null
        val q = resample(seg)
        var bestId: String? = null; var best = Float.MAX_VALUE
        for ((id, list) in templates) for (t in list) {
            val d = dtw(q, t); if (d < best) { best = d; bestId = id }
        }
        val id = bestId ?: return null
        val threshold = threshold(id)
        return if (best < threshold) id to (1f - best / threshold).coerceIn(0f, 1f) else null
    }

    private fun threshold(id: String): Float {
        val list = templates[id] ?: return 0f
        if (list.size < 2) return 6f
        var sum = 0f; var n = 0
        for (i in list.indices) for (j in i + 1 until list.size) { sum += dtw(list[i], list[j]); n++ }
        // Acepta movimientos hasta 2.5 veces más distintos que tus propias muestras (entre 4 y 10)
        return ((sum / n) * 2.5f).coerceIn(4f, 10f)
    }

    private fun resample(seg: List<Sample>): List<FloatArray> = List(N) { i ->
        val pos = i * (seg.size - 1f) / (N - 1)
        val a = seg[pos.toInt()]; val b = seg[min(pos.toInt() + 1, seg.lastIndex)]; val f = pos - pos.toInt()
        floatArrayOf(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f, a.z + (b.z - a.z) * f)
    }

    private fun dtw(a: List<FloatArray>, b: List<FloatArray>): Float {
        val inf = Float.MAX_VALUE / 4
        val d = Array(N + 1) { FloatArray(N + 1) { inf } }
        d[0][0] = 0f
        for (i in 1..N) for (j in max(1, i - BAND)..min(N, i + BAND)) {
            val p = a[i - 1]; val q = b[j - 1]
            val cost = sqrt((p[0] - q[0]) * (p[0] - q[0]) + (p[1] - q[1]) * (p[1] - q[1]) + (p[2] - q[2]) * (p[2] - q[2]))
            d[i][j] = cost + minOf(d[i - 1][j], d[i][j - 1], d[i - 1][j - 1])
        }
        return d[N][N] / N
    }

    private fun save() {
        val p = prefs ?: return
        val plain = templates.mapValues { (_, list) -> list.map { seq -> seq.map { it.toList() } } }
        p.edit().putString("templates", json.encodeToString(ser, plain)).apply()
    }

    /** Borra todo lo aprendido en memoria (para las pruebas automáticas). */
    internal fun resetForTests() { templates.clear(); names.clear() }
}

/** Copia de seguridad de la IA de gestos. */
@kotlinx.serialization.Serializable
data class Backup(
    val version: Int = 1,
    val templates: Map<String, List<List<List<Float>>>> = emptyMap(),
    val names: Map<String, String> = emptyMap(),
    val calibration: Map<String, Float> = emptyMap(),
)
