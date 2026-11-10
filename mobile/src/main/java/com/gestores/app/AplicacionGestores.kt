package com.gestores.app

import android.app.Application

/** Carga el idioma antes que todo: también lo usan las alarmas y el servicio de gestos. */
class AplicacionGestores : Application() {
    override fun onCreate() {
        super.onCreate()
        Idioma.cargar(this)
    }
}
