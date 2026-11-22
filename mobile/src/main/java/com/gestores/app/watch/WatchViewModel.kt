package com.gestores.app.watch

import com.gestores.app.*
import com.gestores.app.apps.*
import com.gestores.app.gestures.*
import com.gestores.app.state.*
import com.gestores.app.language.*
import com.gestores.app.errors.*

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** Dónde se guarda la esfera elegida (en el celular, o en memoria durante las pruebas). */
interface WatchPreferences {
    var face: Int
}

class AndroidPreferences(ctx: Context) : WatchPreferences {
    private val p = ctx.applicationContext.prefs()
    override var face: Int
        get() = p.getInt("face2", 0)
        set(v) = p.edit().putInt("face2", v).apply()
}

/**
 * ESTADO DE LA PANTALLA DEL RELOJ (ViewModel)
 * Qué se ve dentro del reloj (esfera, menú o una app), qué esfera está puesta y
 * qué piden los gestos. Sobrevive a girar el celular y, gracias a SavedStateHandle,
 * también a que Android cierre la app en segundo plano.
 *
 * Navegación:  ESFERA ⇄ MENÚ ⇄ app      (Gestores también se abre con el botón lateral)
 */
class WatchViewModel(
    private val state: SavedStateHandle,
    private val prefs: WatchPreferences,
    appRequests: Flow<String?> = WatchState.openRequest,
    faceChanges: Flow<Int> = WatchState.nextFace,
    private val requestHandled: () -> Unit = { WatchState.openRequest.value = null },
) : ViewModel() {

    companion object {
        const val FACE = "face"
        const val MENU = "apps"
        const val GESTURES = "gestos"
        const val TOTAL_FACES = 5
        private const val KEY = "pantalla"

        fun factory(ctx: Context) = viewModelFactory {
            initializer { WatchViewModel(createSavedStateHandle(), AndroidPreferences(ctx)) }
        }
    }

    /** "face" (esfera), "apps" (menú) o el id de una app del reloj. */
    val screen: StateFlow<String> = state.getStateFlow(KEY, FACE)

    private val _face = MutableStateFlow(prefs.face.coerceIn(0, TOTAL_FACES - 1))
    val face: StateFlow<Int> = _face.asStateFlow()

    val canGoBack: Boolean get() = screen.value != FACE

    init {
        // Un gesto puede pedir abrir una app del reloj…
        viewModelScope.launch { appRequests.filterNotNull().collect { openNow(it); requestHandled() } }
        // …o cambiar de esfera (se ignora el valor que ya tenía al abrir la app)
        viewModelScope.launch { faceChanges.drop(1).collect { go(FACE); changeFace(+1) } }
    }

    fun openNow(id: String) = go(id)

    /** Corona: desde la esfera abre el menú; desde el menú vuelve a la esfera; desde una app vuelve al menú. */
    fun crown() = go(if (screen.value == MENU) FACE else MENU)

    /** Botón lateral: abre Gestores directo (o lo cierra si ya está abierto). */
    fun sideButton() = go(if (screen.value == GESTURES) FACE else GESTURES)

    /** Atrás: una app vuelve al menú; el menú vuelve a la esfera. */
    fun back() = go(if (screen.value == FACE || screen.value == MENU) FACE else MENU)

    /** Deslizar el dedo: en la esfera cambia de esfera; dentro de una app, a la derecha es volver. */
    fun swipe(distance: Float) {
        if (screen.value == FACE) {
            if (distance < -80) changeFace(+1) else if (distance > 80) changeFace(-1)
        } else if (distance > 90) back()
    }

    fun changeFace(step: Int) {
        val newOne = Math.floorMod(_face.value + step, TOTAL_FACES)
        _face.value = newOne
        prefs.face = newOne
    }

    private fun go(target: String) { state[KEY] = target }
}
