package com.gestores.app

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** Pantalla de inicio: el celular muestra un reloj inteligente estilo HUD. Al tocarlo se despliega la consola. */
@Composable
fun WatchHome(onOpen: () -> Unit) {
    val eng = LocalEng.current
    val running by PhoneBus.running.collectAsState()
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000) } }

    val blink by rememberInfiniteTransition(label = "blink").animateFloat(
        initialValue = 1f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alpha",
    )
    val sec = Calendar.getInstance().apply { time = now }.get(Calendar.SECOND)
    val strap = if (eng.isDark) eng.panel else eng.border
    val mono = FontFamily.Monospace

    Box(
        Modifier.fillMaxSize().background(eng.bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Correa superior
            Box(Modifier.width(150.dp).height(110.dp)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(Brush.verticalGradient(listOf(eng.bg, strap))))

            // Caja del reloj + corona lateral
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.offset(x = 150.dp).width(18.dp).height(44.dp)
                    .clip(RoundedCornerShape(3.dp)).background(eng.alert))
                Box(
                    Modifier.size(290.dp).clip(CircleShape).background(strap)
                        .border(2.dp, eng.primary, CircleShape).padding(12.dp)
                        .clip(CircleShape).background(eng.bg),
                    contentAlignment = Alignment.Center,
                ) {
                    // Marcas de minutos; la del segundo actual en rojo
                    Canvas(Modifier.fillMaxSize()) {
                        val r = size.minDimension / 2
                        for (i in 0 until 60) {
                            val a = Math.toRadians(i * 6.0 - 90)
                            val len = if (i % 5 == 0) 16f else 7f
                            val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
                            drawLine(
                                color = if (i == sec) eng.alert else eng.primary.copy(alpha = if (i % 5 == 0) 1f else 0.45f),
                                start = center + dir * (r - 4f - len), end = center + dir * (r - 4f),
                                strokeWidth = if (i % 5 == 0) 4f else 2f,
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).alpha(blink).clip(CircleShape).background(eng.alert))
                            Spacer(Modifier.width(6.dp))
                            Text("REC", color = eng.alert, fontFamily = mono, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now),
                            color = eng.primary, fontFamily = mono, fontSize = 58.sp, fontWeight = FontWeight.Bold)
                        Text(SimpleDateFormat("EEE dd MMM", Locale("es")).format(now).uppercase(),
                            color = eng.text, fontFamily = mono, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(if (running) "SYS OK · SENSOR ON" else "SYS OK · SENSOR OFF",
                            color = if (running) eng.primary else eng.muted, fontFamily = mono, fontSize = 11.sp)
                        Text("GESTORES", color = eng.muted, fontFamily = mono, fontSize = 11.sp)
                    }
                }
            }

            // Correa inferior
            Box(Modifier.width(150.dp).height(110.dp)
                .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                .background(Brush.verticalGradient(listOf(strap, eng.bg))))

            Spacer(Modifier.height(24.dp))
            Text("> TOCA EL RELOJ PARA ABRIR_", color = eng.muted, fontFamily = mono, fontSize = 13.sp,
                modifier = Modifier.alpha(0.6f + 0.4f * blink))
        }
    }
}
