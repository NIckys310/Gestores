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
   APPS DEL RELOJ · lista, pantalla que las abre y piezas comunes
   Cada grupo de apps está en su archivo: AppsReloj, AppsSalud,
   AppsUtilidades y AppsGestores.
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
internal fun ticker(periodMs: Long = 0): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) { if (periodMs == 0L) withFrameMillis { now = System.currentTimeMillis() } else { now = System.currentTimeMillis(); delay(periodMs) } }
    }
    return now
}

internal fun today() = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
