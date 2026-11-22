package com.gestores.app.reloj

import com.gestores.app.*
import com.gestores.app.apps.*
import com.gestores.app.gestos.*
import com.gestores.app.estado.*
import com.gestores.app.idioma.*
import com.gestores.app.fallos.*

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/* Las 5 esferas del reloj, sus complicaciones y las utilidades de dibujo. */

internal val FACES = listOf("Digital", "Cronógrafo", "Minimalista", "Clásica", "Deportiva")
internal val ROMAN = listOf("XII", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI")

/* ---------------- Esfera ---------------- */
@Composable
internal fun FaceView(face: Int, now: Long, aod: Boolean, comp: Complications, running: Boolean, onTap: () -> Unit, onLong: () -> Unit) {
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

/* ---------------- Utilidades de dibujo ---------------- */
internal fun angles(now: Long): Triple<Float, Float, Float> {
    val c = Calendar.getInstance().apply { timeInMillis = now }
    val s = c.get(Calendar.SECOND) + c.get(Calendar.MILLISECOND) / 1000f
    val m = c.get(Calendar.MINUTE) + s / 60f
    val h = c.get(Calendar.HOUR) + m / 60f
    return Triple(h * 30f, m * 6f, s * 6f)   // grados
}

internal fun DrawScope.hand(deg: Float, length: Float, width: Float, color: Color, tail: Float = 0f) {
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

internal fun DrawScope.ticks(color: Color, majorEvery: Int = 5, majorLen: Float = 16f, minorLen: Float = 6f, inset: Float = 6f) {
    val r = size.minDimension / 2
    for (i in 0 until 60) {
        val a = (i * 6.0 - 90) * PI / 180
        val len = if (i % majorEvery == 0) majorLen else minorLen
        val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
        drawLine(color.copy(alpha = if (i % majorEvery == 0) 1f else 0.5f), center + dir * (r - inset - len), center + dir * (r - inset),
            strokeWidth = if (i % majorEvery == 0) 4f else 1.5f, cap = StrokeCap.Round)
    }
}

internal fun DrawScope.text(t: String, at: Offset, sizePx: Float, color: Color, serif: Boolean = false, bold: Boolean = false) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb(); textSize = sizePx; textAlign = Paint.Align.CENTER
        typeface = Typeface.create(if (serif) Typeface.SERIF else Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
    drawContext.canvas.nativeCanvas.drawText(t, at.x, at.y + sizePx / 3, p)
}

internal fun DrawScope.pin(color: Color) { drawCircle(color, 9f); drawCircle(Color.Black.copy(alpha = 0.4f), 3f) }

/* ---------------- 1. Clásica romana ---------------- */
@Composable
internal fun RomanFace(now: Long, aod: Boolean) {
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
internal fun ChronoFace(now: Long, aod: Boolean, c: Complications) {
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
internal fun MinimalFace(now: Long, aod: Boolean) {
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
internal fun DigitalFace(now: Long, aod: Boolean, c: Complications, running: Boolean) {
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
        Text(java.text.SimpleDateFormat(if (Idioma.codigo == "en") "EEEE, MMMM d" else "EEEE d 'de' MMMM", Idioma.locale).format(cal.time).replaceFirstChar { it.uppercase() },
            color = eng.text, fontFamily = FontFamily.SansSerif, fontSize = 14.sp)
        if (!aod) {
            Spacer(Modifier.height(10.dp))
            ComplicationRow(c)
            c.nextAlarm?.let { Text("⏰ $it", color = eng.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
            Text(if (running) "● gestos activos" else "○ gestos apagados", color = if (running) eng.primary else eng.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/* ---------------- 5. Deportiva ---------------- */
@Composable
internal fun SportFace(now: Long, aod: Boolean) {
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
internal fun ComplicationRow(c: Complications, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Bubble(if (c.charging) "⚡" else "🔋", c.battery?.let { "$it%" } ?: "–")
        Bubble(c.weather.ifBlank { "🌡️" }, c.tempC?.let { "$it°" } ?: "–")
        Bubble("👣", c.steps?.let { if (it >= 1000) "%.1fk".format(it / 1000f) else "$it" } ?: "–")
    }
}

@Composable
internal fun Bubble(icon: String, value: String) {
    val eng = LocalEng.current
    Column(
        Modifier.size(50.dp).clip(CircleShape).background(eng.raised).border(1.dp, eng.goldBrush(), CircleShape),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(icon, fontSize = 13.sp)
        Text(value, color = eng.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
