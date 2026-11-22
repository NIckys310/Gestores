package com.gestores.app

import com.gestores.app.watch.*
import com.gestores.app.apps.*
import com.gestores.app.gestures.*
import com.gestores.app.state.*
import com.gestores.app.language.*
import com.gestores.app.errors.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** El segmentador corta el flujo del acelerómetro en movimientos completos. */
class MotionSegmenterTest {
    private val g = 9.8f
    private var t = 0L
    private val motions = mutableListOf<List<Sample>>()
    private val seg = MotionSegmenter { motions += it }

    private fun reading(z: Float, x: Float = 0f) { seg.onAccel(x, 0f, z, t * 1_000_000); t += 20 }
    private fun rest(ms: Int) = repeat(ms / 20) { reading(g) }
    private fun push(ms: Int, force: Float = 10f) = repeat(ms / 20) { reading(g + force) }

    @Test fun `en reposo no se detecta ningun movimiento`() {
        rest(3000)
        assertTrue(motions.isEmpty())
    }

    @Test fun `un empujon seguido de calma es un movimiento`() {
        rest(500); push(120); rest(500)
        assertEquals(1, motions.size)
        assertTrue("el movimiento debe tener varias muestras", motions[0].size >= 4)
    }

    @Test fun `el movimiento empieza en tiempo cero y termina antes de la calma`() {
        rest(500); push(160); rest(500)
        val m = motions.single()
        assertEquals(0L, m.first().t)
        assertTrue("la calma del final se recorta", m.last().t <= 200)
    }

    @Test fun `la aceleracion del movimiento no incluye la gravedad`() {
        rest(500); push(120, force = 10f); rest(500)
        val max = motions.single().maxOf { it.mag }
        assertTrue("debería medir ~10, no ~20 (sin gravedad): $max", max in 8f..12f)
    }

    @Test fun `un toque muy corto (ruido) no cuenta`() {
        rest(500); push(20); rest(500)
        assertTrue(motions.isEmpty())
    }

    @Test fun `justo despues de un gesto hay una pausa para no repetirlo`() {
        rest(500); push(120); rest(240)   // termina el primero
        push(120); rest(500)                 // llega dentro de la pausa de 700 ms
        assertEquals(1, motions.size)
    }

    @Test fun `dos gestos con suficiente tiempo entre ellos son dos movimientos`() {
        rest(500); push(120); rest(1000); push(120); rest(500)
        assertEquals(2, motions.size)
    }

    @Test fun `moverse mas de 2,5 segundos seguidos (como caminar) no es un gesto`() {
        rest(500); push(4000); rest(3000)
        assertTrue(motions.isEmpty())
    }

    @Test fun `un gesto de casi 2,5 segundos si cuenta`() {
        rest(500); push(2400); rest(500)
        assertEquals(1, motions.size)
    }
}
