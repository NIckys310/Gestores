package com.gestores.app.errors

import com.gestores.app.*
import com.gestores.app.watch.*
import com.gestores.app.apps.*
import com.gestores.app.gestures.*
import com.gestores.app.state.*
import com.gestores.app.language.*

import android.content.Context
import android.util.Log
import java.io.File
import java.time.Instant

/**
 * REGISTRO DE FALLOS
 * Ningún error se oculta: se escribe en Logcat y en un archivo del celular (los últimos 60),
 * incluidos los cierres inesperados. Sin servicios externos: nada sale del celular.
 */
object ErrorLog {
    private const val MAX = 60
    private var file: File? = null

    /** Se llama una vez al abrir la app. */
    fun start(ctx: Context) {
        startIn(File(ctx.filesDir, "fallos.log"))
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            record("cierre inesperado", e)
            previous?.uncaughtException(thread, e)
        }
    }

    internal fun startIn(f: File) { file = f }

    /** Anota un error que la app pudo manejar (no se cerró), con dónde pasó. */
    fun record(where: String, e: Throwable) {
        runCatching { Log.w("Fallos", where, e) }
        val f = file ?: return
        synchronized(this) {
            try {
                val line = "${Instant.now()} · $where · ${e::class.java.simpleName}: ${e.message.orEmpty().replace('\n', ' ').take(200)}"
                val previousOnes = if (f.exists()) f.readLines() else emptyList()
                f.writeText((previousOnes + line).takeLast(MAX).joinToString("\n", postfix = "\n"))
            } catch (_: Exception) {
                // Si ni siquiera se puede escribir el registro, queda solo en Logcat
            }
        }
    }

    /** Los fallos guardados, del más reciente al más antiguo. */
    fun recent(): List<String> = file?.takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() }?.reversed() ?: emptyList()
}
