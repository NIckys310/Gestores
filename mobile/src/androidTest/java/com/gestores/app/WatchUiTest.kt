package com.gestores.app

import com.gestores.app.watch.*
import com.gestores.app.apps.*
import com.gestores.app.gestures.*
import com.gestores.app.state.*
import com.gestores.app.language.*
import com.gestores.app.errors.*

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
class WatchUiTest {
    @get:Rule(order = 0) val permissions: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()
    private lateinit var app: ActivityScenario<MainActivity>
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun openNow() {
        // Siempre se empieza igual: español, gestos apagados
        Language.change(ctx, "es")
        WatchState.init(ctx)
        WatchState.setGestores(ctx, false)
        app = ActivityScenario.launch(MainActivity::class.java)
        // El reloj tiene animaciones infinitas (segundero, corona): la prueba avanza el tiempo a mano
        compose.mainClock.autoAdvance = false
        advance()
    }

    @After fun close() {
        Language.change(ctx, "es")
        WatchState.setGestores(ctx, false)
        app.close()
    }

    /** Deja pasar el tiempo de las animaciones (entrar a una app tarda unos 400 ms). */
    private fun advance(ms: Long = 1_200) = compose.mainClock.advanceTimeBy(ms)

    /** Desplaza la lista hasta que aparezca el texto. */
    private fun swipeTo(text: String) {
        repeat(8) {
            if (compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()) return
            // La misma acción que usa un lector de pantalla para desplazar la lista
            compose.onAllNodes(hasScrollAction()).onFirst().performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 500f) }
            advance(800)
        }
    }

    private fun openMenu() {
        compose.onNodeWithContentDescription("Corona: abrir el menú o volver").performClick()
        advance()
        compose.onNodeWithText("Todos los gestos y lo que hacen").assertIsDisplayed()
    }

    @Test fun theFaceShowsHowToOpenTheMenu() {
        compose.onNodeWithText("Toca la pantalla o la corona para abrir el menú").assertIsDisplayed()
        compose.onNodeWithText("○ gestos apagados").assertIsDisplayed()
    }

    @Test fun theCrownOpensTheAppMenu() {
        openMenu()
        compose.onNodeWithText("Alarma").assertIsDisplayed()
        compose.onNodeWithText("Cronómetro").assertIsDisplayed()
    }

    @Test fun sideButtonOpensGestoresAndSwitchTurnsThemOn() {
        compose.onNodeWithContentDescription("Botón lateral: abrir Gestores").performClick()
        advance()
        compose.onNodeWithText("○ Gestores desactivados").performClick()
        advance()
        // El interruptor arranca el servicio de gestos: el estado llega un instante después
        compose.waitUntil(5_000) { advance(200); compose.onAllNodes(hasText("● Gestores activados")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("● Gestores activados").assertIsDisplayed()
        compose.onNodeWithText("Mueve el celular para hacer un gesto").assertIsDisplayed()
    }

    @Test fun switchingToEnglishTranslatesTheWatch() {
        openMenu()
        swipeTo("Ajustes")
        compose.onNodeWithText("Ajustes").performSemanticsAction(SemanticsActions.OnClick)
        advance()
        compose.onNodeWithText("English").performClick()
        advance()
        compose.onNodeWithText("SETTINGS").assertIsDisplayed()
        compose.onNodeWithText("Language").assertIsDisplayed()
        compose.onNodeWithText("Español").performClick()
        advance()
        compose.onNodeWithText("AJUSTES").assertIsDisplayed()
    }

    @Test fun anActionWithSoundAsksFirst() {
        compose.onNodeWithContentDescription("Botón lateral: abrir Gestores").performClick()
        advance()
        swipeTo("Doble giro")
        compose.onNodeWithText("Doble giro").performSemanticsAction(SemanticsActions.OnClick)
        advance()
        compose.onNodeWithText("▶ Probar").performClick()
        advance()
        compose.onNodeWithText("¿Pasar a la siguiente canción?").assertIsDisplayed()
        compose.onNodeWithText("No").performClick()
        advance()
    }
}
