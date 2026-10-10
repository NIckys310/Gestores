package com.gestores.app

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

/* Colores "modo ingeniero": salen del tema elegido en AJUSTES (ver Theme.kt) */
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
            var dark by rememberSaveable { mutableStateOf(ThemePrefs.load(this)) }
            LaunchedEffect(dark) {
                val t = android.graphics.Color.TRANSPARENT
                val bars = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            GestoresTheme(dark) {
                WatchShell(dark, onDark = { dark = it; ThemePrefs.save(this, it) })
            }
        }
    }
}

/** Arranca mostrando el reloj (WatchHome); al tocarlo se despliega la app completa. Atrás vuelve al reloj. */
@Composable
fun WatchShell(dark: Boolean, onDark: (Boolean) -> Unit) {
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
        if (isOpen) PhoneApp(dark, onDark, onBackToWatch = { open = false }) else WatchHome(onOpen = { open = true })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneApp(dark: Boolean, onDark: (Boolean) -> Unit, onBackToWatch: () -> Unit = {}) {
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
                            Text("GESTORES://CONSOLE", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(if (running) Teal else Gold))
                                Spacer(Modifier.width(6.dp))
                                Text(if (running) "SENSOR ONLINE" else "SENSOR OFFLINE",
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
                Module("00", "AJUSTES", "00" in openMods, { toggle("00") }) {
                    Card {
                        Text("TEMA DE LA INTERFAZ", color = Mint, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = dark, onClick = { onDark(true) }, label = { Text("◼ NEGRO") })
                            FilterChip(selected = !dark, onClick = { onDark(false) }, label = { Text("◻ BLANCO") })
                        }
                        Text("Se guarda automáticamente.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // 01. Celular en la muñeca
            item(key = "01") {
                Module("01", "MUÑECA", "01" in openMods, { toggle("01") }) {
                    WristCard(onTestWatch = { PhoneGestureService.notifyWatch(ctx, "⌚ Prueba de Gestores", "Si ves esto en tu reloj, ¡todo está conectado!"); say("Aviso enviado al reloj") })
                }
            }

            // 02. Permiso para abrir apps
            if (!canOverlay) item(key = "02") {
                Module("02", "PERMISOS  [!]", "02" in openMods, { toggle("02") }) {
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
            item(key = "03") { Module("03", "ENTRENAR IA", "03" in openMods, { toggle("03") }) { TrainCard() } }

            // 04. Qué hace cada gesto
            item(key = "04") {
                Module("04", "GESTOS", "04" in openMods, { toggle("04") }) {
                    Gestures.all.forEach { g ->
                        RuleRow(g, cfg.rules[g.id] ?: Rule(),
                            onTest = { scope.launch { say(ActionRunner.execute(ctx, cfg.rules[g.id] ?: Rule(), cfg)) } },
                            onEdit = { editing = g })
                    }
                }
            }

            // 05. Botones de música del reloj
            item(key = "05") {
                Module("05", "BOTONES RELOJ", "05" in openMods, { toggle("05") }) {
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
                Module("06", "PC", "06" in openMods, { toggle("06") }) {
                    PcCard(cfg,
                        onSave = { update(it); say("Guardado") },
                        onTest = { scope.launch { say(ActionRunner.execute(ctx, Rule("pc", "ping", "Conexión con el PC"), cfg)) } })
                }
            }

            // 07. Reloj Wear OS (si algún día tienes uno)
            item(key = "07") {
                Module("07", "WEAR OS", "07" in openMods, { toggle("07") }) {
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
                Module("08", "LOG", "08" in openMods, { toggle("08") }) {
                    Card {
                        if (log.isEmpty()) Text("> sin eventos", color = Muted)
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
@Composable
private fun TrainCard() {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val training by PhoneBus.trainingTarget.collectAsState()
    LaunchedEffect(training) { refresh++ }
    Card {
        Text("🧠 Enseñarle gestos a la IA", fontWeight = FontWeight.Bold, color = Mint, style = MaterialTheme.typography.titleMedium)
        Text("Inventa un movimiento, hazlo 5 veces y la IA lo aprende. Luego asígnale una acción abajo.",
            color = Muted, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Gestures.ai.forEach { g ->
                val n = remember(refresh) { Learner.count(g.id) }
                OutlinedButton(onClick = {
                    PhoneBus.trainCount.value = Learner.count(g.id)
                    PhoneBus.trainingTarget.value = g.id
                    if (!PhoneBus.running.value) PhoneGestureService.start(ctx)
                }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                    Text("${g.name.removePrefix("Gesto ")}\n${if (n == 0) "nuevo" else "$n ✓"}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun TrainingDialog(id: String) {
    val count by PhoneBus.trainCount.collectAsState()
    val result by PhoneBus.result.collectAsState()
    AlertDialog(
        onDismissRequest = {},
        title = { Text("🧠 ${Gestures.get(id)?.name ?: id}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (count < 5) "Con el celular en la muñeca, haz tu gesto y quédate quieto un momento.\n\nMuestra ${count + 1} de 5"
                     else "¡Listo! La IA ya aprendió este gesto.")
                if (result.isNotBlank()) Text(result, color = Gold)
            }
        },
        confirmButton = { TextButton(onClick = { PhoneBus.trainingTarget.value = null }) { Text(if (count >= 3) "Terminar" else "Cancelar") } },
        dismissButton = { TextButton(onClick = { Learner.clear(id); PhoneBus.trainCount.value = 0 }) { Text("Borrar muestras") } },
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
/** Módulo desplegable estilo consola: "[+] 04 > GESTOS". */
@Composable
private fun Module(code: String, title: String, open: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    val eng = LocalEng.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                .border(1.dp, if (open) Teal else eng.border, RoundedCornerShape(4.dp))
                .background(CardColor).clickable(onClick = onToggle).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (open) "[–]" else "[+]", color = if (open) Teal else Gold, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Text(code, color = Muted)
            Spacer(Modifier.width(8.dp))
            Text("> $title", color = Mint, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(start = 8.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    val eng = LocalEng.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).border(1.dp, eng.border, RoundedCornerShape(4.dp))
        .background(CardColor).padding(14.dp),
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
    Card {
        Text("💻 Controlar el PC", fontWeight = FontWeight.SemiBold, color = Mint)
        Text("Abre pc/Iniciar_Gestores_PC.bat en tu PC: te muestra la dirección. El PC y el celular deben estar en el mismo Wi-Fi.",
            color = Muted, style = MaterialTheme.typography.bodySmall)
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
)

@Composable
private fun RuleDialog(g: GestureDef, current: Rule, onDismiss: () -> Unit, onSave: (Rule) -> Unit) {
    val ctx = LocalContext.current
    var mode by remember { mutableStateOf("list") }   // list | app | url | webhook | pcopen
    var text by remember { mutableStateOf("") }
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
                        item { Header("Dispositivos inteligentes") }
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
