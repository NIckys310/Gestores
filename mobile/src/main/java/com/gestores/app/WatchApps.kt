package com.gestores.app

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/* =========================================================
   APPS DEL RELOJ
   Todo funciona dentro del celular: nada se conecta con otros aparatos.
   ========================================================= */
data class WatchApp(val id: String, val emoji: String, val name: String, val group: String)

object WatchApps {
    val all = listOf(
        WatchApp("alarma", "⏰", "Alarma", "Reloj"),
        WatchApp("crono", "⏱️", "Cronómetro", "Reloj"),
        WatchApp("timer", "⏲️", "Temporizador", "Reloj"),
        WatchApp("calendario", "📅", "Calendario", "Reloj"),
        WatchApp("pasos", "👣", "Pasos", "Salud"),
        WatchApp("respirar", "🫁", "Respirar", "Salud"),
        WatchApp("agua", "💧", "Agua", "Salud"),
        WatchApp("ejercicio", "🏃", "Ejercicio", "Salud"),
        WatchApp("linterna", "🔦", "Linterna", "Utilidades"),
        WatchApp("clima", "☀️", "Clima", "Utilidades"),
        WatchApp("calc", "🧮", "Calculadora", "Utilidades"),
        WatchApp("notas", "📝", "Notas", "Utilidades"),
        WatchApp("musica", "🎵", "Música", "Utilidades"),
        WatchApp("gestos", "✋", "Gestores", "Gestos e IA"),
        WatchApp("entrenar", "🧠", "Entrenar IA", "Gestos e IA"),
        WatchApp("ajustes", "⚙️", "Ajustes", "Gestos e IA"),
    )
    fun get(id: String) = all.firstOrNull { it.id == id }
}

/** Abre la app pedida dentro de la esfera. */
@Composable
fun WatchAppScreen(id: String, mode: String, onMode: (String) -> Unit) {
    when (id) {
        "alarma" -> AlarmApp()
        "crono" -> ChronoApp()
        "timer" -> TimerApp()
        "calendario" -> CalendarApp()
        "pasos" -> StepsApp()
        "respirar" -> BreatheApp()
        "agua" -> WaterApp()
        "ejercicio" -> WorkoutApp()
        "linterna" -> TorchApp()
        "clima" -> WeatherApp()
        "calc" -> CalcApp()
        "notas" -> NotesApp()
        "musica" -> MusicApp()
        "gestos" -> GestoresApp()
        "entrenar" -> TrainApp()
        "ajustes" -> SettingsApp(mode, onMode)
    }
}

/* ---------------- Piezas comunes ---------------- */

/** Columna centrada que cabe dentro del círculo; se desliza hacia arriba si no cabe. */
@Composable
fun Dial(scroll: Boolean = true, content: @Composable () -> Unit) {
    val m = Modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = 30.dp)
    Column(
        if (scroll) m.verticalScroll(rememberScrollState()) else m,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) { content() }
}

@Composable
fun Title(t: String) {
    val eng = LocalEng.current
    Text(t, color = eng.primary, fontFamily = FontFamily.SansSerif, fontSize = 15.sp, letterSpacing = 1.sp)
}

@Composable
fun Big(t: String, size: TextUnit = 40.sp) {
    val eng = LocalEng.current
    Text(t, color = eng.text, fontFamily = FontFamily.SansSerif, fontSize = size, fontWeight = FontWeight.Light, textAlign = TextAlign.Center)
}

@Composable
fun Small(t: String) {
    val eng = LocalEng.current
    Text(t, color = eng.muted, fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 14.sp)
}

/** Botón dorado redondeado. */
@Composable
fun Pill(t: String, filled: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val eng = LocalEng.current
    Box(
        modifier.clip(RoundedCornerShape(50)).then(if (filled) Modifier.background(eng.primary) else Modifier)
            .border(1.dp, eng.goldBrush(), RoundedCornerShape(50)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) { Text(t, color = if (filled) eng.bg else eng.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
}

/** Botón redondo grande (play, +, −…). */
@Composable
fun Round(t: String, size: Int = 46, filled: Boolean = false, onClick: () -> Unit) {
    val eng = LocalEng.current
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(if (filled) eng.primary else eng.raised)
            .border(1.dp, eng.goldBrush(), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(t, color = if (filled) eng.bg else eng.text, fontSize = (size / 2.6).sp) }
}

@Composable
fun Line(content: @Composable RowScope.() -> Unit) =
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically, content = content)

/** Anillo de progreso dorado. */
@Composable
fun Ring(progress: Float, sizeDp: Int = 120, content: @Composable () -> Unit) {
    val eng = LocalEng.current
    Box(Modifier.size(sizeDp.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = Stroke(9f, cap = StrokeCap.Round)
            drawCircle(eng.border.copy(alpha = 0.35f), size.minDimension / 2 - 6f, style = Stroke(9f))
            drawArc(Brush.sweepGradient(listOf(eng.goldLight, eng.primary, eng.goldLight)), -90f, 360f * progress.coerceIn(0f, 1f), false,
                topLeft = Offset(6f, 6f), size = Size(size.width - 12f, size.height - 12f), style = s)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
private fun ticker(periodMs: Long = 0): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) { if (periodMs == 0L) withFrameMillis { now = System.currentTimeMillis() } else { now = System.currentTimeMillis(); delay(periodMs) } }
    }
    return now
}

private fun fmt(ms: Long, cents: Boolean = true): String {
    val t = ms.coerceAtLeast(0)
    val h = t / 3_600_000; val m = t / 60_000 % 60; val s = t / 1000 % 60; val c = t / 10 % 100
    return when {
        h > 0 -> "%d:%02d:%02d".format(h, m, s)
        cents -> "%02d:%02d.%02d".format(m, s, c)
        else -> "%02d:%02d".format(m, s)
    }
}

private fun today() = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

/* ---------------- RELOJ ---------------- */

@Composable
private fun AlarmApp() {
    val ctx = LocalContext.current
    var alarms by remember { mutableStateOf(Alarms.list(ctx)) }
    var adding by remember { mutableStateOf(false) }
    var h by remember { mutableIntStateOf(6) }
    var m by remember { mutableIntStateOf(30) }
    fun save(l: List<Alarm>) { alarms = l; Alarms.save(ctx, l) }
    Dial {
        Title("ALARMA")
        if (adding) {
            Line {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Round("+", 34) { h = (h + 1) % 24 }; Big("%02d".format(h), 34.sp); Round("−", 34) { h = (h + 23) % 24 }
                }
                Big(":", 34.sp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Round("+", 34) { m = (m + 5) % 60 }; Big("%02d".format(m), 34.sp); Round("−", 34) { m = (m + 55) % 60 }
                }
            }
            Line {
                Pill("Cancelar") { adding = false }
                Pill("Guardar", filled = true) {
                    save(alarms + Alarm((alarms.maxOfOrNull { it.id } ?: 0) + 1, h, m)); adding = false
                }
            }
        } else {
            if (alarms.isEmpty()) Small("Sin alarmas")
            alarms.sortedBy { it.hour * 60 + it.minute }.forEach { a ->
                Line {
                    Text("%02d:%02d".format(a.hour, a.minute), color = if (a.on) LocalEng.current.text else LocalEng.current.muted,
                        fontFamily = FontFamily.SansSerif, fontSize = 24.sp)
                    Pill(if (a.on) "ON" else "OFF", filled = a.on) { save(alarms.map { if (it.id == a.id) it.copy(on = !it.on) else it }) }
                    Text("✕", color = LocalEng.current.alert, modifier = Modifier.clickable { Alarms.cancel(ctx, a.id); save(alarms - a) }.padding(4.dp))
                }
            }
            Pill("+ Nueva alarma", filled = true) { adding = true }
            Small("Suena aunque cierres la app")
        }
    }
}

@Composable
private fun ChronoApp() {
    val running by WatchState.chronoRunning.collectAsState()
    val laps by WatchState.laps.collectAsState()
    ticker()
    val t = WatchState.chronoNow()
    Dial {
        Title("CRONÓMETRO")
        Ring(((t / 1000f) % 60f) / 60f, 130) { Big(fmt(t), 26.sp) }
        Line {
            Round(if (running) "⏸" else "▶", 52, filled = true) { WatchState.toggleChrono() }
            Round(if (running) "⚑" else "↺", 44) {
                if (running) WatchState.laps.value = listOf(t) + laps else WatchState.resetChrono()
            }
        }
        laps.take(4).forEachIndexed { i, l -> Small("Vuelta ${laps.size - i}   ${fmt(l)}") }
    }
}

@Composable
private fun TimerApp() {
    val ctx = LocalContext.current
    val end by WatchState.timerEnd.collectAsState()
    var minutes by remember { mutableIntStateOf(ctx.prefs().getInt("timer_min", 5)) }
    val now = ticker(200)
    Dial {
        Title("TEMPORIZADOR")
        if (end > 0) {
            val left = end - now
            Ring(left / (minutes * 60_000f)) { Big(fmt(left, cents = false), 30.sp) }
            Pill("Detener", filled = true) { Alarms.stopTimer(ctx) }
        } else {
            Line { Round("−", 38) { minutes = (minutes - 1).coerceAtLeast(1) }; Big("$minutes", 44.sp); Round("+", 38) { minutes = (minutes + 1).coerceAtMost(180) } }
            Small("minutos")
            Line { listOf(1, 5, 10, 25).forEach { v -> Pill("$v") { minutes = v } } }
            Pill("▶ Iniciar", filled = true) {
                ctx.prefs().edit().putInt("timer_min", minutes).apply()
                Alarms.startTimer(ctx, minutes * 60_000L)
            }
        }
    }
}

@Composable
private fun CalendarApp() {
    val eng = LocalEng.current
    var offset by remember { mutableIntStateOf(0) }
    val now = Calendar.getInstance()
    val c = Calendar.getInstance().apply { add(Calendar.MONTH, offset); set(Calendar.DAY_OF_MONTH, 1) }
    val first = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7   // lunes = 0
    val days = c.getActualMaximum(Calendar.DAY_OF_MONTH)
    val isThisMonth = offset == 0
    Dial(scroll = false) {
        Line {
            Text("‹", color = eng.primary, fontSize = 22.sp, modifier = Modifier.clickable { offset-- }.padding(horizontal = 6.dp))
            Title(SimpleDateFormat("MMMM yyyy", Locale("es")).format(c.time).uppercase())
            Text("›", color = eng.primary, fontSize = 22.sp, modifier = Modifier.clickable { offset++ }.padding(horizontal = 6.dp))
        }
        Row { listOf("L", "M", "M", "J", "V", "S", "D").forEach { Text(it, color = eng.muted, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.width(26.dp)) } }
        for (week in 0 until 6) {
            if (week * 7 - first >= days) break
            Row {
                for (d in 0 until 7) {
                    val day = week * 7 + d - first + 1
                    val today = isThisMonth && day == now.get(Calendar.DAY_OF_MONTH)
                    Box(Modifier.size(26.dp).clip(CircleShape).then(if (today) Modifier.background(eng.primary) else Modifier), contentAlignment = Alignment.Center) {
                        if (day in 1..days) Text("$day", color = if (today) eng.bg else eng.text, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/* ---------------- SALUD ---------------- */

@Composable
private fun NeedPermission(perm: String, why: String) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    Small(why)
    Pill("Dar permiso", filled = true) { launcher.launch(perm) }
}

@Composable
private fun StepsApp() {
    val ctx = LocalContext.current
    val c = rememberComplications()
    var goal by remember { mutableIntStateOf(ctx.prefs().getInt("steps_goal", 8000)) }
    Dial {
        Title("PASOS DE HOY")
        if (!ctx.has(Manifest.permission.ACTIVITY_RECOGNITION)) NeedPermission(Manifest.permission.ACTIVITY_RECOGNITION, "Para contar tus pasos")
        else {
            val s = c.steps ?: 0
            Ring(s / goal.toFloat(), 130) { Big("$s", 30.sp); Small("de $goal") }
            Small("≈ %.1f km · %d kcal".format(s * 0.00075f, (s * 0.04f).toInt()))
            Line {
                Round("−", 32) { goal = (goal - 1000).coerceAtLeast(1000); ctx.prefs().edit().putInt("steps_goal", goal).apply() }
                Small("meta")
                Round("+", 32) { goal += 1000; ctx.prefs().edit().putInt("steps_goal", goal).apply() }
            }
        }
    }
}

@Composable
private fun BreatheApp() {
    val eng = LocalEng.current
    var on by remember { mutableStateOf(false) }
    var start by remember { mutableLongStateOf(0L) }
    val now = ticker(100)
    val phase = if (on) ((now - start) / 4000) % 2 else 0
    val anim by rememberInfiniteTransition(label = "resp").animateFloat(0.45f, 1f,
        infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "r")
    Dial(scroll = false) {
        Title("RESPIRAR")
        Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(140.dp).graphicsLayer { val k = if (on) anim else 0.6f; scaleX = k; scaleY = k }
                .clip(CircleShape).background(Brush.radialGradient(listOf(eng.goldLight.copy(alpha = 0.7f), eng.primary.copy(alpha = 0.15f)))))
            Text(if (!on) "🫁" else if (phase == 0L) "Inhala" else "Exhala", color = eng.text, fontFamily = FontFamily.SansSerif, fontSize = 18.sp)
        }
        if (on) Small("Ciclo ${(now - start) / 8000 + 1}") else Small("4 s inhalar · 4 s exhalar")
        Pill(if (on) "Terminar" else "Empezar", filled = !on) { on = !on; start = System.currentTimeMillis() }
    }
}

@Composable
private fun WaterApp() {
    val ctx = LocalContext.current
    val p = ctx.prefs()
    var glasses by remember { mutableIntStateOf(if (p.getString("water_day", "") == today()) p.getInt("water", 0) else 0) }
    var remind by remember { mutableStateOf(p.getBoolean("water_on", false)) }
    fun set(v: Int) { glasses = v.coerceIn(0, 30); p.edit().putString("water_day", today()).putInt("water", glasses).apply() }
    Dial {
        Title("AGUA")
        Ring(glasses / 8f, 120) { Big("$glasses", 34.sp); Small("de 8 vasos") }
        Line { Round("−", 38) { set(glasses - 1) }; Round("💧", 52, filled = true) { set(glasses + 1) } }
        Pill(if (remind) "🔔 Recordarme: sí" else "🔕 Recordarme: no", filled = remind) {
            remind = !remind; Alarms.waterReminder(ctx, remind)
        }
        Small("Cada 2 horas, de 8 a. m. a 9 p. m.")
    }
}

@Composable
private fun WorkoutApp() {
    val c = rememberComplications()
    val start by WatchState.workoutStart.collectAsState()
    val steps0 by WatchState.workoutSteps0.collectAsState()
    val now = ticker(500)
    Dial {
        Title("EJERCICIO")
        if (start == 0L) {
            Big("🏃", 44.sp)
            Small("Caminar o correr con el celular encima")
            Pill("▶ Empezar", filled = true) {
                WatchState.workoutStart.value = System.currentTimeMillis()
                WatchState.workoutSteps0.value = c.steps ?: -1
            }
        } else {
            val ms = now - start
            val steps = if (steps0 >= 0) ((c.steps ?: steps0) - steps0).coerceAtLeast(0) else 0
            Big(fmt(ms, cents = false), 34.sp)
            Small("$steps pasos · %.2f km".format(steps * 0.00075f))
            Small("≈ ${(steps * 0.04f + ms / 60_000f * 3f).toInt()} kcal")
            Pill("■ Terminar", filled = true) { WatchState.workoutStart.value = 0L }
        }
    }
}

/* ---------------- UTILIDADES ---------------- */

@Composable
private fun TorchApp() {
    val ctx = LocalContext.current
    val eng = LocalEng.current
    val on by WatchState.torch.collectAsState()
    Dial(scroll = false) {
        Title("LINTERNA")
        Box(
            Modifier.size(120.dp).clip(CircleShape).background(if (on) eng.goldLight else eng.raised)
                .border(2.dp, eng.goldBrush(), CircleShape).clickable { ActionRunner.toggleTorch(ctx) },
            contentAlignment = Alignment.Center,
        ) { Text("🔦", fontSize = 48.sp) }
        Small(if (on) "Encendida · toca para apagar" else "Toca para encender")
    }
}

@Composable
private fun WeatherApp() {
    val ctx = LocalContext.current
    val c = rememberComplications()
    Dial {
        Title("CLIMA")
        if (!ctx.has(Manifest.permission.ACCESS_COARSE_LOCATION)) NeedPermission(Manifest.permission.ACCESS_COARSE_LOCATION, "Para saber el clima donde estás")
        else if (c.tempC == null) { Big("…", 30.sp); Small("Buscando el clima") }
        else {
            Text(c.weather, fontSize = 52.sp)
            Big("${c.tempC}°", 46.sp)
            Small(when (c.weather) { "☀️" -> "Despejado"; "🌤️" -> "Algo nublado"; "☁️" -> "Nublado"; "🌫️" -> "Niebla"; "🌧️" -> "Lluvia"
                "❄️" -> "Nieve"; "🌦️" -> "Chubascos"; "⛈️" -> "Tormenta"; else -> "" })
        }
    }
}

@Composable
private fun CalcApp() {
    val eng = LocalEng.current
    var display by remember { mutableStateOf("0") }
    var acc by remember { mutableStateOf<Double?>(null) }
    var op by remember { mutableStateOf<String?>(null) }
    var fresh by remember { mutableStateOf(true) }
    fun show(v: Double) = if (v.isNaN() || v.isInfinite()) "Error" else if (v == v.toLong().toDouble()) v.toLong().toString() else "%.6f".format(Locale.US, v).trimEnd('0').trimEnd('.')
    fun apply(a: Double, b: Double, o: String?) = when (o) { "+" -> a + b; "−" -> a - b; "×" -> a * b; "÷" -> a / b; else -> b }
    fun press(k: String) {
        val cur = display.toDoubleOrNull() ?: 0.0
        when (k) {
            "C" -> { display = "0"; acc = null; op = null; fresh = true }
            "⌫" -> display = display.dropLast(1).ifBlank { "0" }
            "%" -> display = show(cur / 100)
            "+", "−", "×", "÷" -> { acc = acc?.let { if (fresh) it else apply(it, cur, op) } ?: cur; display = show(acc!!); op = k; fresh = true }
            "=" -> { acc?.let { display = show(apply(it, cur, op)) }; acc = null; op = null; fresh = true }
            "." -> { if (fresh) { display = "0."; fresh = false } else if ('.' !in display) display += "." }
            else -> { display = if (fresh || display == "0") k else (display + k).take(12); fresh = false }
        }
    }
    Dial(scroll = false) {
        Text(display, color = eng.text, fontFamily = FontFamily.SansSerif, fontSize = 26.sp, maxLines = 1, textAlign = TextAlign.End,
            modifier = Modifier.width(176.dp))
        listOf(listOf("C", "⌫", "%", "÷"), listOf("7", "8", "9", "×"), listOf("4", "5", "6", "−"), listOf("1", "2", "3", "+"), listOf("0", ".", "="))
            .forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { k ->
                        val isOp = k in listOf("÷", "×", "−", "+", "=")
                        Box(Modifier.size(40.dp, 30.dp).clip(RoundedCornerShape(10.dp)).background(if (isOp) eng.primary else eng.raised).clickable { press(k) },
                            contentAlignment = Alignment.Center) { Text(k, color = if (isOp) eng.bg else eng.text, fontSize = 15.sp) }
                    }
                }
            }
    }
}

@Composable
private fun NotesApp() {
    val ctx = LocalContext.current
    val eng = LocalEng.current
    val ser = remember { ListSerializer(String.serializer()) }
    var notes by remember { mutableStateOf(ctx.prefs().getString("notes", null)?.let { runCatching { json.decodeFromString(ser, it) }.getOrNull() } ?: emptyList()) }
    var text by remember { mutableStateOf("") }
    fun save(l: List<String>) { notes = l; ctx.prefs().edit().putString("notes", json.encodeToString(ser, l)).apply() }
    Dial {
        Title("NOTAS")
        Box(Modifier.width(180.dp).clip(RoundedCornerShape(12.dp)).background(eng.raised).padding(8.dp)) {
            if (text.isEmpty()) Text("Escribe aquí…", color = eng.muted, fontSize = 12.sp)
            BasicTextField(text, { text = it.take(140) }, textStyle = TextStyle(color = eng.text, fontSize = 13.sp),
                cursorBrush = SolidColor(eng.primary), modifier = Modifier.fillMaxWidth())
        }
        Pill("Guardar", filled = true) { if (text.isNotBlank()) { save(listOf(text.trim()) + notes); text = "" } }
        notes.forEach { n ->
            Row(Modifier.width(180.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("• $n", color = eng.text, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("✕", color = eng.alert, fontSize = 12.sp, modifier = Modifier.clickable { save(notes - n) }.padding(4.dp))
            }
        }
    }
}

@Composable
private fun MusicApp() {
    val ctx = LocalContext.current
    Dial(scroll = false) {
        Title("MÚSICA")
        Small("Controla lo que suena en el celular")
        Line {
            Round("⏮", 44) { ActionRunner.media(ctx, "prev") }
            Round("⏯", 60, filled = true) { ActionRunner.media(ctx, "playpause") }
            Round("⏭", 44) { ActionRunner.media(ctx, "next") }
        }
        Line { Round("🔉", 40) { ActionRunner.volume(ctx, false) }; Round("🔊", 40) { ActionRunner.volume(ctx, true) } }
    }
}

/* ---------------- GESTOS E IA ---------------- */

/** Una app del celular que se puede abrir con un gesto. */
private data class PhoneApp(val label: String, val pkg: String)

private fun installedApps(ctx: android.content.Context): List<PhoneApp> {
    val pm = ctx.packageManager
    val i = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i, 0).map { PhoneApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
        .filter { it.pkg != ctx.packageName }.distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
}

/**
 * GESTORES: todos los gestos, qué hace cada uno y el interruptor general.
 * Las acciones con sonido preguntan "¿Deseas…?" antes; cámara y apps se abren de una vez.
 */
@Composable
private fun GestoresApp() {
    val ctx = LocalContext.current
    val eng = LocalEng.current
    val on by WatchState.gestoresOn.collectAsState()
    val running by PhoneBus.running.collectAsState()
    val last by PhoneBus.lastGesture.collectAsState()
    val result by PhoneBus.result.collectAsState()
    val sens by PhoneBus.sensitivity.collectAsState()
    var cfg by remember { mutableStateOf(ConfigStore.load(ctx)) }
    var editing by remember { mutableStateOf<String?>(null) }
    var pickingApp by remember { mutableStateOf(false) }
    val apps = remember(pickingApp) { if (pickingApp) installedApps(ctx) else emptyList() }

    fun choose(id: String, r: Rule) { cfg = cfg.copy(rules = cfg.rules + (id to r)); ConfigStore.save(ctx, cfg); editing = null; pickingApp = false }

    @Composable
    fun Option(id: String, r: Rule) {
        val sel = cfg.rules[id] == r
        Text((if (sel) "● " else "") + r.label, color = if (sel) eng.primary else eng.text, fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { choose(id, r) }.padding(vertical = 5.dp, horizontal = 4.dp))
    }

    @Composable
    fun Section(t: String) = Text(t.uppercase(), color = eng.primary, fontSize = 10.sp, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 6.dp))

    val e = editing
    key(e, pickingApp) { Dial {
        when {
            // Elegir una app del celular
            e != null && pickingApp -> {
                Title("ABRIR UNA APP")
                apps.forEach { a -> Option(e, Rule("open_app", a.pkg, "📱 Abrir ${a.label}")) }
                Pill("Atrás") { pickingApp = false }
            }
            // Qué hace un gesto
            e != null -> {
                val g = Gestures.get(e)
                Text(g?.emoji ?: "✋", fontSize = 30.sp)
                Title((g?.name ?: e).uppercase())
                Small(g?.hint ?: "")
                Line {
                    Pill("▶ Probar", filled = true) { PhoneGestureService.execute(ctx, e, null) }
                    Pill("Atrás") { editing = null }
                }
                Section("Se hace de una vez")
                Actions.direct.forEach { Option(e, it) }
                val cur = cfg.rules[e]
                Text((if (cur?.type == "open_app") "● ${cur.label}" else "📱 Abrir una app del celular…"),
                    color = if (cur?.type == "open_app") eng.primary else eng.text, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().clickable { pickingApp = true }.padding(vertical = 5.dp, horizontal = 4.dp))
                Section("Pregunta antes (sonido)")
                Actions.sound.forEach { Option(e, it) }
                Section("Apps del reloj")
                Actions.watchApps.forEach { Option(e, it) }
                Section("Nada")
                Option(e, Rule())
            }
            // Lista de gestos
            else -> {
                Title("GESTORES")
                // Interruptor general
                Box(
                    Modifier.width(190.dp).clip(RoundedCornerShape(50)).background(if (on) eng.primary else eng.raised)
                        .border(1.dp, eng.goldBrush(), RoundedCornerShape(50)).clickable { WatchState.setGestores(ctx, !on) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (on) "● Gestores activados" else "○ Gestores desactivados", color = if (on) eng.bg else eng.text,
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Small(if (on && running) "Mueve el celular para hacer un gesto" else "Actívalo para usar los gestos")
                if (on && last != null) Small("Último: ${Gestures.label(last!!)} → $result")
                Line {
                    listOf("Suave" to 1.3f, "Normal" to 1f, "Fuerte" to 0.8f).forEach { (l, v) ->
                        Pill(l, filled = kotlin.math.abs(sens - v) < 0.05f) { PhoneGestureService.saveSensitivity(ctx, v) }
                    }
                }
                if (!android.provider.Settings.canDrawOverlays(ctx)) Pill("🔓 Permitir abrir apps") {
                    ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + ctx.packageName)))
                }
                listOf("Gestos" to Gestures.builtIn, "Dibujar en el aire" to Gestures.shapes, "Tus gestos IA" to Gestures.ai).forEach { (t, list) ->
                    Section(t)
                    list.forEach { g ->
                        val r = cfg.rules[g.id]
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { editing = g.id }.padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(g.emoji, fontSize = 18.sp, modifier = Modifier.width(30.dp))
                            Column(Modifier.weight(1f)) {
                                Text(g.name, color = eng.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text((r?.label ?: "Sin acción") + if (r != null && ActionRunner.needsConfirm(r)) "  · pregunta" else "",
                                    color = eng.muted, fontSize = 10.sp)
                            }
                            Text("›", color = eng.primary, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    } }
}

@Composable
private fun TrainApp() {
    val ctx = LocalContext.current
    val eng = LocalEng.current
    val training by PhoneBus.trainingTarget.collectAsState()
    val count by PhoneBus.trainCount.collectAsState()
    val calib by PhoneBus.calibTarget.collectAsState()
    val calibCount by PhoneBus.calibCount.collectAsState()
    val factor by PhoneBus.calibFactor.collectAsState()
    var calibrating by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    fun ensureRunning() { if (!PhoneBus.running.value) PhoneGestureService.start(ctx) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        msg = runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(Learner.exportJson().toByteArray()) }; "✓ Copia guardada" }
            .getOrElse { "⚠ No se pudo guardar" }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        msg = runCatching { "✓ ${Learner.importJson(ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })} gestos cargados" }
            .getOrElse { "⚠ Ese archivo no es una copia de Gestores" }
        refresh++
    }

    val t = training
    val cb = calib
    Dial {
        when {
            t != null -> {
                val isAi = t.startsWith("ia_")
                var name by remember(t) { mutableStateOf(Learner.nameOf(t) ?: "") }
                Title((Gestures.get(t)?.name ?: t).uppercase())
                Ring(count / 5f, 100) { Big("$count", 30.sp); Small("de 5") }
                Small(if (count < 5) "Haz el gesto y quédate quieto un momento" else "¡Listo! La IA ya lo aprendió")
                if (isAi) Box(Modifier.width(170.dp).clip(RoundedCornerShape(12.dp)).background(eng.raised).padding(8.dp)) {
                    if (name.isEmpty()) Text("Ponle un nombre", color = eng.muted, fontSize = 12.sp)
                    BasicTextField(name, { name = it.take(24) }, singleLine = true, textStyle = TextStyle(color = eng.text, fontSize = 13.sp),
                        cursorBrush = SolidColor(eng.primary), modifier = Modifier.fillMaxWidth())
                }
                Line {
                    Pill("Borrar") { Learner.clear(t); PhoneBus.trainCount.value = 0 }
                    Pill("Listo", filled = true) { if (isAi) Learner.rename(t, name); PhoneBus.trainingTarget.value = null; refresh++ }
                }
            }
            cb != null -> {
                Title("CALIBRAR")
                Big(Gestures.get(cb)?.emoji ?: "✋", 34.sp)
                Small(Gestures.get(cb)?.hint ?: "")
                Line { repeat(3) { i -> Box(Modifier.size(18.dp).clip(CircleShape).background(if (i < calibCount) eng.primary else eng.border)) } }
                val f = factor
                Small(when { f == null -> "Hazlo con tu fuerza normal · ${calibCount + 1} de 3"
                    f < 0.9f -> "✓ Lo haces suave: ahora es más fácil"
                    f > 1.1f -> "✓ Lo haces fuerte: ya no se activa por accidente"
                    else -> "✓ Tu fuerza es la normal" })
                Pill(if (f != null) "Listo" else "Atrás", filled = f != null) { PhoneBus.calibTarget.value = null }
            }
            calibrating -> {
                Title("CALIBRAR")
                Small("Elige un gesto: lo harás 3 veces")
                Calibration.calibrable.forEach { id ->
                    Text(Gestures.label(id) + if (Calibration.factor(id) != 1f) " ✓" else "", color = eng.text, fontSize = 12.sp,
                        modifier = Modifier.clickable {
                            PhoneBus.calibSamples.clear(); PhoneBus.calibCount.value = 0; PhoneBus.calibFactor.value = null
                            PhoneBus.calibTarget.value = id; ensureRunning()
                        }.padding(vertical = 4.dp))
                }
                Pill("Atrás") { calibrating = false }
            }
            else -> {
                Title("ENTRENAR IA")
                Small("Toca uno y hazlo 5 veces")
                (Gestures.shapes + Gestures.ai).forEach { g ->
                    val n = remember(refresh) { Learner.count(g.id) }
                    Row(Modifier.fillMaxWidth().clickable {
                        PhoneBus.trainCount.value = Learner.count(g.id); PhoneBus.trainingTarget.value = g.id; ensureRunning()
                    }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${g.emoji} ${g.name}", color = eng.text, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text(if (n == 0) "nuevo" else "$n ✓", color = if (n == 0) eng.muted else eng.primary, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Pill("🎯 Calibrar a mi fuerza", filled = true) { calibrating = true }
                Line { Pill("Guardar copia") { exporter.launch("gestores-gestos.json") }; Pill("Cargar") { importer.launch(arrayOf("*/*")) } }
                if (msg.isNotBlank()) Small(msg)
            }
        }
    }
}

@Composable
private fun SettingsApp(mode: String, onMode: (String) -> Unit) {
    val ctx = LocalContext.current
    val eng = LocalEng.current
    val aod by WatchState.aod.collectAsState()
    val log = remember { EventLog.list(ctx) }
    Dial {
        Title("AJUSTES")
        Small("Modo claro / oscuro")
        Line { listOf("system" to "Auto", "dark" to "Oscuro", "light" to "Claro").forEach { (k, l) -> Pill(l, filled = mode == k) { onMode(k) } } }
        val shape by WatchState.shape.collectAsState()
        Small("Forma del reloj")
        Line { listOf("square" to "▢ Cuadrado", "round" to "◯ Redondo").forEach { (k, l) -> Pill(l, filled = shape == k) { WatchState.setShape(ctx, k) } } }
        Pill(if (aod) "🌙 Siempre encendido: sí" else "Siempre encendido: no", filled = aod) {
            WatchState.setAod(ctx, !aod)
        }
        Small("También: mantén presionada la esfera")
        Spacer(Modifier.height(4.dp))
        Small("Últimos gestos")
        if (log.isEmpty()) Small("Todavía ninguno")
        log.take(6).forEach { e ->
            Text("${SimpleDateFormat("HH:mm", Locale("es")).format(Date(e.time))} ${Gestures.label(e.gesture)}", color = eng.text, fontSize = 11.sp)
        }
    }
}
