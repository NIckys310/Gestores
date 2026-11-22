package com.gestores.app

import com.gestores.app.reloj.*
import com.gestores.app.apps.*
import com.gestores.app.gestos.*
import com.gestores.app.estado.*
import com.gestores.app.idioma.*
import com.gestores.app.fallos.*

import android.app.Application

/** Carga el idioma antes que todo: también lo usan las alarmas y el servicio de gestos. */
class AplicacionGestores : Application() {
    override fun onCreate() {
        super.onCreate()
        Fallos.iniciar(this)
        Idioma.cargar(this)
    }
}
