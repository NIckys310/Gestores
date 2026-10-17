package com.gestores.app

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Movimientos inventados para las pruebas: imitan lo que miden el acelerómetro
 * y el giroscopio cuando haces cada gesto. Cada muestra dura 20 ms.
 */
object Movimientos {
    private const val DT = 20L

    /** Crea un movimiento a partir de una lista de (x, y, z, arriba, giro). */
    fun de(vararg puntos: FloatArray): List<Sample> =
        puntos.mapIndexed { i, p -> Sample(p[0], p[1], p[2], p[3], p[4], i * DT) }

    private fun p(x: Float = 0f, y: Float = 0f, z: Float = 0f, up: Float = z, gyro: Float = 0.5f) = floatArrayOf(x, y, z, up, gyro)

    /** Sube la mano rápido: aceleración hacia arriba y luego frena. */
    fun alzar(fuerza: Float = 12f) = de(p(z = 3f), p(z = fuerza * 0.7f), p(z = fuerza), p(z = fuerza * 0.6f), p(z = 2f), p(z = -3f), p(z = -1f))

    /** Baja la mano rápido: primero hacia abajo. */
    fun bajar(fuerza: Float = 12f) = de(p(z = -3f), p(z = -fuerza * 0.7f), p(z = -fuerza), p(z = -fuerza * 0.6f), p(z = -2f), p(z = 3f), p(z = 1f))

    /** Gira la muñeca: mucho giro y casi nada de movimiento vertical. */
    fun girar(giro: Float = 9f) = de(p(x = 2f, gyro = 3f), p(x = 3f, gyro = giro), p(x = 2f, gyro = giro * 0.8f), p(x = 1f, gyro = 2f), p(gyro = 1f))

    /** Dos giros seguidos con una pausa entre ellos. */
    fun dobleGiro() = de(p(x = 2f, gyro = 9f), p(x = 1f, gyro = 1f), p(gyro = 1f), p(x = 2f, gyro = 9f), p(x = 1f, gyro = 1f))

    /** Sacudir: la mano va y viene de lado varias veces. */
    fun sacudir() = de(*Array(8) { i -> p(x = if (i % 2 == 0) 10f else -10f, up = 0f) })

    /** Golpes secos: picos muy fuertes separados por momentos de calma. */
    fun golpes(n: Int, separacionMuestras: Int = 6) = de(*Array(n * separacionMuestras) { i ->
        if (i % separacionMuestras == 0) p(x = 20f, up = 0f) else p(x = 1f, up = 0f)
    })

    /** Mano quieta (ruido pequeño del sensor). */
    fun quieto() = de(*Array(10) { p(x = 0.3f, y = 0.2f, up = 0f, gyro = 0.1f) })

    /** Dibujar un círculo en el aire (para la IA). */
    fun circulo(ruido: Float = 0f, semilla: Int = 0) = de(*Array(30) { i ->
        val a = 2 * PI * i / 30
        val r = ruido * sin(i * 1.7 + semilla).toFloat()
        p(x = 6f * cos(a).toFloat() + r, y = 6f * sin(a).toFloat() - r, up = 0f)
    })

    /** Dibujar una Z en el aire (para la IA): derecha, diagonal, derecha. */
    fun zeta(ruido: Float = 0f, semilla: Int = 0) = de(*Array(30) { i ->
        val r = ruido * cos(i * 1.3 + semilla).toFloat()
        when {
            i < 10 -> p(x = 7f + r, y = 0f)
            i < 20 -> p(x = -5f, y = -6f + r)
            else -> p(x = 7f - r, y = 0f)
        }
    })
}
