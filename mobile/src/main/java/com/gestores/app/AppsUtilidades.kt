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
   UTILIDADES · Linterna, clima, calculadora, notas y música
   ========================================================= */

/* ---------------- UTILIDADES ---------------- */

@Composable
internal fun TorchApp() {
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
internal fun WeatherApp() {
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
internal fun CalcApp() {
    val eng = LocalEng.current
    val calc = remember { Calculadora() }
    var display by remember { mutableStateOf(calc.pantalla) }
    fun press(k: String) { calc.pulsar(k); display = calc.pantalla }
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
internal fun NotesApp() {
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
internal fun MusicApp() {
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
