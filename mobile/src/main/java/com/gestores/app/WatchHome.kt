package com.gestores.app

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/* =========================================================
   EL RELOJ INTELIGENTE (única pantalla de la app)
   El celular simula un smartwatch moderno: el reloj siempre está a la vista
   (cuadrado o redondo, se elige en Ajustes) y todo se abre DENTRO de él.
   • Corona: abre el menú / vuelve.   • Botón de abajo: Gestores directo.
   • Deslizar a la derecha: volver.   • Deslizar en la esfera: cambiar de esfera.
   • Mantener presionada la esfera: siempre encendido.
   ========================================================= */
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
