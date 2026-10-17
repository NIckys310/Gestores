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
   GESTORES E IA · Todos los gestos, entrenar la IA y ajustes
   ========================================================= */

/* ---------------- GESTOS E IA ---------------- */

/** Una app del celular que se puede abrir con un gesto. */
internal data class PhoneApp(val label: String, val pkg: String)

internal fun installedApps(ctx: android.content.Context): List<PhoneApp> {
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
internal fun GestoresApp() {
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
internal fun TrainApp() {
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
internal fun SettingsApp(mode: String, onMode: (String) -> Unit) {
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
