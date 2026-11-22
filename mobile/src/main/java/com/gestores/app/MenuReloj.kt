package com.gestores.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.sin

/* Menú de apps dentro del reloj, con el atajo grande a Gestores. */

/* ---------------- Menú dentro del reloj ---------------- */
@Composable
internal fun AppMenu(round: Boolean, onOpen: (String) -> Unit) {
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
                Text("Todos los gestos y lo que hacen", color = eng.muted, fontSize = 12.sp)
            }
            // Interruptor general sin entrar a la sección
            Box(
                Modifier.size(width = 44.dp, height = 24.dp).clip(RoundedCornerShape(50)).background(if (on) eng.primary else eng.border)
                    .clickable { WatchState.setGestores(ctx, !on) },
                contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
            ) { Box(Modifier.padding(3.dp).size(18.dp).clip(CircleShape).background(Color.White)) }
        }
        WatchApps.all.filter { it.id != "gestos" }.groupBy { it.group }.forEach { (group, apps) ->
            Text(group.uppercase(), color = eng.primary, fontSize = 12.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold)
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
                            Text(a.name, color = eng.text, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
