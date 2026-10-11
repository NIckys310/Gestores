package com.gestores.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

/* Paleta "reloj clásico de lujo" (igual que en el celular): oro, oro claro y negro cálido */
private val Teal = Color(0xFFD4AF37)   // oro: acción principal
private val Gold = Color(0xFFF3D27A)   // oro claro: avisos
private val Deep = Color(0xFF17140F)   // negro cálido: tarjetas

class MainActivity : ComponentActivity() {
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Learner.init(this)
        Bus.sensitivity.value = prefs().getFloat("sensitivity", 1f)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MaterialTheme(colors = Colors(primary = Teal, onPrimary = Color(0xFF1A1405), secondary = Gold, surface = Deep, background = Color(0xFF0B0A08), onSurface = Color(0xFFF3EBD8)), typography = androidx.wear.compose.material.Typography(defaultFontFamily = androidx.compose.ui.text.font.FontFamily.Serif)) {
                WatchApp()
            }
        }
    }
}

@Composable
fun WatchApp() {
    val ctx = LocalContext.current
    val running by Bus.running.collectAsState()
    val last by Bus.lastGesture.collectAsState()
    val result by Bus.result.collectAsState()
    val training by Bus.trainingTarget.collectAsState()
    val sensitivity by Bus.sensitivity.collectAsState()
    var phone by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val listState = rememberScalingLazyListState()

    LaunchedEffect(Unit) {
        while (true) {
            phone = runCatching { Wearable.getNodeClient(ctx).connectedNodes.await().firstOrNull()?.displayName }.getOrNull()
            delay(5000)
        }
    }

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        val target = training
        if (target != null) {
            TrainingScreen(target) { Bus.trainingTarget.value = null; refresh++ }
            return@Scaffold
        }
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { Text("Gestores", style = MaterialTheme.typography.title2, color = Teal) }
            item {
                ToggleChip(
                    checked = running,
                    onCheckedChange = { on -> if (on) GestureService.start(ctx) else GestureService.stop(ctx) },
                    label = { Text("Detectar gestos") },
                    toggleControl = { Switch(checked = running) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    text = (last?.let { Gestures.label(it) + "\n" } ?: "") + result.ifBlank { "Actívalo y haz un gesto" },
                    textAlign = TextAlign.Center, color = Color.White,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
            item {
                Text(if (phone != null) "📱 $phone" else "📱 Sin celular · PC directo",
                    style = MaterialTheme.typography.caption2, color = if (phone != null) Teal else Gold)
            }

            item { ListHeader { Text("Probar gestos") } }
            items(Gestures.all) { g ->
                Chip(
                    onClick = { GestureService.simulate(ctx, g.id) },
                    label = { Text("${g.emoji} ${g.name}") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item { ListHeader { Text("Entrenar IA") } }
            items(Gestures.ai) { g ->
                val n = remember(refresh) { Learner.count(g.id) }
                Chip(
                    onClick = {
                        Bus.trainCount.value = Learner.count(g.id)
                        Bus.trainingTarget.value = g.id
                        if (!running) GestureService.start(ctx)
                    },
                    label = { Text("🧠 ${g.name}") },
                    secondaryLabel = { Text(if (n == 0) "Sin entrenar" else "$n muestras") },
                    colors = ChipDefaults.primaryChipColors(backgroundColor = Deep),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item { ListHeader { Text("Ajustes") } }
            item {
                val name = when { sensitivity < 0.9f -> "Baja"; sensitivity > 1.1f -> "Alta"; else -> "Normal" }
                Chip(
                    onClick = { setSensitivity(ctx, if (sensitivity < 0.9f) 1f else if (sensitivity < 1.1f) 1.3f else 0.8f) },
                    label = { Text("Sensibilidad") },
                    secondaryLabel = { Text(name) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TrainingScreen(id: String, onDone: () -> Unit) {
    val count by Bus.trainCount.collectAsState()
    val result by Bus.result.collectAsState()
    Column(
        Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🧠 ${Gestures.get(id)?.name ?: id}", color = Teal, style = MaterialTheme.typography.title3)
        Spacer(Modifier.height(4.dp))
        Text(
            if (count < 5) "Haz tu gesto y quédate quieto.\nMuestra ${count + 1} de 5" else "¡Listo! La IA ya lo aprendió",
            textAlign = TextAlign.Center, style = MaterialTheme.typography.caption1,
        )
        if (result.isNotBlank()) Text(result, color = Gold, style = MaterialTheme.typography.caption2)
        Spacer(Modifier.height(6.dp))
        Chip(onClick = onDone, label = { Text(if (count >= 3) "Terminar" else "Cancelar") },
            colors = ChipDefaults.primaryChipColors(), modifier = Modifier.fillMaxWidth())
        Chip(onClick = { Learner.clear(id); Bus.trainCount.value = 0 }, label = { Text("Borrar muestras") },
            colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.fillMaxWidth())
    }
}

private fun setSensitivity(ctx: Context, v: Float) {
    Bus.sensitivity.value = v
    ctx.prefs().edit().putFloat("sensitivity", v).apply()
}
