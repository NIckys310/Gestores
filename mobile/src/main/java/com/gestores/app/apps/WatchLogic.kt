package com.gestores.app.apps

import com.gestores.app.*
import com.gestores.app.watch.*
import com.gestores.app.gestures.*
import com.gestores.app.state.*
import com.gestores.app.language.*
import com.gestores.app.errors.*

import java.util.Locale

/* =========================================================
   Lógica de las apps del reloj sin pantalla
   (así se puede probar con pruebas automáticas, sin celular)
   ========================================================= */

/** Tiempo para el cronómetro, el temporizador y el ejercicio: 01:05.32, 01:05 o 1:00:05. */
fun formatTime(ms: Long, hundredths: Boolean = true): String {
    val t = ms.coerceAtLeast(0)
    val h = t / 3_600_000; val m = t / 60_000 % 60; val s = t / 1000 % 60; val c = t / 10 % 100
    return when {
        h > 0 -> "%d:%02d:%02d".format(Locale.US, h, m, s)
        hundredths -> "%02d:%02d.%02d".format(Locale.US, m, s, c)
        else -> "%02d:%02d".format(Locale.US, m, s)
    }
}

/**
 * Calculadora de la app del reloj: funciona como una calculadora de bolsillo
 * (cada operación se resuelve al pulsar la siguiente o "=").
 * Teclas: 0-9 . + − × ÷ = % C ⌫
 */
class Calculator {
    var screen = "0"
        private set
    private var accumulated: Double? = null
    private var operation: String? = null
    private var newOne = true          // la próxima cifra empieza un número nuevo

    fun press(keyLabel: String) {
        val current = screen.toDoubleOrNull() ?: 0.0
        when (keyLabel) {
            "C" -> { screen = "0"; accumulated = null; operation = null; newOne = true }
            "⌫" -> screen = screen.dropLast(1).ifBlank { "0" }.let { if (it == "-") "0" else it }
            "%" -> screen = show(current / 100)
            "+", "−", "×", "÷" -> {
                val a = accumulated?.let { if (newOne) it else operate(it, current, operation) } ?: current
                accumulated = a; screen = show(a); operation = keyLabel; newOne = true
            }
            "=" -> { accumulated?.let { screen = show(operate(it, current, operation)) }; accumulated = null; operation = null; newOne = true }
            "." -> { if (newOne) { screen = "0."; newOne = false } else if ('.' !in screen) screen += "." }
            else -> if (keyLabel.length == 1 && keyLabel[0].isDigit()) {
                screen = if (newOne || screen == "0") keyLabel else (screen + keyLabel).take(12)
                newOne = false
            }
        }
    }

    /** Pulsa varias teclas seguidas, por ejemplo "12+3=". */
    fun write(keys: String) = keys.forEach { press(it.toString()) }

    private fun operate(a: Double, b: Double, o: String?) = when (o) { "+" -> a + b; "−" -> a - b; "×" -> a * b; "÷" -> a / b; else -> b }

    private fun show(v: Double) = when {
        v.isNaN() || v.isInfinite() -> "Error"
        v == v.toLong().toDouble() -> v.toLong().toString()
        else -> "%.6f".format(Locale.US, v).trimEnd('0').trimEnd('.')
    }
}
