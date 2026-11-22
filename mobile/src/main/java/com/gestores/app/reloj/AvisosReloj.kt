package com.gestores.app.reloj

import com.gestores.app.*
import com.gestores.app.apps.*
import com.gestores.app.gestos.*
import com.gestores.app.estado.*
import com.gestores.app.idioma.*
import com.gestores.app.fallos.*

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/* Avisos dentro del reloj: "¿Deseas…?", "¿Fue este gesto?" y la animación al detectar un gesto. */

/* ---------------- "¿Deseas…?" antes de una acción con sonido ---------------- */
@Composable
internal fun ConfirmOverlay() {
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
            Text("Gesto: ${Gestures.label(id)}", color = eng.muted, fontSize = 12.sp)
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
internal fun AskOverlay() {
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
        Text("La IA no está segura (${(conf * 100).toInt()} %)", color = eng.muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("✗ No") { PhoneGestureService.answer(ctx, false) }
            Pill("✓ Sí", filled = true) { PhoneGestureService.answer(ctx, true) }
        }
    }
}

/* ---------------- Animación al detectar un gesto ---------------- */
@Composable
internal fun GestureBurst() {
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
