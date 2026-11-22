package com.gestores.app

import com.gestores.app.watch.*
import com.gestores.app.apps.*
import com.gestores.app.gestures.*
import com.gestores.app.state.*
import com.gestores.app.language.*
import com.gestores.app.errors.*

import android.app.Application

/** Carga el idioma antes que todo: también lo usan las alarmas y el servicio de gestos. */
class GestoresApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ErrorLog.start(this)
        Language.load(this)
    }
}
