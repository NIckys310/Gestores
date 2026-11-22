package com.gestores.app

import com.gestores.app.reloj.*
import com.gestores.app.apps.*
import com.gestores.app.gestos.*
import com.gestores.app.estado.*
import com.gestores.app.idioma.*
import com.gestores.app.fallos.*

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.hasText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Pruebas de interfaz: el reloj se usa con toques reales en el emulador o el celular. */
@RunWith(AndroidJUnit4::class)
class RelojInterfazTest {
    @get:Rule(order = 0) val permisos: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()
    private lateinit var app: ActivityScenario<MainActivity>
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun abrir() {
        // Siempre se empieza igual: español, gestos apagados
        Idioma.cambiar(ctx, "es")
        WatchState.init(ctx)
        WatchState.setGestores(ctx, false)
        app = ActivityScenario.launch(MainActivity::class.java)
        // El reloj tiene animaciones infinitas (segundero, corona): la prueba avanza el tiempo a mano
        compose.mainClock.autoAdvance = false
        avanzar()
    }

    @After fun cerrar() {
        Idioma.cambiar(ctx, "es")
        WatchState.setGestores(ctx, false)
        app.close()
    }

    /** Deja pasar el tiempo de las animaciones (entrar a una app tarda unos 400 ms). */
    private fun avanzar(ms: Long = 1_200) = compose.mainClock.advanceTimeBy(ms)

    /** Desplaza la lista hasta que aparezca el texto. */
    private fun deslizarHasta(texto: String) {
        repeat(8) {
            if (compose.onAllNodes(hasText(texto)).fetchSemanticsNodes().isNotEmpty()) return
            // La misma acción que usa un lector de pantalla para desplazar la lista
            compose.onAllNodes(hasScrollAction()).onFirst().performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 500f) }
            avanzar(800)
        }
    }

    private fun abrirMenu() {
        compose.onNodeWithContentDescription("Corona: abrir el menú o volver").performClick()
        avanzar()
        compose.onNodeWithText("Todos los gestos y lo que hacen").assertIsDisplayed()
    }

    @Test fun laEsferaMuestraComoAbrirElMenu() {
        compose.onNodeWithText("Toca la pantalla o la corona para abrir el menú").assertIsDisplayed()
        compose.onNodeWithText("○ gestos apagados").assertIsDisplayed()
    }

    @Test fun laCoronaAbreElMenuConLasApps() {
        abrirMenu()
        compose.onNodeWithText("Alarma").assertIsDisplayed()
        compose.onNodeWithText("Cronómetro").assertIsDisplayed()
    }

    @Test fun elBotonLateralAbreGestoresYElInterruptorLosEnciende() {
        compose.onNodeWithContentDescription("Botón lateral: abrir Gestores").performClick()
        avanzar()
        compose.onNodeWithText("○ Gestores desactivados").performClick()
        avanzar()
        // El interruptor arranca el servicio de gestos: el estado llega un instante después
        compose.waitUntil(5_000) { avanzar(200); compose.onAllNodes(hasText("● Gestores activados")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("● Gestores activados").assertIsDisplayed()
        compose.onNodeWithText("Mueve el celular para hacer un gesto").assertIsDisplayed()
    }

    @Test fun cambiarAInglesTraduceElReloj() {
        abrirMenu()
        deslizarHasta("Ajustes")
        compose.onNodeWithText("Ajustes").performSemanticsAction(SemanticsActions.OnClick)
        avanzar()
        compose.onNodeWithText("English").performClick()
        avanzar()
        compose.onNodeWithText("SETTINGS").assertIsDisplayed()
        compose.onNodeWithText("Language").assertIsDisplayed()
        compose.onNodeWithText("Español").performClick()
        avanzar()
        compose.onNodeWithText("AJUSTES").assertIsDisplayed()
    }

    @Test fun unaAccionConSonidoPreguntaAntesDeHacerse() {
        compose.onNodeWithContentDescription("Botón lateral: abrir Gestores").performClick()
        avanzar()
        deslizarHasta("Doble giro")
        compose.onNodeWithText("Doble giro").performSemanticsAction(SemanticsActions.OnClick)
        avanzar()
        compose.onNodeWithText("▶ Probar").performClick()
        avanzar()
        compose.onNodeWithText("¿Pasar a la siguiente canción?").assertIsDisplayed()
        compose.onNodeWithText("No").performClick()
        avanzar()
    }
}
