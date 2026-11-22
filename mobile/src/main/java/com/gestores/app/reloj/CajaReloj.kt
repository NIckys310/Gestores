package com.gestores.app.reloj

import com.gestores.app.*
import com.gestores.app.apps.*
import com.gestores.app.gestos.*
import com.gestores.app.estado.*
import com.gestores.app.idioma.*
import com.gestores.app.fallos.*

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/* Caja del reloj: corona, botón lateral y correas. */

/* ---------------- Corona y botón lateral ---------------- */
@Composable
internal fun SideButtons(dial: androidx.compose.ui.unit.Dp, screen: String, onCrown: () -> Unit, onButton: () -> Unit) {
    val eng = LocalEng.current
    val turn = remember { Animatable(0f) }
    LaunchedEffect(screen) { turn.animateTo(turn.value + 90f, tween(400)) }   // la corona gira al cambiar de pantalla
    // Corona digital
    Box(
        Modifier.offset(x = dial / 2 + 3.dp, y = -dial * 0.12f).width(16.dp).height(44.dp).clip(RoundedCornerShape(5.dp))
            .background(Brush.verticalGradient(listOf(eng.goldLight, eng.primary, eng.border)))
            .semantics { contentDescription = tr("Corona: abrir el menú o volver") }
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
            .semantics { contentDescription = tr("Botón lateral: abrir Gestores") }
            .clickable(onClick = onButton),
    )
}

/* ---------------- Correa deportiva de silicona café con agujeros ---------------- */
@Composable
internal fun Band(top: Boolean, dim: Boolean, width: androidx.compose.ui.unit.Dp) {
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
