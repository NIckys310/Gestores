package com.gestores.app.fallos

import com.gestores.app.*
import com.gestores.app.reloj.*
import com.gestores.app.apps.*
import com.gestores.app.gestos.*
import com.gestores.app.estado.*
import com.gestores.app.idioma.*

import android.content.Context
import android.util.Log
import java.io.File
import java.time.Instant

/**
 * REGISTRO DE FALLOS
 * Ningún error se oculta: se escribe en Logcat y en un archivo del celular (los últimos 60),
 * incluidos los cierres inesperados. Sin servicios externos: nada sale del celular.
 */
object Fallos {
    private const val MAX = 60
    private var archivo: File? = null

    /** Se llama una vez al abrir la app. */
    fun iniciar(ctx: Context) {
        iniciarEn(File(ctx.filesDir, "fallos.log"))
        val anterior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { hilo, e ->
            registrar("cierre inesperado", e)
            anterior?.uncaughtException(hilo, e)
        }
    }

    internal fun iniciarEn(f: File) { archivo = f }

    /** Anota un error que la app pudo manejar (no se cerró), con dónde pasó. */
    fun registrar(donde: String, e: Throwable) {
        runCatching { Log.w("Fallos", donde, e) }
        val f = archivo ?: return
        synchronized(this) {
            try {
                val linea = "${Instant.now()} · $donde · ${e::class.java.simpleName}: ${e.message.orEmpty().replace('\n', ' ').take(200)}"
                val previas = if (f.exists()) f.readLines() else emptyList()
                f.writeText((previas + linea).takeLast(MAX).joinToString("\n", postfix = "\n"))
            } catch (_: Exception) {
                // Si ni siquiera se puede escribir el registro, queda solo en Logcat
            }
        }
    }

    /** Los fallos guardados, del más reciente al más antiguo. */
    fun recientes(): List<String> = archivo?.takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() }?.reversed() ?: emptyList()
}
