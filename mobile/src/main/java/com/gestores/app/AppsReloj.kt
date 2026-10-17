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
   APPS DEL RELOJ · Alarma, cronómetro, temporizador y calendario
   ========================================================= */

/* ---------------- RELOJ ---------------- */

@Composable
internal fun AlarmApp() {
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
internal fun ChronoApp() {
    val running by WatchState.chronoRunning.collectAsState()
    val laps by WatchState.laps.collectAsState()
    ticker()
    val t = WatchState.chronoNow()
    Dial {
        Title("CRONÓMETRO")
        Ring(((t / 1000f) % 60f) / 60f, 130) { Big(formatoTiempo(t), 26.sp) }
        Line {
            Round(if (running) "⏸" else "▶", 52, filled = true) { WatchState.toggleChrono() }
            Round(if (running) "⚑" else "↺", 44) {
                if (running) WatchState.laps.value = listOf(t) + laps else WatchState.resetChrono()
            }
        }
        laps.take(4).forEachIndexed { i, l -> Small("Vuelta ${laps.size - i}   ${formatoTiempo(l)}") }
    }
}

@Composable
internal fun TimerApp() {
    val ctx = LocalContext.current
    val end by WatchState.timerEnd.collectAsState()
    var minutes by remember { mutableIntStateOf(ctx.prefs().getInt("timer_min", 5)) }
    val now = ticker(200)
    Dial {
        Title("TEMPORIZADOR")
        if (end > 0) {
            val left = end - now
            Ring(left / (minutes * 60_000f)) { Big(formatoTiempo(left, centesimas = false), 30.sp) }
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
internal fun CalendarApp() {
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
