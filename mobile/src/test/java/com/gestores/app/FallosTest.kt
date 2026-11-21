package com.gestores.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FallosTest {
    @Test fun `sin iniciar no se cae`() {
        Fallos.registrar("prueba", IllegalStateException("x"))
    }

    @Test fun `guarda donde paso y el mensaje, el mas reciente primero`() {
        val f = File.createTempFile("fallos", ".log").apply { delete() }
        Fallos.iniciarEn(f)
        Fallos.registrar("primero", IllegalStateException("uno"))
        Fallos.registrar("segundo", IllegalArgumentException("dos"))
        val r = Fallos.recientes()
        assertTrue(r[0].contains("segundo") && r[0].contains("IllegalArgumentException: dos"))
        assertTrue(r[1].contains("primero"))
    }

    @Test fun `solo guarda los ultimos 60`() {
        val f = File.createTempFile("fallos", ".log").apply { delete() }
        Fallos.iniciarEn(f)
        repeat(75) { Fallos.registrar("n$it", RuntimeException("e")) }
        val r = Fallos.recientes()
        assertEquals(60, r.size)
        assertTrue(r.first().contains("n74"))
    }
}
