package com.gestores.app

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
interface PreferenciasReloj {
    var esfera: Int
}

class PreferenciasAndroid(ctx: Context) : PreferenciasReloj {
    private val p = ctx.applicationContext.prefs()
    override var esfera: Int
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
class RelojViewModel(
    private val estado: SavedStateHandle,
    private val prefs: PreferenciasReloj,
    peticionesDeApp: Flow<String?> = WatchState.openRequest,
    cambiosDeEsfera: Flow<Int> = WatchState.nextFace,
    private val peticionAtendida: () -> Unit = { WatchState.openRequest.value = null },
) : ViewModel() {

    companion object {
        const val ESFERA = "face"
        const val MENU = "apps"
        const val GESTORES = "gestos"
        const val TOTAL_ESFERAS = 5
        private const val CLAVE = "pantalla"

        fun factory(ctx: Context) = viewModelFactory {
            initializer { RelojViewModel(createSavedStateHandle(), PreferenciasAndroid(ctx)) }
        }
    }

    /** "face" (esfera), "apps" (menú) o el id de una app del reloj. */
    val pantalla: StateFlow<String> = estado.getStateFlow(CLAVE, ESFERA)

    private val _esfera = MutableStateFlow(prefs.esfera.coerceIn(0, TOTAL_ESFERAS - 1))
    val esfera: StateFlow<Int> = _esfera.asStateFlow()

    val puedeVolver: Boolean get() = pantalla.value != ESFERA

    init {
        // Un gesto puede pedir abrir una app del reloj…
        viewModelScope.launch { peticionesDeApp.filterNotNull().collect { abrir(it); peticionAtendida() } }
        // …o cambiar de esfera (se ignora el valor que ya tenía al abrir la app)
        viewModelScope.launch { cambiosDeEsfera.drop(1).collect { ir(ESFERA); cambiarEsfera(+1) } }
    }

    fun abrir(id: String) = ir(id)

    /** Corona: desde la esfera abre el menú; desde el menú vuelve a la esfera; desde una app vuelve al menú. */
    fun corona() = ir(if (pantalla.value == MENU) ESFERA else MENU)

    /** Botón lateral: abre Gestores directo (o lo cierra si ya está abierto). */
    fun botonLateral() = ir(if (pantalla.value == GESTORES) ESFERA else GESTORES)

    /** Atrás: una app vuelve al menú; el menú vuelve a la esfera. */
    fun atras() = ir(if (pantalla.value == ESFERA || pantalla.value == MENU) ESFERA else MENU)

    /** Deslizar el dedo: en la esfera cambia de esfera; dentro de una app, a la derecha es volver. */
    fun deslizar(distancia: Float) {
        if (pantalla.value == ESFERA) {
            if (distancia < -80) cambiarEsfera(+1) else if (distancia > 80) cambiarEsfera(-1)
        } else if (distancia > 90) atras()
    }

    fun cambiarEsfera(paso: Int) {
        val nueva = Math.floorMod(_esfera.value + paso, TOTAL_ESFERAS)
        _esfera.value = nueva
        prefs.esfera = nueva
    }

    private fun ir(destino: String) { estado[CLAVE] = destino }
}
