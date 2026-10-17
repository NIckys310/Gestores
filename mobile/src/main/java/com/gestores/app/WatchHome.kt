package com.gestores.app

import android.app.Activity
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.viewmodel.compose.viewModel
import android.graphics.Typeface
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/* =========================================================
   EL RELOJ INTELIGENTE (única pantalla de la app)
   El celular simula un smartwatch moderno: el reloj siempre está a la vista
   (cuadrado o redondo, se elige en Ajustes) y todo se abre DENTRO de él.
   • Corona: abre el menú / vuelve.   • Botón de abajo: Gestores directo.
   • Deslizar a la derecha: volver.   • Deslizar en la esfera: cambiar de esfera.
   • Mantener presionada la esfera: siempre encendido.
   ========================================================= */
private val FACES = listOf("Digital", "Cronógrafo", "Minimalista", "Clásica", "Deportiva")
private val ROMAN = listOf("XII", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI")

@Composable
fun WatchHome(
    mode: String,
    onMode: (String) -> Unit,
    vm: RelojViewModel = viewModel(factory = RelojViewModel.factory(LocalContext.current)),
) {
    val ctx = LocalContext.current
    val eng = LocalEng.current
    // Qué se ve y qué esfera está puesta: lo maneja el ViewModel (sobrevive a girar el celular)
    val screen by vm.pantalla.collectAsState()
    val face by vm.esfera.collectAsState()
    val aod by WatchState.aod.collectAsState()
    val shape by WatchState.shape.collectAsState()
    val running by PhoneBus.running.collectAsState()
    val gestoresOn by WatchState.gestoresOn.collectAsState()
    val comp = rememberComplications()

    // Hora fluida (60 fps) o, en siempre encendido, una vez por segundo para ahorrar batería
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(aod) {
        while (true) {
            if (aod) { now = System.currentTimeMillis(); delay(1000) } else withFrameMillis { now = System.currentTimeMillis() }
        }
    }

    // Siempre encendido: la pantalla no se apaga y baja el brillo (solo en la esfera)
    val dimmed = aod && screen == "face"
    val view = LocalView.current
    DisposableEffect(dimmed) {
        view.keepScreenOn = dimmed
        val w = (ctx as? Activity)?.window
        w?.attributes = w?.attributes?.apply { screenBrightness = if (dimmed) 0.04f else -1f }
        onDispose { view.keepScreenOn = false; w?.attributes = w?.attributes?.apply { screenBrightness = -1f } }
    }

    BackHandler(enabled = vm.puedeVolver) { vm.atras() }

    val round = shape == "round"
    Box(
        Modifier.fillMaxSize().background(if (dimmed) Color.Black else eng.bg)
            .background(Brush.radialGradient(listOf(eng.primary.copy(alpha = if (dimmed) 0f else 0.12f), Color.Transparent))),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val dial = minOf(maxWidth - 64.dp, maxHeight * 0.55f, 360.dp)
            val caseShape = if (round) CircleShape else RoundedCornerShape(dial * 0.24f)
            val screenShape = if (round) CircleShape else RoundedCornerShape(dial * 0.21f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Band(top = true, dim = dimmed, width = dial * 0.62f)

                Box(contentAlignment = Alignment.Center) {
                    SideButtons(dial, screen, onCrown = vm::corona, onButton = vm::botonLateral)
                    Box(
                        Modifier.size(dial).clip(caseShape)
                            .background(Brush.linearGradient(listOf(eng.goldLight, eng.border, eng.primary, eng.border)))   // caja de metal cobrizo
                            .padding(7.dp).clip(screenShape).background(Color(0xFF050505))                                     // vidrio negro
                            .padding(4.dp).clip(screenShape).background(if (dimmed) Color.Black else if (eng.isDark) Color(0xFF0C0A09) else Color(0xFFFBF9F7))
                            .pointerInput(screen) {
                                var drag = 0f
                                detectHorizontalDragGestures(onDragEnd = {
                                    vm.deslizar(drag)
                                    drag = 0f
                                }) { _, d -> drag += d }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        // Entrar a una app: crece desde el centro, como en un reloj de verdad
                        AnimatedContent(screen, transitionSpec = {
                            val deeper = targetState != "face" && (initialState == "face" || targetState != "apps")
                            if (deeper) (fadeIn(tween(350)) + scaleIn(tween(380, easing = FastOutSlowInEasing), initialScale = 0.6f)) togetherWith
                                (fadeOut(tween(200)) + scaleOut(tween(250), targetScale = 1.25f))
                            else (fadeIn(tween(350)) + scaleIn(tween(380), initialScale = 1.25f)) togetherWith
                                (fadeOut(tween(200)) + scaleOut(tween(250), targetScale = 0.6f))
                        }, label = "pantalla") { s ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                when (s) {
                                    "face" -> FaceView(face, now, aod, comp, running,
                                        onTap = { vm.abrir(RelojViewModel.MENU) },
                                        onLong = { WatchState.setAod(ctx, !aod) })
                                    "apps" -> AppMenu(round, vm::abrir)
                                    else -> WatchAppScreen(s, mode, onMode)
                                }
                            }
                        }
                        GestureBurst()
                        AskOverlay()
                        ConfirmOverlay()
                    }
                }

                Band(top = false, dim = dimmed, width = dial * 0.62f)
                Spacer(Modifier.height(12.dp))

                // Debajo del reloj: nombre de lo que se ve
                if (screen == "face") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("‹", color = eng.primary, fontSize = 28.sp, modifier = Modifier.clickable { vm.cambiarEsfera(-1) }.padding(horizontal = 8.dp))
                        Text(FACES[face], color = eng.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                        Text("›", color = eng.primary, fontSize = 28.sp, modifier = Modifier.clickable { vm.cambiarEsfera(+1) }.padding(horizontal = 8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(FACES.size) { i -> Box(Modifier.size(if (i == face) 9.dp else 6.dp).clip(CircleShape).background(if (i == face) eng.primary else eng.border)) }
                    }
                } else {
                    val app = WatchApps.get(screen)
                    Text(if (app != null) "${app.emoji} ${app.name}" else "Menú", color = eng.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    (if (screen == "face") "Toca la pantalla o la corona para abrir el menú" else "Corona o deslizar a la derecha: volver") +
                        if (gestoresOn) "   ·   ✋ activo" else "",
                    color = eng.muted, fontSize = 12.sp,
                )
            }
        }
    }
}

/* ---------------- Corona y botón lateral ---------------- */
@Composable
private fun SideButtons(dial: androidx.compose.ui.unit.Dp, screen: String, onCrown: () -> Unit, onButton: () -> Unit) {
    val eng = LocalEng.current
    val turn = remember { Animatable(0f) }
    LaunchedEffect(screen) { turn.animateTo(turn.value + 90f, tween(400)) }   // la corona gira al cambiar de pantalla
    // Corona digital
    Box(
        Modifier.offset(x = dial / 2 + 3.dp, y = -dial * 0.12f).width(16.dp).height(44.dp).clip(RoundedCornerShape(5.dp))
            .background(Brush.verticalGradient(listOf(eng.goldLight, eng.primary, eng.border)))
            .clickable(onClick = onCrown),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val shift = (turn.value % 90f) / 90f * 7f
            var y = shift
            while (y < size.height) { drawLine(eng.border, Offset(2f, y), Offset(size.width - 2f, y), 2f); y += 7f }
        }
    }
    // Botón lateral: abre Gestores
    Box(
        Modifier.offset(x = dial / 2 + 1.dp, y = dial * 0.14f).width(9.dp).height(54.dp).clip(RoundedCornerShape(4.dp))
            .background(Brush.verticalGradient(listOf(eng.primary, eng.border)))
            .clickable(onClick = onButton),
    )
}

/* ---------------- Esfera ---------------- */
@Composable
private fun FaceView(face: Int, now: Long, aod: Boolean, comp: Complications, running: Boolean, onTap: () -> Unit, onLong: () -> Unit) {
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onTap() }, onLongPress = { onLong() }) }, contentAlignment = Alignment.Center) {
        AnimatedContent(face, transitionSpec = {
            (fadeIn(tween(450)) + scaleIn(tween(450, easing = FastOutSlowInEasing), initialScale = 0.85f)) togetherWith
                (fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 1.1f))
        }, label = "esfera") { f ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when (f) {
                    0 -> DigitalFace(now, aod, comp, running)
                    1 -> ChronoFace(now, aod, comp)
                    2 -> MinimalFace(now, aod)
                    3 -> RomanFace(now, aod)
                    else -> SportFace(now, aod)
                }
                if (f != 0 && f != 1 && !aod) ComplicationRow(comp, Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp))
            }
        }
    }
}

/* ---------------- Menú dentro del reloj ---------------- */
@Composable
private fun AppMenu(round: Boolean, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val eng = LocalEng.current
    val on by WatchState.gestoresOn.collectAsState()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (round) 34.dp else 16.dp, vertical = if (round) 36.dp else 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Sección principal: GESTORES
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(eng.raised, eng.panel)))
                .border(1.dp, eng.goldBrush(), RoundedCornerShape(18.dp))
                .clickable { onOpen("gestos") }.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("✋", fontSize = 26.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("GESTORES", color = eng.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text("Todos los gestos y lo que hacen", color = eng.muted, fontSize = 10.sp)
            }
            // Interruptor general sin entrar a la sección
            Box(
                Modifier.size(width = 44.dp, height = 24.dp).clip(RoundedCornerShape(50)).background(if (on) eng.primary else eng.border)
                    .clickable { WatchState.setGestores(ctx, !on) },
                contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
            ) { Box(Modifier.padding(3.dp).size(18.dp).clip(CircleShape).background(Color.White)) }
        }
        WatchApps.all.filter { it.id != "gestos" }.groupBy { it.group }.forEach { (group, apps) ->
            Text(group.uppercase(), color = eng.primary, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold)
            apps.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEachIndexed { i, a ->
                        var shown by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { delay(40L * i); shown = true }
                        val k by animateFloatAsState(if (shown) 1f else 0.4f, tween(300), label = "app")
                        Column(
                            Modifier.width(54.dp).graphicsLayer { scaleX = k; scaleY = k; alpha = k }.clickable { onOpen(a.id) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(eng.raised).border(1.dp, eng.border, RoundedCornerShape(13.dp)),
                                contentAlignment = Alignment.Center) { Text(a.emoji, fontSize = 19.sp) }
                            Text(a.name, color = eng.text, fontSize = 9.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/* ---------------- "¿Deseas…?" antes de una acción con sonido ---------------- */
@Composable
private fun ConfirmOverlay() {
    val ctx = LocalContext.current
    val eng = LocalEng.current
    val pending by WatchState.confirm.collectAsState()
    val (id, rule) = pending ?: return
    LaunchedEffect(pending) { delay(15_000); if (WatchState.confirm.value == pending) ActionRunner.answer(ctx, false) }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(18.dp).clip(RoundedCornerShape(22.dp)).background(eng.panel).border(1.dp, eng.goldBrush(), RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(rule.label.take(2), fontSize = 28.sp)
            Text(ActionRunner.question(rule), color = eng.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text("Gesto: ${Gestures.label(id)}", color = eng.muted, fontSize = 11.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("No") { ActionRunner.answer(ctx, false) }
                Pill("Sí", filled = true) { ActionRunner.answer(ctx, true) }
            }
        }
    }
}

/* ---------------- "¿Fue este gesto?" dentro del reloj ---------------- */
@Composable
private fun AskOverlay() {
    val ctx = LocalContext.current
    val eng = LocalEng.current
    val ask by WatchState.ask.collectAsState()
    val (id, conf) = ask ?: return
    LaunchedEffect(ask) { delay(12_000); if (WatchState.ask.value == ask) PhoneGestureService.answer(ctx, false) }
    Column(
        Modifier.clip(RoundedCornerShape(20.dp)).background(eng.bg.copy(alpha = 0.94f)).border(1.dp, eng.goldBrush(), RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("¿Fue ${Gestures.label(id)}?", color = eng.primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("La IA no está segura (${(conf * 100).toInt()} %)", color = eng.muted, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("✗ No") { PhoneGestureService.answer(ctx, false) }
            Pill("✓ Sí", filled = true) { PhoneGestureService.answer(ctx, true) }
        }
    }
}

/* ---------------- Correa deportiva de silicona café con agujeros ---------------- */
@Composable
private fun Band(top: Boolean, dim: Boolean, width: androidx.compose.ui.unit.Dp) {
    val eng = LocalEng.current
    val band = if (eng.isDark) listOf(Color(0xFF2B1E15), Color(0xFF1A120C)) else listOf(Color(0xFF5A3D27), Color(0xFF3E2A1B))
    Box(
        Modifier.width(width).height(70.dp)
            .clip(if (top) RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp) else RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
            .background(Brush.verticalGradient(if (top) band else band.reversed()))
            .graphicsLayer { alpha = if (dim) 0.25f else 1f },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // canal central y agujeros
            drawLine(Color.Black.copy(alpha = 0.25f), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 3f)
            if (!top) for (i in 0..2) drawCircle(Color.Black.copy(alpha = 0.45f), 5f, Offset(size.width / 2, size.height * (0.35f + i * 0.22f)))
        }
    }
}

/* ---------------- Utilidades de dibujo ---------------- */
private fun angles(now: Long): Triple<Float, Float, Float> {
    val c = Calendar.getInstance().apply { timeInMillis = now }
    val s = c.get(Calendar.SECOND) + c.get(Calendar.MILLISECOND) / 1000f
    val m = c.get(Calendar.MINUTE) + s / 60f
    val h = c.get(Calendar.HOUR) + m / 60f
    return Triple(h * 30f, m * 6f, s * 6f)   // grados
}

private fun DrawScope.hand(deg: Float, length: Float, width: Float, color: Color, tail: Float = 0f) {
    rotate(deg) {
        val p = Path().apply {
            moveTo(center.x - width / 2, center.y + tail)
            lineTo(center.x - width / 4, center.y - length)
            lineTo(center.x, center.y - length - width)
            lineTo(center.x + width / 4, center.y - length)
            lineTo(center.x + width / 2, center.y + tail)
            close()
        }
        drawPath(p, color)
    }
}

private fun DrawScope.ticks(color: Color, majorEvery: Int = 5, majorLen: Float = 16f, minorLen: Float = 6f, inset: Float = 6f) {
    val r = size.minDimension / 2
    for (i in 0 until 60) {
        val a = (i * 6.0 - 90) * PI / 180
        val len = if (i % majorEvery == 0) majorLen else minorLen
        val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
        drawLine(color.copy(alpha = if (i % majorEvery == 0) 1f else 0.5f), center + dir * (r - inset - len), center + dir * (r - inset),
            strokeWidth = if (i % majorEvery == 0) 4f else 1.5f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.text(t: String, at: Offset, sizePx: Float, color: Color, serif: Boolean = false, bold: Boolean = false) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb(); textSize = sizePx; textAlign = Paint.Align.CENTER
        typeface = Typeface.create(if (serif) Typeface.SERIF else Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
    drawContext.canvas.nativeCanvas.drawText(t, at.x, at.y + sizePx / 3, p)
}

private fun DrawScope.pin(color: Color) { drawCircle(color, 9f); drawCircle(Color.Black.copy(alpha = 0.4f), 3f) }

/* ---------------- 1. Clásica romana ---------------- */
@Composable
private fun RomanFace(now: Long, aod: Boolean) {
    val eng = LocalEng.current
    Canvas(Modifier.fillMaxSize()) {
        val r = size.minDimension / 2
        ticks(eng.primary.copy(alpha = if (aod) 0.4f else 1f))
        ROMAN.forEachIndexed { i, n ->
            val a = (i * 30.0 - 90) * PI / 180
            text(n, center + Offset(cos(a).toFloat(), sin(a).toFloat()) * (r - 48f), 30f, if (aod) eng.muted else eng.text)
        }
        if (!aod) text("GESTORES", center + Offset(0f, -r * 0.32f), 20f, eng.primary, bold = true)
        val (h, m, s) = angles(now)
        hand(h, r * 0.48f, 14f, eng.primary, tail = 18f)
        hand(m, r * 0.72f, 10f, eng.goldLight, tail = 22f)
        if (!aod) rotate(s) { drawLine(eng.alert, center + Offset(0f, 30f), center - Offset(0f, r * 0.82f), 3f, cap = StrokeCap.Round) }
        pin(eng.primary)
    }
}

/* ---------------- 2. Cronógrafo con subesferas ---------------- */
@Composable
private fun ChronoFace(now: Long, aod: Boolean, c: Complications) {
    val eng = LocalEng.current
    Canvas(Modifier.fillMaxSize()) {
        val r = size.minDimension / 2
        drawCircle(eng.border, r - 4f, style = Stroke(1.5f))
        ticks(eng.steel, inset = 12f)
        listOf(12, 3, 6, 9).forEach { n ->
            val a = ((n % 12) * 30.0 - 90) * PI / 180
            text("$n", center + Offset(cos(a).toFloat(), sin(a).toFloat()) * (r - 48f), 30f, eng.text, serif = false, bold = true)
        }
        val (h, m, s) = angles(now)
        fun sub(o: Offset, frac: Float, label: String) {
            drawCircle(eng.raised, 40f, center + o); drawCircle(eng.primary, 40f, center + o, style = Stroke(2f))
            rotate(frac * 360f, center + o) { drawLine(eng.alert, center + o, center + o - Offset(0f, 32f), 3f, cap = StrokeCap.Round) }
            text(label, center + o + Offset(0f, 56f), 17f, eng.muted, serif = false)
        }
        if (!aod) {   // subesferas: segundos, batería y pasos
            sub(Offset(-r * 0.45f, 0f), s / 360f, "SEG")
            sub(Offset(r * 0.45f, 0f), (c.battery ?: 0) / 100f, "BAT ${c.battery ?: "–"}%")
            sub(Offset(0f, r * 0.42f), ((c.steps ?: 0) % 10000) / 10000f, "PASOS ${c.steps ?: "–"}")
        }
        hand(h, r * 0.48f, 13f, eng.steel, tail = 16f)
        hand(m, r * 0.74f, 9f, eng.primary, tail = 20f)
        pin(eng.goldLight)
    }
}

/* ---------------- 3. Minimalista ---------------- */
@Composable
private fun MinimalFace(now: Long, aod: Boolean) {
    val eng = LocalEng.current
    Canvas(Modifier.fillMaxSize()) {
        val r = size.minDimension / 2
        for (i in 0 until 12) {
            val a = (i * 30.0 - 90) * PI / 180
            val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
            drawLine(eng.primary, center + dir * (r - 34f), center + dir * (r - 12f), if (i % 3 == 0) 6f else 3f, cap = StrokeCap.Round)
        }
        val day = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_MONTH)
        if (!aod) {   // ventana de fecha
            drawRoundRect(eng.raised, topLeft = center + Offset(r * 0.42f, -16f), size = Size(46f, 32f))
            text("$day", center + Offset(r * 0.42f + 23f, 0f), 22f, eng.text, serif = false, bold = true)
        }
        val (h, m, s) = angles(now)
        rotate(h) { drawLine(eng.text, center, center - Offset(0f, r * 0.5f), 7f, cap = StrokeCap.Round) }
        rotate(m) { drawLine(eng.text, center, center - Offset(0f, r * 0.78f), 4f, cap = StrokeCap.Round) }
        if (!aod) rotate(s) { drawLine(eng.primary, center, center - Offset(0f, r * 0.84f), 1.5f) }
        drawCircle(eng.primary, 6f)
    }
}

/* ---------------- 4. Digital de lujo ---------------- */
@Composable
private fun DigitalFace(now: Long, aod: Boolean, c: Complications, running: Boolean) {
    val eng = LocalEng.current
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val sec = cal.get(Calendar.SECOND) + cal.get(Calendar.MILLISECOND) / 1000f
    Canvas(Modifier.fillMaxSize()) {   // anillo de segundos que se llena en oro
        val r = size.minDimension / 2 - 10f
        drawCircle(eng.border.copy(alpha = 0.5f), r, style = Stroke(4f))
        if (!aod) drawArc(Brush.sweepGradient(listOf(eng.goldLight, eng.primary, eng.goldLight)), -90f, sec * 6f, false,
            topLeft = center - Offset(r, r), size = Size(r * 2, r * 2), style = Stroke(6f, cap = StrokeCap.Round))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)),
            color = if (aod) eng.muted else eng.primary, fontFamily = FontFamily.SansSerif, fontSize = 66.sp, fontWeight = FontWeight.Thin)
        Text(java.text.SimpleDateFormat("EEEE d 'de' MMMM", Locale("es")).format(cal.time).replaceFirstChar { it.uppercase() },
            color = eng.text, fontFamily = FontFamily.SansSerif, fontSize = 14.sp)
        if (!aod) {
            Spacer(Modifier.height(10.dp))
            ComplicationRow(c)
            c.nextAlarm?.let { Text("⏰ $it", color = eng.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
            Text(if (running) "● gestos activos" else "○ gestos apagados", color = if (running) eng.primary else eng.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/* ---------------- 5. Deportiva ---------------- */
@Composable
private fun SportFace(now: Long, aod: Boolean) {
    val eng = LocalEng.current
    Canvas(Modifier.fillMaxSize()) {
        val r = size.minDimension / 2
        // Bisel de minutos con los primeros 15 en rubí
        drawArc(eng.alert.copy(alpha = if (aod) 0.3f else 0.9f), -90f, 90f, false, topLeft = center - Offset(r - 8f, r - 8f), size = Size((r - 8f) * 2, (r - 8f) * 2), style = Stroke(10f))
        ticks(eng.text, majorLen = 20f, inset = 22f)
        for (n in listOf(12, 2, 4, 6, 8, 10)) {
            val a = ((n % 12) * 30.0 - 90) * PI / 180
            text("$n", center + Offset(cos(a).toFloat(), sin(a).toFloat()) * (r - 62f), 38f, if (aod) eng.muted else eng.primary, serif = false, bold = true)
        }
        val (h, m, s) = angles(now)
        hand(h, r * 0.5f, 18f, eng.text, tail = 14f)
        hand(m, r * 0.74f, 12f, eng.text, tail = 18f)
        if (!aod) rotate(s) {
            drawLine(eng.alert, center + Offset(0f, 34f), center - Offset(0f, r * 0.8f), 4f, cap = StrokeCap.Round)
            drawCircle(eng.alert, 10f, center - Offset(0f, r * 0.62f), style = Stroke(3f))
        }
        pin(eng.alert)
    }
}

/* ---------------- Complicaciones ---------------- */
@Composable
private fun ComplicationRow(c: Complications, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Bubble(if (c.charging) "⚡" else "🔋", c.battery?.let { "$it%" } ?: "–")
        Bubble(c.weather.ifBlank { "🌡️" }, c.tempC?.let { "$it°" } ?: "–")
        Bubble("👣", c.steps?.let { if (it >= 1000) "%.1fk".format(it / 1000f) else "$it" } ?: "–")
    }
}

@Composable
private fun Bubble(icon: String, value: String) {
    val eng = LocalEng.current
    Column(
        Modifier.size(50.dp).clip(CircleShape).background(eng.raised).border(1.dp, eng.goldBrush(), CircleShape),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(icon, fontSize = 13.sp)
        Text(value, color = eng.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/* ---------------- Animación al detectar un gesto ---------------- */
@Composable
private fun GestureBurst() {
    val eng = LocalEng.current
    val event by PhoneBus.gestureEvent.collectAsState()
    val gesture by PhoneBus.lastGesture.collectAsState()
    val result by PhoneBus.result.collectAsState()
    val t = remember { Animatable(1f) }
    LaunchedEffect(event) {
        if (event == 0L) return@LaunchedEffect
        t.snapTo(0f); t.animateTo(1f, tween(2200))
    }
    val id = gesture
    if (t.value >= 1f || id == null) return
    val k = t.value
    // Ondas doradas que salen del centro
    Canvas(Modifier.fillMaxSize()) {
        for (i in 0..2) {
            val p = ((k * 1.6f) - i * 0.25f).coerceIn(0f, 1f)
            if (p > 0f) drawCircle(eng.primary.copy(alpha = (1f - p) * 0.8f), radius = size.minDimension / 2 * p, style = Stroke(5f * (1f - p) + 1f))
        }
    }
    val g = Gestures.get(id)
    val pop by rememberInfiniteTransition(label = "mano").animateFloat(0.9f, 1.1f, infiniteRepeatable(tween(260), RepeatMode.Reverse), label = "m")
    Column(
        Modifier.clip(RoundedCornerShape(18.dp)).background(eng.bg.copy(alpha = 0.85f)).border(1.dp, eng.goldBrush(), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .graphicsLayer { alpha = if (k < 0.8f) 1f else (1f - k) * 5f },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(g?.emoji ?: "✋", fontSize = 34.sp, modifier = Modifier.graphicsLayer { scaleX = pop; scaleY = pop })
        Text(g?.name ?: id, color = eng.primary, fontFamily = FontFamily.SansSerif, fontSize = 16.sp)
        Text(result, color = eng.text, fontSize = 12.sp, maxLines = 2)
    }
}
