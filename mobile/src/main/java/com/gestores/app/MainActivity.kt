package com.gestores.app

import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* Colores del reloj de lujo: salen del tema elegido en Ajustes (ver Theme.kt) */
private val Deep: Color @Composable get() = LocalEng.current.bg
private val CardColor: Color @Composable get() = LocalEng.current.panel
private val Teal: Color @Composable get() = LocalEng.current.primary
private val Gold: Color @Composable get() = LocalEng.current.alert
private val Mint: Color @Composable get() = LocalEng.current.text
private val Muted: Color @Composable get() = LocalEng.current.muted

class MainActivity : ComponentActivity() {
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Learner.init(this)
        PhoneBus.sensitivity.value = PhoneGestureService.loadSensitivity(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        runCatching { ConfigStore.syncToWatch(this, ConfigStore.load(this)) }   // para un reloj Wear OS, si hay uno
        setContent {
            var mode by rememberSaveable { mutableStateOf(ThemePrefs.loadMode(this)) }
            val dark = isDarkMode(mode)
            LaunchedEffect(dark) {
                val t = android.graphics.Color.TRANSPARENT
                val bars = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            GestoresTheme(dark) {
                WatchShell(mode, onMode = { mode = it; ThemePrefs.saveMode(this, it) })
            }
        }
    }
}

/** Arranca mostrando el reloj (WatchHome); al tocarlo se despliega la app completa. Atrás vuelve al reloj. */
@Composable
fun WatchShell(mode: String, onMode: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = open) { open = false }
    AnimatedContent(
        targetState = open,
        transitionSpec = {
            if (targetState) (scaleIn(tween(450), initialScale = 0.35f) + fadeIn(tween(450))) togetherWith
                (scaleOut(tween(450), targetScale = 1.6f) + fadeOut(tween(300)))
            else (scaleIn(tween(450), initialScale = 1.6f) + fadeIn(tween(450))) togetherWith
                (scaleOut(tween(450), targetScale = 0.35f) + fadeOut(tween(300)))
        },
        label = "watch",
    ) { isOpen ->
        if (isOpen) PhoneApp(mode, onMode, onBackToWatch = { open = false }) else WatchHome(onOpen = { open = true })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneApp(mode: String, onMode: (String) -> Unit, onBackToWatch: () -> Unit = {}) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var cfg by remember { mutableStateOf(ConfigStore.load(ctx)) }
    var wearWatches by remember { mutableStateOf<List<String>>(emptyList()) }
    var log by remember { mutableStateOf(EventLog.list(ctx)) }
    var canOverlay by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var editing by remember { mutableStateOf<GestureDef?>(null) }
    val training by PhoneBus.trainingTarget.collectAsState()

    fun update(c: Config) { cfg = c; ConfigStore.save(ctx, c) }
    fun say(msg: String) { scope.launch { snack.showSnackbar(msg) } }

    LaunchedEffect(Unit) {
        while (true) {
            wearWatches = runCatching { Wearable.getNodeClient(ctx).connectedNodes.await().map { it.displayName } }.getOrDefault(emptyList())
            log = EventLog.list(ctx)
            canOverlay = Settings.canDrawOverlays(ctx)
            delay(2000)
        }
    }

    val running by PhoneBus.running.collectAsState()
    var openMods by rememberSaveable { mutableStateOf(setOf("00", "01")) }
    fun toggle(code: String) { openMods = if (code in openMods) openMods - code else openMods + code }

    Scaffold(
        containerColor = Deep,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.logo), null, Modifier.size(30.dp).clip(RoundedCornerShape(4.dp)))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Gestores", style = MaterialTheme.typography.titleLarge)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(if (running) Teal else Gold))
                                Spacer(Modifier.width(6.dp))
                                Text(if (running) "Detectando gestos" else "Detección apagada",
                                    color = if (running) Teal else Gold, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onBackToWatch) { Icon(Icons.Filled.Watch, "Volver al reloj", tint = Teal) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Deep, titleContentColor = Mint),
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 00. Ajustes: tema negro / blanco
            item(key = "00") {
                Module("00", "Ajustes", "00" in openMods, { toggle("00") }) {
                    Card {
                        Text("Tema", color = Mint, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = mode == "system", onClick = { onMode("system") }, label = { Text("Automático") })
                            FilterChip(selected = mode == "dark", onClick = { onMode("dark") }, label = { Text("Noche · negro y oro") })
                            FilterChip(selected = mode == "light", onClick = { onMode("light") }, label = { Text("Día · marfil") })
                        }
                        Text("Se guarda automáticamente.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // 01. Celular en la muñeca
            item(key = "01") {
                Module("01", "Celular en la muñeca", "01" in openMods, { toggle("01") }) {
                    WristCard(onTestWatch = { PhoneGestureService.notifyWatch(ctx, "⌚ Prueba de Gestores", "Si ves esto en tu reloj, ¡todo está conectado!"); say("Aviso enviado al reloj") })
                }
            }

            // 02. Permiso para abrir apps
            if (!canOverlay) item(key = "02") {
                Module("02", "Permisos pendientes", "02" in openMods, { toggle("02") }) {
                    Card {
                        Text("Permiso para abrir apps", fontWeight = FontWeight.SemiBold, color = Gold)
                        Text("Para que un gesto abra apps aunque el celular esté bloqueado o en otra app, activa “Mostrar sobre otras apps”.",
                            color = Muted, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = {
                            ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                        }) { Text("Activar") }
                    }
                }
            }

            // 03. Entrenar IA
            item(key = "03") { Module("03", "Entrenar la IA", "03" in openMods, { toggle("03") }) { TrainCard() } }

            // 04. Qué hace cada gesto
            item(key = "04") {
                Module("04", "Qué hace cada gesto", "04" in openMods, { toggle("04") }) {
                    Gestures.all.forEach { g ->
                        RuleRow(g, cfg.rules[g.id] ?: Rule(),
                            onTest = { scope.launch { say(ActionRunner.execute(ctx, cfg.rules[g.id] ?: Rule(), cfg)) } },
                            onEdit = { editing = g })
                    }
                }
            }

            // 05. Botones de música del reloj
            item(key = "05") {
                Module("05", "Botones del reloj", "05" in openMods, { toggle("05") }) {
                    WatchButtonsCard()
                    Triggers.all.forEach { g ->
                        RuleRow(g, cfg.rules[g.id] ?: Rule(),
                            onTest = { scope.launch { say(ActionRunner.execute(ctx, cfg.rules[g.id] ?: Rule(), cfg)) } },
                            onEdit = { editing = g })
                    }
                }
            }

            // 06. PC
            item(key = "06") {
                Module("06", "Computador", "06" in openMods, { toggle("06") }) {
                    PcCard(cfg,
                        onSave = { update(it); say("Guardado") },
                        onTest = { scope.launch { say(ActionRunner.execute(ctx, Rule("pc", "ping", "Conexión con el PC"), cfg)) } })
                }
            }

            // 07. Reloj Wear OS (si algún día tienes uno)
            item(key = "07") {
                Module("07", "Reloj Wear OS", "07" in openMods, { toggle("07") }) {
                    Card {
                        Text(if (wearWatches.isEmpty()) "Reloj Wear OS: no conectado" else "Reloj Wear OS: ${wearWatches.joinToString()}",
                            fontWeight = FontWeight.SemiBold, color = if (wearWatches.isEmpty()) Gold else Teal)
                        Text("Con un Galaxy Watch o Pixel Watch, instala la app “wear” y los gestos se hacen directamente en el reloj.",
                            color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // 08. Historial
            item(key = "08") {
                Module("08", "Historial", "08" in openMods, { toggle("08") }) {
                    Card {
                        if (log.isEmpty()) Text("Aún no hay gestos registrados.", color = Muted)
                        val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale("es")) }
                        log.forEach { e ->
                            Text("[${fmt.format(Date(e.time))}] ${Gestures.label(e.gesture)} → ${e.result}", color = Teal, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
    editing?.let { g ->
        RuleDialog(g, cfg.rules[g.id] ?: Rule(), onDismiss = { editing = null }) { r ->
            update(cfg.copy(rules = cfg.rules + (g.id to r)))
            editing = null
            say("${g.name}: ${r.label}")
        }
    }
    training?.let { id -> TrainingDialog(id) }
}

/* ---------------- Celular en la muñeca ---------------- */
@Composable
private fun WristCard(onTestWatch: () -> Unit) {
    val ctx = LocalContext.current
    val running by PhoneBus.running.collectAsState()
    val last by PhoneBus.lastGesture.collectAsState()
    val result by PhoneBus.result.collectAsState()
    val sensitivity by PhoneBus.sensitivity.collectAsState()
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("📱 Celular en la muñeca", fontWeight = FontWeight.Bold, color = Mint, style = MaterialTheme.typography.titleMedium)
                Text("Ponte el celular en la muñeca o el antebrazo con una banda deportiva y haz los gestos.",
                    color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = running, onCheckedChange = { if (it) PhoneGestureService.start(ctx) else PhoneGestureService.stop(ctx) })
        }
        if (running) {
            Text((last?.let { Gestures.label(it) + "  →  " } ?: "") + result, color = Teal, fontWeight = FontWeight.SemiBold)
        }
        Text("Sensibilidad", color = Mint, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Baja" to 0.8f, "Normal" to 1f, "Alta" to 1.3f).forEach { (label, v) ->
                FilterChip(selected = kotlin.math.abs(sensitivity - v) < 0.05f,
                    onClick = { PhoneGestureService.saveSensitivity(ctx, v) }, label = { Text(label) })
            }
        }
        OutlinedButton(onClick = onTestWatch) { Text("⌚ Probar aviso en mi reloj") }
        Text("Cada gesto envía una notificación que vibra y aparece en tu Honor.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

/* ---------------- Entrenar la IA ---------------- */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrainCard() {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var calibrating by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    val training by PhoneBus.trainingTarget.collectAsState()
    LaunchedEffect(training) { refresh++ }

    // Exportar / importar los gestos como archivo (Drive, WhatsApp, otro celular…)
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        msg = runCatching {
            ctx.contentResolver.openOutputStream(uri)!!.use { it.write(Learner.exportJson().toByteArray()) }
            "✓ Gestos exportados"
        }.getOrElse { "⚠ No se pudo exportar" }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        msg = runCatching {
            val text = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            "✓ ${Learner.importJson(text)} gestos importados"
        }.getOrElse { "⚠ Ese archivo no es una copia de Gestores" }
        refresh++
    }

    fun train(id: String) {
        PhoneBus.trainCount.value = Learner.count(id)
        PhoneBus.trainingTarget.value = id
        if (!PhoneBus.running.value) PhoneGestureService.start(ctx)
    }

    @Composable
    fun GestureButton(g: GestureDef) {
        val n = remember(refresh) { Learner.count(g.id) }
        OutlinedButton(onClick = { train(g.id) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
            Text("${g.emoji} ${g.name.removePrefix("Gesto ").removePrefix("Dibujar ")}\n${if (n == 0) "nuevo" else "$n ✓"}",
                style = MaterialTheme.typography.bodySmall)
        }
    }

    Card {
        Text("🧠 Enseñarle gestos a la IA", fontWeight = FontWeight.Bold, color = Mint, style = MaterialTheme.typography.titleMedium)
        Text("Inventa un movimiento, hazlo 5 veces y la IA lo aprende. Mientras lo usas sigue aprendiendo, " +
             "y si no está segura te pregunta “¿Fue este gesto?”.", color = Muted, style = MaterialTheme.typography.bodySmall)
        Text("Dibujar en el aire", color = Gold, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Gestures.shapes.forEach { GestureButton(it) }
        }
        Text("Tus 8 gestos (toca para entrenar y ponerle nombre)", color = Gold, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Gestures.ai.forEach { GestureButton(it) }
        }
        HorizontalDivider(color = Muted.copy(alpha = 0.3f))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { calibrating = true }) { Text("🎯 Calibrar a mi fuerza") }
            OutlinedButton(onClick = { exporter.launch("gestores-gestos.json") }) { Text("⤴ Exportar") }
            OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("⤵ Importar") }
        }
        if (msg.isNotBlank()) Text(msg, color = Teal, style = MaterialTheme.typography.bodySmall)
    }
    if (calibrating) CalibrationDialog { calibrating = false }
}

@Composable
private fun TrainingDialog(id: String) {
    val count by PhoneBus.trainCount.collectAsState()
    val result by PhoneBus.result.collectAsState()
    val isAi = id.startsWith("ia_")
    var name by remember(id) { mutableStateOf(Learner.nameOf(id) ?: "") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("${Gestures.get(id)?.emoji ?: "🧠"} ${Gestures.get(id)?.name ?: id}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isAi) OutlinedTextField(name, { name = it.take(24) }, label = { Text("Nombre del gesto") },
                    placeholder = { Text("Ej.: Saludo, Puño, Ola") }, singleLine = true)
                Text(if (count < 5) "Con el celular en la muñeca, haz tu gesto y quédate quieto un momento.\n\nMuestra ${count + 1} de 5"
                     else "¡Listo! La IA ya aprendió este gesto ($count ejemplos).")
                if (result.isNotBlank()) Text(result, color = Gold)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (isAi) Learner.rename(id, name)
                PhoneBus.trainingTarget.value = null
            }) { Text(if (count >= 3) "Terminar" else "Cerrar") }
        },
        dismissButton = { TextButton(onClick = { Learner.clear(id); PhoneBus.trainCount.value = 0 }) { Text("Borrar muestras") } },
    )
}

/** Calibración guiada: haces el gesto 3 veces y se ajusta a tu fuerza. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalibrationDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val target by PhoneBus.calibTarget.collectAsState()
    val count by PhoneBus.calibCount.collectAsState()
    val factor by PhoneBus.calibFactor.collectAsState()
    fun pick(id: String?) {
        PhoneBus.calibSamples.clear(); PhoneBus.calibCount.value = 0; PhoneBus.calibFactor.value = null
        PhoneBus.calibTarget.value = id
        if (id != null && !PhoneBus.running.value) PhoneGestureService.start(ctx)
    }
    fun close() { pick(null); onClose() }
    AlertDialog(
        onDismissRequest = ::close,
        title = { Text("🎯 Calibración guiada") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val t = target
                if (t == null) {
                    Text("Elige un gesto. Lo harás 3 veces con tu fuerza normal y Gestores se ajusta a ti.", color = Muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Calibration.calibrable.forEach { id ->
                            val f = Calibration.factor(id)
                            FilterChip(selected = f != 1f, onClick = { pick(id) },
                                label = { Text(Gestures.label(id) + if (f != 1f) " ✓" else "") })
                        }
                    }
                } else {
                    Text(Gestures.label(t), color = Gold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(Gestures.get(t)?.hint ?: "", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(3) { i ->
                            Box(Modifier.size(22.dp).clip(CircleShape)
                                .background(if (i < count) Gold else Muted.copy(alpha = 0.25f)))
                        }
                    }
                    val f = factor
                    Text(
                        when {
                            f == null -> "Hazlo ahora · ${count + 1} de 3"
                            f < 0.9f -> "✓ Listo. Lo haces suave: ahora es más fácil de reconocer."
                            f > 1.1f -> "✓ Listo. Lo haces fuerte: ahora no se activará por accidente."
                            else -> "✓ Listo. Tu fuerza es la normal."
                        },
                        color = if (f == null) MaterialTheme.colorScheme.onSurface else Teal,
                    )
                }
            }
        },
        confirmButton = {
            if (target != null) TextButton(onClick = { pick(null) }) { Text(if (factor != null) "Otro gesto" else "Atrás") }
            else TextButton(onClick = ::close) { Text("Cerrar") }
        },
        dismissButton = {
            target?.let { t -> TextButton(onClick = { Calibration.reset(t); pick(null) }) { Text("Restablecer") } }
        },
    )
}

/* ---------------- Botones de música del reloj ---------------- */
@Composable
private fun WatchButtonsCard() {
    val ctx = LocalContext.current
    val on by UniversalService.active.collectAsState()
    val last by UniversalService.lastTrigger.collectAsState()
    val result by UniversalService.lastResult.collectAsState()
    var useVolume by remember { mutableStateOf(UniversalService.useVolume(ctx)) }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("⌚ Botones de música del reloj", fontWeight = FontWeight.Bold, color = Mint)
                Text("En tu Honor abre Música → Teléfono: ⏯️ ⏭️ ⏮️ ejecutan las acciones de abajo.",
                    color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = on, onCheckedChange = { if (it) UniversalService.start(ctx) else UniversalService.stop(ctx) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Usar también el volumen del reloj", color = Mint, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = useVolume, onCheckedChange = {
                useVolume = it; UniversalService.setUseVolume(ctx, it)
                if (on) { UniversalService.stop(ctx); UniversalService.start(ctx) }
            })
        }
        if (on && last != null) Text("Último: ${Gestures.label(last!!)} → $result", color = Teal, style = MaterialTheme.typography.bodySmall)
        Text("Mientras esté activo, los botones de música controlan Gestores y no Spotify.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

/* ---------------- Piezas comunes ---------------- */
/** Módulo desplegable con bisel dorado; el número va en romano como en una esfera de reloj. */
@Composable
private fun Module(code: String, title: String, open: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    val eng = LocalEng.current
    val roman = listOf("XII", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI").getOrElse(code.toIntOrNull() ?: 0) { code }
    val arrow by androidx.compose.animation.core.animateFloatAsState(if (open) 90f else 0f, label = "flecha")
    // Entrada: el módulo sube y aparece
    val enter = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { delay((code.toIntOrNull() ?: 0) * 45L); enter.animateTo(1f, tween(450)) }
    Column(Modifier.fillMaxWidth().graphicsLayer { alpha = enter.value; translationY = (1 - enter.value) * 40f }) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .border(if (open) 1.5.dp else 1.dp, if (open) eng.goldBrush() else androidx.compose.ui.graphics.SolidColor(eng.border), RoundedCornerShape(14.dp))
                .background(CardColor).clickable(onClick = onToggle).padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(34.dp).clip(CircleShape).border(1.dp, eng.goldBrush(), CircleShape), contentAlignment = Alignment.Center) {
                Text(roman, color = Teal, style = MaterialTheme.typography.labelSmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
            }
            Spacer(Modifier.width(12.dp))
            Text(title, color = Mint, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("›", color = Teal, fontSize = 26.sp, modifier = Modifier.graphicsLayer { rotationZ = arrow })
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(start = 6.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    val eng = LocalEng.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, eng.border, RoundedCornerShape(12.dp))
        .background(eng.raised).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

@Composable
private fun RuleRow(g: GestureDef, rule: Rule, onTest: () -> Unit, onEdit: () -> Unit) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(g.emoji, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(g.name, fontWeight = FontWeight.SemiBold, color = Mint)
                Text(rule.label, color = if (rule.type == "none") Muted else Teal, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onTest) { Text("Probar") }
            OutlinedButton(onClick = onEdit) { Text("Cambiar") }
        }
    }
}

@Composable
private fun PcCard(cfg: Config, onSave: (Config) -> Unit, onTest: () -> Unit) {
    var addr by remember(cfg.pcAddress) { mutableStateOf(cfg.pcAddress) }
    var token by remember(cfg.pcToken) { mutableStateOf(cfg.pcToken) }
    var scanMsg by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    Card {
        Text("💻 Controlar el PC", fontWeight = FontWeight.SemiBold, color = Mint)
        Text("Abre pc/Iniciar_Gestores_PC.bat en tu PC y escanea el QR que aparece. El PC y el celular deben estar en el mismo Wi-Fi de tu casa.",
            color = Muted, style = MaterialTheme.typography.bodySmall)
        Button(onClick = {
            com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(ctx).startScan()
                .addOnSuccessListener { code ->
                    val uri = Uri.parse(code.rawValue ?: "")
                    val a = uri.getQueryParameter("addr"); val t = uri.getQueryParameter("token")
                    if (uri.scheme == "gestores" && uri.host == "pc" && !a.isNullOrBlank() && !t.isNullOrBlank()) {
                        addr = a; token = t
                        onSave(cfg.copy(pcAddress = a, pcToken = t))
                        scanMsg = "✓ PC vinculado: $a"
                    } else scanMsg = "⚠ Ese QR no es el de Gestores PC"
                }
                .addOnFailureListener { scanMsg = "⚠ No se pudo abrir el escáner" }
        }) { Text("📷 Escanear QR del PC") }
        if (scanMsg.isNotBlank()) Text(scanMsg, color = Teal, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(addr, { addr = it.trim() }, label = { Text("Dirección del PC") },
            placeholder = { Text("192.168.1.10:8765") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(token, { token = it.trim() }, label = { Text("Clave del PC") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSave(cfg.copy(pcAddress = addr, pcToken = token.ifBlank { "gestores" })) }) { Text("Guardar") }
            OutlinedButton(onClick = onTest) { Text("Probar conexión") }
        }
    }
}

/* ---------------- Elegir la acción de un gesto ---------------- */

private data class AppInfo(val label: String, val pkg: String)

private fun installedApps(ctx: Context): List<AppInfo> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
        .filter { it.pkg != ctx.packageName }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

private val phonePresets = listOf(
    Rule("camera", "", "Abrir la cámara"),
    Rule("flashlight", "", "Linterna (encender / apagar)"),
    Rule("media", "playpause", "Música: play / pausa"),
    Rule("media", "next", "Música: siguiente"),
    Rule("media", "prev", "Música: anterior"),
    Rule("volume", "up", "Subir volumen"),
    Rule("volume", "down", "Bajar volumen"),
    Rule("dnd", "", "🔕 No molestar (encender / apagar)"),
    Rule("vibrate_mode", "", "📳 Timbre ↔ vibración"),
)

/** Atajos a las otras dos apps. */
private val appPresets = listOf(
    Rule("open_url", "https://guardian-fauna.netlify.app/#/escaner", "🦜 Guardián: abrir el escáner"),
    Rule("open_url", "https://guardian-fauna.netlify.app/#/alertas/confirmar", "🦜 Guardián: confirmar la última alerta"),
    Rule("open_url", "timecapsule://nueva", "💌 Time Capsule: cápsula rápida"),
    Rule("open_app", "com.timecapsule.app", "💌 Abrir Time Capsule"),
)

@Composable
private fun RuleDialog(g: GestureDef, current: Rule, onDismiss: () -> Unit, onSave: (Rule) -> Unit) {
    val ctx = LocalContext.current
    var mode by remember { mutableStateOf("list") }   // list | app | url | webhook | pcopen | call | wa
    var text by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    val callPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var method by remember { mutableStateOf("GET") }
    val apps = remember { installedApps(ctx) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${g.emoji} ${g.name}") },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                when (mode) {
                    "list" -> {
                        item { Header("En el celular") }
                        item { Option("📱 Abrir una app…", false) { mode = "app" } }
                        item { Option("🔗 Abrir un enlace…", false) { mode = "url"; text = "https://" } }
                        items(phonePresets) { r -> Option(r.label, current == r) { onSave(r) } }
                        item { Header("Personas") }
                        item { Option("📞 Llamar a un favorito…", current.type == "call") { mode = "call"; text = ""; name = "" } }
                        item { Option("💬 WhatsApp con mensaje listo…", current.type == "whatsapp") { mode = "wa"; text = ""; name = ""; msg = "Voy en camino 🙌" } }
                        item { Header("Guardián y Time Capsule") }
                        items(appPresets) { r -> Option(r.label, current == r) { onSave(r) } }
                        item { Header("Casa inteligente (lista para cuando tengas aparatos)") }
                        item {
                            Option("💡 Luz, enchufe o aparato (webhook)…", current.type == "webhook") {
                                mode = "webhook"; text = if (current.type == "webhook") current.value else "http://"
                            }
                        }
                        item { Header("En el PC") }
                        items(PcActions.presets) { (cmd, label) ->
                            val r = Rule("pc", cmd, label)
                            Option(label, current == r) { onSave(r) }
                        }
                        item { Option("💻 PC: abrir programa o página…", false) { mode = "pcopen"; text = "" } }
                        item { HorizontalDivider(Modifier.padding(vertical = 6.dp)) }
                        item { Option("Sin acción", current.type == "none") { onSave(Rule()) } }
                    }
                    "app" -> {
                        item { Header("Elige la app") }
                        items(apps, key = { it.pkg }) { a ->
                            Option(a.label, current.value == a.pkg) { onSave(Rule("open_app", a.pkg, "Abrir ${a.label}")) }
                        }
                    }
                    "url" -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(text, { text = it.trim() }, label = { Text("Enlace") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Text("Ej.: https://youtube.com", color = Muted, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { onSave(Rule("open_url", text, "Abrir $text")) }, enabled = text.length > 8) { Text("Guardar") }
                        }
                    }
                    "webhook" -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(text, { text = it.trim() }, label = { Text("Dirección del dispositivo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(method == "GET", { method = "GET" }, label = { Text("GET") })
                                FilterChip(method == "POST", { method = "POST" }, label = { Text("POST") })
                            }
                            Text(
                                "Ejemplos:\n• Shelly: http://192.168.1.50/relay/0?turn=toggle\n" +
                                    "• Tasmota/ESP32: http://192.168.1.60/cm?cmnd=Power%20TOGGLE\n" +
                                    "• Home Assistant: http://IP:8123/api/webhook/mi_luz (POST)\n" +
                                    "• IFTTT: https://maker.ifttt.com/trigger/EVENTO/with/key/CLAVE",
                                color = Muted, style = MaterialTheme.typography.bodySmall,
                            )
                            Button(onClick = { onSave(Rule("webhook", text, "Dispositivo: ${Uri.parse(text).host ?: text}", method)) },
                                enabled = text.length > 10) { Text("Guardar") }
                        }
                    }
                    "call" -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(text, { text = it.trim() }, label = { Text("Número") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Text("Si das el permiso de llamadas, llama directo. Si no, abre el marcador con el número listo.",
                                color = Muted, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = {
                                callPermission.launch(Manifest.permission.CALL_PHONE)
                                onSave(Rule("call", text, "Llamar a ${name.ifBlank { text }}"))
                            }, enabled = text.count { it.isDigit() } >= 7) { Text("Guardar") }
                        }
                    }
                    "wa" -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(text, { text = it.trim() }, label = { Text("Número con indicativo (ej. 57 300…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(msg, { msg = it }, label = { Text("Mensaje") }, modifier = Modifier.fillMaxWidth())
                            Text("WhatsApp se abre con el mensaje escrito; tú tocas enviar (así nunca se envía nada sin querer).",
                                color = Muted, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { onSave(Rule("whatsapp", text, "WhatsApp a ${name.ifBlank { text }}", msg)) },
                                enabled = text.count { it.isDigit() } >= 10) { Text("Guardar") }
                        }
                    }
                    "pcopen" -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(text, { text = it }, label = { Text("Programa o página") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Text("Ej.: spotify, notepad, calc, https://youtube.com o C:\\ruta\\programa.exe", color = Muted, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { onSave(Rule("pc", "open:${text.trim()}", "PC: abrir ${text.trim()}")) }, enabled = text.isNotBlank()) { Text("Guardar") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (mode == "list") onDismiss() else mode = "list" }) { Text(if (mode == "list") "Cerrar" else "Atrás") }
        },
    )
}

@Composable
private fun Header(t: String) =
    Text(t, color = Gold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))

@Composable
private fun Option(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        (if (selected) "● " else "") + label,
        color = if (selected) Teal else Mint,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 8.dp),
    )
}
