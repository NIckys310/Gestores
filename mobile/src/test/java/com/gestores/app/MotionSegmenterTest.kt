package com.gestores.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** El segmentador corta el flujo del acelerómetro en movimientos completos. */
class MotionSegmenterTest {
    private val g = 9.8f
    private var t = 0L
    private val movimientos = mutableListOf<List<Sample>>()
    private val seg = MotionSegmenter { movimientos += it }

    private fun lectura(z: Float, x: Float = 0f) { seg.onAccel(x, 0f, z, t * 1_000_000); t += 20 }
    private fun reposo(ms: Int) = repeat(ms / 20) { lectura(g) }
    private fun empujon(ms: Int, fuerza: Float = 10f) = repeat(ms / 20) { lectura(g + fuerza) }

    @Test fun `en reposo no se detecta ningun movimiento`() {
        reposo(3000)
        assertTrue(movimientos.isEmpty())
    }

    @Test fun `un empujon seguido de calma es un movimiento`() {
        reposo(500); empujon(120); reposo(500)
        assertEquals(1, movimientos.size)
        assertTrue("el movimiento debe tener varias muestras", movimientos[0].size >= 4)
    }

    @Test fun `el movimiento empieza en tiempo cero y termina antes de la calma`() {
        reposo(500); empujon(160); reposo(500)
        val m = movimientos.single()
        assertEquals(0L, m.first().t)
        assertTrue("la calma del final se recorta", m.last().t <= 200)
    }

    @Test fun `la aceleracion del movimiento no incluye la gravedad`() {
        reposo(500); empujon(120, fuerza = 10f); reposo(500)
        val max = movimientos.single().maxOf { it.mag }
        assertTrue("debería medir ~10, no ~20 (sin gravedad): $max", max in 8f..12f)
    }

    @Test fun `un toque muy corto (ruido) no cuenta`() {
        reposo(500); empujon(20); reposo(500)
        assertTrue(movimientos.isEmpty())
    }

    @Test fun `justo despues de un gesto hay una pausa para no repetirlo`() {
        reposo(500); empujon(120); reposo(240)   // termina el primero
        empujon(120); reposo(500)                 // llega dentro de la pausa de 700 ms
        assertEquals(1, movimientos.size)
    }

    @Test fun `dos gestos con suficiente tiempo entre ellos son dos movimientos`() {
        reposo(500); empujon(120); reposo(1000); empujon(120); reposo(500)
        assertEquals(2, movimientos.size)
    }

    @Test fun `moverse mas de 2,5 segundos seguidos (como caminar) no es un gesto`() {
        reposo(500); empujon(4000); reposo(3000)
        assertTrue(movimientos.isEmpty())
    }

    @Test fun `un gesto de casi 2,5 segundos si cuenta`() {
        reposo(500); empujon(2400); reposo(500)
        assertEquals(1, movimientos.size)
    }
}
