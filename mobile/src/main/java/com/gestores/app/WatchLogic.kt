package com.gestores.app

import java.util.Locale

/* =========================================================
   Lógica de las apps del reloj sin pantalla
   (así se puede probar con pruebas automáticas, sin celular)
   ========================================================= */

/** Tiempo para el cronómetro, el temporizador y el ejercicio: 01:05.32, 01:05 o 1:00:05. */
fun formatoTiempo(ms: Long, centesimas: Boolean = true): String {
    val t = ms.coerceAtLeast(0)
    val h = t / 3_600_000; val m = t / 60_000 % 60; val s = t / 1000 % 60; val c = t / 10 % 100
    return when {
        h > 0 -> "%d:%02d:%02d".format(Locale.US, h, m, s)
        centesimas -> "%02d:%02d.%02d".format(Locale.US, m, s, c)
        else -> "%02d:%02d".format(Locale.US, m, s)
    }
}

/**
 * Calculadora de la app del reloj: funciona como una calculadora de bolsillo
 * (cada operación se resuelve al pulsar la siguiente o "=").
 * Teclas: 0-9 . + − × ÷ = % C ⌫
 */
class Calculadora {
    var pantalla = "0"
        private set
    private var acumulado: Double? = null
    private var operacion: String? = null
    private var nueva = true          // la próxima cifra empieza un número nuevo

    fun pulsar(tecla: String) {
        val actual = pantalla.toDoubleOrNull() ?: 0.0
        when (tecla) {
            "C" -> { pantalla = "0"; acumulado = null; operacion = null; nueva = true }
            "⌫" -> pantalla = pantalla.dropLast(1).ifBlank { "0" }.let { if (it == "-") "0" else it }
            "%" -> pantalla = mostrar(actual / 100)
            "+", "−", "×", "÷" -> {
                val a = acumulado?.let { if (nueva) it else operar(it, actual, operacion) } ?: actual
                acumulado = a; pantalla = mostrar(a); operacion = tecla; nueva = true
            }
            "=" -> { acumulado?.let { pantalla = mostrar(operar(it, actual, operacion)) }; acumulado = null; operacion = null; nueva = true }
            "." -> { if (nueva) { pantalla = "0."; nueva = false } else if ('.' !in pantalla) pantalla += "." }
            else -> if (tecla.length == 1 && tecla[0].isDigit()) {
                pantalla = if (nueva || pantalla == "0") tecla else (pantalla + tecla).take(12)
                nueva = false
            }
        }
    }

    /** Pulsa varias teclas seguidas, por ejemplo "12+3=". */
    fun escribir(teclas: String) = teclas.forEach { pulsar(it.toString()) }

    private fun operar(a: Double, b: Double, o: String?) = when (o) { "+" -> a + b; "−" -> a - b; "×" -> a * b; "÷" -> a / b; else -> b }

    private fun mostrar(v: Double) = when {
        v.isNaN() || v.isInfinite() -> "Error"
        v == v.toLong().toDouble() -> v.toLong().toString()
        else -> "%.6f".format(Locale.US, v).trimEnd('0').trimEnd('.')
    }
}
