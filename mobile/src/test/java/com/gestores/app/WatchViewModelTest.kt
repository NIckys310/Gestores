package com.gestores.app

import com.gestores.app.watch.*
import com.gestores.app.apps.*
import com.gestores.app.gestures.*
import com.gestores.app.state.*
import com.gestores.app.language.*
import com.gestores.app.errors.*

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Navegación dentro del reloj: esfera ⇄ menú ⇄ apps, esferas y lo que piden los gestos. */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchViewModelTest {
    private class InMemoryPrefs(override var face: Int = 0) : WatchPreferences

    private val requests = MutableStateFlow<String?>(null)
    private val faceChanges = MutableStateFlow(0)
    private lateinit var prefs: InMemoryPrefs
    private lateinit var state: SavedStateHandle
    private lateinit var vm: WatchViewModel

    private fun create(savedFace: Int = 0, saved: SavedStateHandle = SavedStateHandle()): WatchViewModel {
        prefs = InMemoryPrefs(savedFace); state = saved
        return WatchViewModel(state, prefs, requests, faceChanges) { requests.value = null }
    }

    @Before fun before() { Dispatchers.setMain(UnconfinedTestDispatcher()); vm = create() }
    @After fun after() = Dispatchers.resetMain()

    private val screen get() = vm.screen.value

    @Test fun `al abrir la app se ve la esfera`() {
        assertEquals(WatchViewModel.FACE, screen)
        assertFalse(vm.canGoBack)
    }

    @Test fun `la corona abre el menu y lo vuelve a cerrar`() {
        vm.crown(); assertEquals(WatchViewModel.MENU, screen)
        vm.crown(); assertEquals(WatchViewModel.FACE, screen)
    }

    @Test fun `desde una app la corona vuelve al menu`() {
        vm.openNow("crono"); vm.crown()
        assertEquals(WatchViewModel.MENU, screen)
    }

    @Test fun `atras lleva de la app al menu y del menu a la esfera`() {
        vm.openNow("calc")
        assertTrue(vm.canGoBack)
        vm.back(); assertEquals(WatchViewModel.MENU, screen)
        vm.back(); assertEquals(WatchViewModel.FACE, screen)
    }

    @Test fun `el boton lateral abre Gestores directo y lo cierra`() {
        vm.sideButton(); assertEquals(WatchViewModel.GESTURES, screen)
        vm.sideButton(); assertEquals(WatchViewModel.FACE, screen)
    }

    @Test fun `deslizar en la esfera cambia de esfera y lo recuerda`() {
        vm.swipe(-120f); assertEquals(1, vm.face.value)
        vm.swipe(120f); assertEquals(0, vm.face.value)
        assertEquals(0, prefs.face)
    }

    @Test fun `un deslizamiento corto no hace nada`() {
        vm.swipe(-40f)
        assertEquals(0, vm.face.value)
    }

    @Test fun `las esferas dan la vuelta (de la ultima a la primera)`() {
        vm.changeFace(-1)
        assertEquals(WatchViewModel.TOTAL_FACES - 1, vm.face.value)
        vm.changeFace(+1)
        assertEquals(0, vm.face.value)
    }

    @Test fun `deslizar a la derecha dentro de una app vuelve`() {
        vm.openNow("notas"); vm.swipe(150f)
        assertEquals(WatchViewModel.MENU, screen)
    }

    @Test fun `deslizar a la izquierda dentro de una app no cambia la esfera`() {
        vm.openNow("notas"); vm.swipe(-150f)
        assertEquals("notas", screen)
        assertEquals(0, vm.face.value)
    }

    @Test fun `recuerda la esfera elegida la vez pasada`() = assertEquals(3, create(savedFace = 3).face.value)

    @Test fun `una esfera guardada invalida no rompe la app`() = assertEquals(WatchViewModel.TOTAL_FACES - 1, create(savedFace = 99).face.value)

    @Test fun `si Android cierra la app, vuelve a la misma pantalla`() {
        val saved = SavedStateHandle()
        val before = create(saved = saved); before.openNow("timer")
        assertEquals("timer", create(saved = saved).screen.value)
    }

    @Test fun `un gesto puede abrir una app del reloj`() {
        requests.value = "linterna"
        assertEquals("linterna", screen)
        assertNull("la petición queda atendida", requests.value)
    }

    @Test fun `un gesto puede cambiar la esfera y la muestra`() {
        vm.openNow("calc")
        faceChanges.value = 1
        assertEquals(WatchViewModel.FACE, screen)
        assertEquals(1, vm.face.value)
    }

    @Test fun `al abrir la app no cambia la esfera por gestos viejos`() {
        faceChanges.value = 7
        val newValue = create()
        assertEquals(0, newValue.face.value)
    }
}
