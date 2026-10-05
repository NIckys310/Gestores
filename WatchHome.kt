package com.gestores.app

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/* Colores del reloj (cámbialos aquí) */
private val Bezel = Brush.sweepGradient(listOf(Color(0xFF26D0CE), Color(0xFF1A8FB0), Color(0xFF0B4558), Color(0xFF26D0CE)))
private val Face = Color(0xFF02141A)
private val Strap = Color(0xFF0B3F50)
private val Teal = Color(0xFF26D0CE)
private val Gold = Color(0xFFFFD166)
private val Mint = Color(0xFFE8F4F2)
private val Muted = Color(0xFF8FB5BC)

private const val PAGES = 4   // 0 esfera · 1 apps · 2 gestos · 3 ajustes rápidos

/**
 * Pantalla principal: el celular se ve como un RELOJ.
 * Desliza dentro de la esfera para pasar entre: hora → apps → gestos → ajustes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WatchHome(
    cfg: Config,
    onChange: (Config) -> Unit,
    onEditGesture: (GestureDef) -> Unit,
    onOpenSettings: () -> Unit,
    say: (String) -> Unit,
) {
    val pager = rememberPagerState { PAGES }
    val scope = rememberCoroutineScope()

    Box(
        Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF0B3F50), Color(0xFF02141A)))),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            val dial: Dp = minOf(maxWidth - 24.dp, maxHeight * 0.62f, 400.dp)

            // Correas del reloj
            Box(Modifier.size(dial * 0.46f, dial * 1.55f).clip(RoundedCornerShape(28.dp)).background(Strap))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    // Cuerpo + bisel
                    Box(
                        Modifier.size(dial).clip(CircleShape).background(Face).border(8.dp, Bezel, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                            when (page) {
                                0 -> FacePage(cfg, dial)
                                1 -> AppsPage(cfg, dial, onOpenGestures = { scope.launch { pager.animateScrollToPage(2) } })
                                2 -> GesturesPage(cfg, dial, onEditGesture)
                                else -> QuickPage(cfg, dial, onChange, onOpenSettings)
                            }
                        }
                    }
                    // Corona: vuelve a la esfera
                    Box(
                        Modifier.offset(x = dial / 2 + 6.dp).size(14.dp, 40.dp).clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFB8D6D3)).clickable { scope.launch { pager.animateScrollToPage(0) } }
                    )
                }
                Spacer(Modifier.height(14.dp))
                // Puntos de página
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(PAGES) { i ->
                        Box(Modifier.size(if (pager.currentPage == i) 10.dp else 7.dp).clip(CircleShape)
                            .background(if (pager.currentPage == i) Teal else Muted.copy(alpha = 0.5f))
                            .clickable { scope.launch { pager.animateScrollToPage(i) } })
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(listOf("Hora", "Apps", "Gestos", "Ajustes")[pager.currentPage], color = Muted, fontSize = 13.sp)
            }
        }
    }
}

/* ---------------- 1. Esfera: hora, estado y confirmación ---------------- */
@Composable
private fun FacePage(cfg: Config, dial: Dp) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val running by PhoneBus.running.collectAsState()
    val last by PhoneBus.lastGesture.collectAsState()
    val result by PhoneBus.result.collectAsState()
    val pending by Gate.pending.collectAsState()
    val now by produceState(LocalDateTime.now()) { while (true) { value = LocalDateTime.now(); delay(1000) } }
    val es = Locale("es", "CO")

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Anillo de segundos
        Canvas(Modifier.fillMaxSize().padding(14.dp)) {
            val stroke = 4.dp.toPx()
            drawArc(Teal.copy(alpha = 0.15f), 0f, 360f, false, style = Stroke(stroke),
                topLeft = Offset(stroke, stroke), size = Size(size.width - 2 * stroke, size.height - 2 * stroke))
            drawArc(if (running) Teal else Muted, -90f, now.second * 6f + 1f, false, style = Stroke(stroke),
                topLeft = Offset(stroke, stroke), size = Size(size.width - 2 * stroke, size.height - 2 * stroke))
        }
        Column(
            Modifier.padding(horizontal = dial * 0.14f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(now.format(DateTimeFormatter.ofPattern("EEE d MMM", es)).replaceFirstChar { it.uppercase() }, color = Muted, fontSize = 13.sp)
            Text(now.format(DateTimeFormatter.ofPattern("h:mm", es)), color = Mint, fontSize = (dial.value * 0.2f).sp, fontWeight = FontWeight.Light)
            val p = pending
            if (p != null) {
                Text("❓ ${Gestures.label(p)}", color = Gold, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Text("¿${cfg.rules[p]?.label ?: ""}?", color = Mint, fontSize = 12.sp, textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("Sí", Teal, Color.Black) { scope.launch { Gate.confirm(ctx) } }
                    Pill("No", Color(0xFF173F48), Mint) { Gate.cancel(ctx) }
                }
            } else {
                Pill(if (running) "● Detectando" else "▶ Activar gestos", if (running) Teal else Color(0xFF173F48), if (running) Color.Black else Mint) {
                    if (running) PhoneGestureService.stop(ctx) else PhoneGestureService.start(ctx)
                }
                Text(
                    if (running) (last?.let { Gestures.label(it) + "\n" } ?: "") + result
                    else if (cfg.isActiveNow()) "Listo para usar" else "⏸ Fuera de horario",
                    color = if (cfg.isActiveNow()) Muted else Gold, fontSize = 12.sp, textAlign = TextAlign.Center,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/* ---------------- 2. Apps en círculo, con Gestores en el centro ---------------- */
@Composable
private fun AppsPage(cfg: Config, dial: Dp, onOpenGestures: () -> Unit) {
    val ctx = LocalContext.current
    var favs by remember { mutableStateOf(WatchApps.load(ctx, cfg)) }
    var picking by remember { mutableStateOf(false) }
    val center = dial * 0.2f
    val iconSize = dial * 0.15f
    val radius = dial * 0.31f
    val slots = favs.take(8) + "+"

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Gestores en el centro
        Image(painterResource(R.drawable.logo), "Gestores",
            Modifier.size(center).clip(CircleShape).clickable(onClick = onOpenGestures))
        // Apps alrededor
        slots.forEachIndexed { i, pkg ->
            val angle = Math.toRadians(-90.0 + i * 360.0 / slots.size)
            Box(Modifier.offset(x = radius * cos(angle).toFloat(), y = radius * sin(angle).toFloat())) {
                if (pkg == "+") {
                    Box(Modifier.size(iconSize).clip(CircleShape).background(Color(0xFF173F48)).clickable { picking = true },
                        contentAlignment = Alignment.Center) { Text("+", color = Teal, fontSize = 22.sp) }
                } else AppIcon(pkg, iconSize) { WatchApps.open(ctx, pkg) }
            }
        }
    }
    if (picking) AppPicker(favs, onDismiss = { picking = false }) { chosen ->
        favs = chosen; WatchApps.save(ctx, chosen); picking = false
    }
}

@Composable
private fun AppIcon(pkg: String, size: Dp, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val bmp = remember(pkg) { runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(128, 128).asImageBitmap() }.getOrNull() }
    if (bmp != null) Image(bmp, pkg, Modifier.size(size).clip(CircleShape).clickable(onClick = onClick))
    else Box(Modifier.size(size).clip(CircleShape).background(Color(0xFF173F48)).clickable(onClick = onClick))
}

@Composable
private fun AppPicker(current: List<String>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    val ctx = LocalContext.current
    val apps = remember { WatchApps.installed(ctx) }
    var chosen by remember { mutableStateOf(current.take(8)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apps del reloj (máx. 8)") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(apps, key = { it.second }) { (label, pkg) ->
                    val on = pkg in chosen
                    Row(Modifier.fillMaxWidth().clickable {
                        chosen = if (on) chosen - pkg else if (chosen.size < 8) chosen + pkg else chosen
                    }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = on, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(chosen) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/* ---------------- 3. Gestos y su acción ---------------- */
@Composable
private fun GesturesPage(cfg: Config, dial: Dp, onEditGesture: (GestureDef) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = dial * 0.16f, vertical = dial * 0.13f),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { Text("Gestos", color = Teal, fontWeight = FontWeight.SemiBold) }
        items(Gestures.all, key = { it.id }) { g ->
            val rule = cfg.rules[g.id] ?: Rule()
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color(0xFF0E2E36))
                    .clickable { onEditGesture(g) }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(g.emoji, fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(g.name, color = Mint, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(rule.label, color = if (rule.type == "none") Muted else Teal, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        item { Text("Toca un gesto para cambiar su acción", color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center) }
    }
}

/* ---------------- 4. Ajustes rápidos ---------------- */
@Composable
private fun QuickPage(cfg: Config, dial: Dp, onChange: (Config) -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = dial * 0.15f, vertical = dial * 0.12f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text("Ajustes", color = Teal, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Preguntar antes", color = Mint, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Switch(checked = cfg.confirm, onCheckedChange = { onChange(cfg.copy(confirm = it)) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sensibilidad", color = Mint, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Pill("−", Color(0xFF173F48), Mint) { onChange(cfg.copy(sensitivity = (cfg.sensitivity - 0.1f).coerceAtLeast(0.6f))) }
            Text(" %.1f ".format(cfg.sensitivity), color = Teal, fontSize = 13.sp)
            Pill("+", Color(0xFF173F48), Mint) { onChange(cfg.copy(sensitivity = (cfg.sensitivity + 0.1f).coerceAtMost(1.6f))) }
        }
        Text(cfg.scheduleText(), color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
        Pill("⚙️ Más ajustes", Teal, Color.Black, onClick = onOpenSettings)
    }
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp))
}

/** Apps que se muestran dentro del reloj. */
object WatchApps {
    fun installed(ctx: Context): List<Pair<String, String>> {
        val pm = ctx.packageManager
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .filter { it.second != ctx.packageName }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
    }

    fun load(ctx: Context, cfg: Config): List<String> {
        ctx.prefs().getString("watch_apps", null)?.let { s -> return s.split(",").filter { it.isNotBlank() } }
        // Por defecto: las apps que abren tus gestos + algunas comunes
        val installed = installed(ctx).map { it.second }.toSet()
        val fromRules = cfg.rules.values.filter { it.type == "open_app" }.map { it.value }
        val common = listOf("com.whatsapp", "com.spotify.music", "com.google.android.youtube", "com.sec.android.app.camera",
            "com.instagram.android", "com.google.android.apps.maps", "com.android.chrome", "com.sec.android.gallery3d")
        return (fromRules + common).filter { it in installed }.distinct().take(8)
    }

    fun save(ctx: Context, pkgs: List<String>) = ctx.prefs().edit().putString("watch_apps", pkgs.joinToString(",")).apply()

    fun open(ctx: Context, pkg: String) {
        ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
