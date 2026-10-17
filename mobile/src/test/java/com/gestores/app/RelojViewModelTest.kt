package com.gestores.app

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
class RelojViewModelTest {
    private class PrefsEnMemoria(override var esfera: Int = 0) : PreferenciasReloj

    private val peticiones = MutableStateFlow<String?>(null)
    private val cambiosDeEsfera = MutableStateFlow(0)
    private lateinit var prefs: PrefsEnMemoria
    private lateinit var estado: SavedStateHandle
    private lateinit var vm: RelojViewModel

    private fun crear(esferaGuardada: Int = 0, guardado: SavedStateHandle = SavedStateHandle()): RelojViewModel {
        prefs = PrefsEnMemoria(esferaGuardada); estado = guardado
        return RelojViewModel(estado, prefs, peticiones, cambiosDeEsfera) { peticiones.value = null }
    }

    @Before fun antes() { Dispatchers.setMain(UnconfinedTestDispatcher()); vm = crear() }
    @After fun despues() = Dispatchers.resetMain()

    private val pantalla get() = vm.pantalla.value

    @Test fun `al abrir la app se ve la esfera`() {
        assertEquals(RelojViewModel.ESFERA, pantalla)
        assertFalse(vm.puedeVolver)
    }

    @Test fun `la corona abre el menu y lo vuelve a cerrar`() {
        vm.corona(); assertEquals(RelojViewModel.MENU, pantalla)
        vm.corona(); assertEquals(RelojViewModel.ESFERA, pantalla)
    }

    @Test fun `desde una app la corona vuelve al menu`() {
        vm.abrir("crono"); vm.corona()
        assertEquals(RelojViewModel.MENU, pantalla)
    }

    @Test fun `atras lleva de la app al menu y del menu a la esfera`() {
        vm.abrir("calc")
        assertTrue(vm.puedeVolver)
        vm.atras(); assertEquals(RelojViewModel.MENU, pantalla)
        vm.atras(); assertEquals(RelojViewModel.ESFERA, pantalla)
    }

    @Test fun `el boton lateral abre Gestores directo y lo cierra`() {
        vm.botonLateral(); assertEquals(RelojViewModel.GESTORES, pantalla)
        vm.botonLateral(); assertEquals(RelojViewModel.ESFERA, pantalla)
    }

    @Test fun `deslizar en la esfera cambia de esfera y lo recuerda`() {
        vm.deslizar(-120f); assertEquals(1, vm.esfera.value)
        vm.deslizar(120f); assertEquals(0, vm.esfera.value)
        assertEquals(0, prefs.esfera)
    }

    @Test fun `un deslizamiento corto no hace nada`() {
        vm.deslizar(-40f)
        assertEquals(0, vm.esfera.value)
    }

    @Test fun `las esferas dan la vuelta (de la ultima a la primera)`() {
        vm.cambiarEsfera(-1)
        assertEquals(RelojViewModel.TOTAL_ESFERAS - 1, vm.esfera.value)
        vm.cambiarEsfera(+1)
        assertEquals(0, vm.esfera.value)
    }

    @Test fun `deslizar a la derecha dentro de una app vuelve`() {
        vm.abrir("notas"); vm.deslizar(150f)
        assertEquals(RelojViewModel.MENU, pantalla)
    }

    @Test fun `deslizar a la izquierda dentro de una app no cambia la esfera`() {
        vm.abrir("notas"); vm.deslizar(-150f)
        assertEquals("notas", pantalla)
        assertEquals(0, vm.esfera.value)
    }

    @Test fun `recuerda la esfera elegida la vez pasada`() = assertEquals(3, crear(esferaGuardada = 3).esfera.value)

    @Test fun `una esfera guardada invalida no rompe la app`() = assertEquals(RelojViewModel.TOTAL_ESFERAS - 1, crear(esferaGuardada = 99).esfera.value)

    @Test fun `si Android cierra la app, vuelve a la misma pantalla`() {
        val guardado = SavedStateHandle()
        val antes = crear(guardado = guardado); antes.abrir("timer")
        assertEquals("timer", crear(guardado = guardado).pantalla.value)
    }

    @Test fun `un gesto puede abrir una app del reloj`() {
        peticiones.value = "linterna"
        assertEquals("linterna", pantalla)
        assertNull("la petición queda atendida", peticiones.value)
    }

    @Test fun `un gesto puede cambiar la esfera y la muestra`() {
        vm.abrir("calc")
        cambiosDeEsfera.value = 1
        assertEquals(RelojViewModel.ESFERA, pantalla)
        assertEquals(1, vm.esfera.value)
    }

    @Test fun `al abrir la app no cambia la esfera por gestos viejos`() {
        cambiosDeEsfera.value = 7
        val nuevo = crear()
        assertEquals(0, nuevo.esfera.value)
    }
}
