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
   APPS DE SALUD · Pasos, respirar, agua y ejercicio
   ========================================================= */

/* ---------------- SALUD ---------------- */

@Composable
internal fun NeedPermission(perm: String, why: String) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    Small(why)
    Pill("Dar permiso", filled = true) { launcher.launch(perm) }
}

@Composable
internal fun StepsApp() {
    val ctx = LocalContext.current
    val c = rememberComplications()
    var goal by remember { mutableIntStateOf(ctx.prefs().getInt("steps_goal", 8000)) }
    Dial {
        Title("PASOS DE HOY")
        if (!ctx.has(Manifest.permission.ACTIVITY_RECOGNITION)) NeedPermission(Manifest.permission.ACTIVITY_RECOGNITION, "Para contar tus pasos")
        else {
            val s = c.steps ?: 0
            Ring(s / goal.toFloat(), 130) { Big("$s", 30.sp); Small("de $goal") }
            Small("≈ %.1f km · %d kcal".format(s * 0.00075f, (s * 0.04f).toInt()))
            Line {
                Round("−", 32) { goal = (goal - 1000).coerceAtLeast(1000); ctx.prefs().edit().putInt("steps_goal", goal).apply() }
                Small("meta")
                Round("+", 32) { goal += 1000; ctx.prefs().edit().putInt("steps_goal", goal).apply() }
            }
        }
    }
}

@Composable
internal fun BreatheApp() {
    val eng = LocalEng.current
    var on by remember { mutableStateOf(false) }
    var start by remember { mutableLongStateOf(0L) }
    val now = ticker(100)
    val phase = if (on) ((now - start) / 4000) % 2 else 0
    val anim by rememberInfiniteTransition(label = "resp").animateFloat(0.45f, 1f,
        infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "r")
    Dial(scroll = false) {
        Title("RESPIRAR")
        Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(140.dp).graphicsLayer { val k = if (on) anim else 0.6f; scaleX = k; scaleY = k }
                .clip(CircleShape).background(Brush.radialGradient(listOf(eng.goldLight.copy(alpha = 0.7f), eng.primary.copy(alpha = 0.15f)))))
            Text(if (!on) "🫁" else if (phase == 0L) "Inhala" else "Exhala", color = eng.text, fontFamily = FontFamily.SansSerif, fontSize = 18.sp)
        }
        if (on) Small("Ciclo ${(now - start) / 8000 + 1}") else Small("4 s inhalar · 4 s exhalar")
        Pill(if (on) "Terminar" else "Empezar", filled = !on) { on = !on; start = System.currentTimeMillis() }
    }
}

@Composable
internal fun WaterApp() {
    val ctx = LocalContext.current
    val p = ctx.prefs()
    var glasses by remember { mutableIntStateOf(if (p.getString("water_day", "") == today()) p.getInt("water", 0) else 0) }
    var remind by remember { mutableStateOf(p.getBoolean("water_on", false)) }
    fun set(v: Int) { glasses = v.coerceIn(0, 30); p.edit().putString("water_day", today()).putInt("water", glasses).apply() }
    Dial {
        Title("AGUA")
        Ring(glasses / 8f, 120) { Big("$glasses", 34.sp); Small("de 8 vasos") }
        Line { Round("−", 38) { set(glasses - 1) }; Round("💧", 52, filled = true) { set(glasses + 1) } }
        Pill(if (remind) "🔔 Recordarme: sí" else "🔕 Recordarme: no", filled = remind) {
            remind = !remind; Alarms.waterReminder(ctx, remind)
        }
        Small("Cada 2 horas, de 8 a. m. a 9 p. m.")
    }
}

@Composable
internal fun WorkoutApp() {
    val c = rememberComplications()
    val start by WatchState.workoutStart.collectAsState()
    val steps0 by WatchState.workoutSteps0.collectAsState()
    val now = ticker(500)
    Dial {
        Title("EJERCICIO")
        if (start == 0L) {
            Big("🏃", 44.sp)
            Small("Caminar o correr con el celular encima")
            Pill("▶ Empezar", filled = true) {
                WatchState.workoutStart.value = System.currentTimeMillis()
                WatchState.workoutSteps0.value = c.steps ?: -1
            }
        } else {
            val ms = now - start
            val steps = if (steps0 >= 0) ((c.steps ?: steps0) - steps0).coerceAtLeast(0) else 0
            Big(formatoTiempo(ms, centesimas = false), 34.sp)
            Small("$steps pasos · %.2f km".format(steps * 0.00075f))
            Small("≈ ${(steps * 0.04f + ms / 60_000f * 3f).toInt()} kcal")
            Pill("■ Terminar", filled = true) { WatchState.workoutStart.value = 0L }
        }
    }
}
