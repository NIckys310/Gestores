package com.gestores.app

import com.gestores.app.watch.*
import com.gestores.app.apps.*
import com.gestores.app.gestures.*
import com.gestores.app.state.*
import com.gestores.app.language.*
import com.gestores.app.errors.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** La calibración guiada ajusta cada gesto a la fuerza de la persona. */
class CalibrationTest {
    @Before fun limpiar() = Calibration.resetForTests()

    @Test fun `sin calibrar el factor es 1 (normal)`() = assertEquals(1f, Calibration.factor("alzar"), 0f)

    @Test fun `se pueden calibrar los 7 gestos basicos`() =
        assertEquals(setOf("alzar", "bajar", "girar", "doble_giro", "sacudir", "doble_golpe", "toques"), Calibration.calibratable.toSet())

    @Test fun `la fuerza de alzar se mide hacia arriba`() =
        assertEquals(12f, Calibration.metric("alzar", Motions.raise(12f)), 0.01f)

    @Test fun `la fuerza de girar se mide con el giroscopio`() =
        assertEquals(9f, Calibration.metric("girar", Motions.turn(9f)), 0.01f)

    @Test fun `quien hace el gesto suave queda con un umbral mas bajo`() {
        val f = Calibration.calibrate("alzar", List(3) { Motions.raise(force = 8f) })
        assertTrue("factor $f", f < 1f)
        assertEquals(f, Calibration.factor("alzar"), 0f)
    }

    @Test fun `quien hace el gesto fuerte queda con un umbral mas alto`() {
        val f = Calibration.calibrate("alzar", List(3) { Motions.raise(force = 20f) })
        assertTrue("factor $f", f > 1f)
    }

    @Test fun `el factor nunca se sale de 0,5 a 1,8`() {
        assertEquals(0.5f, Calibration.calibrate("alzar", List(3) { Motions.raise(force = 1f) }), 0f)
        assertEquals(1.8f, Calibration.calibrate("bajar", List(3) { Motions.lower(force = 90f) }), 0f)
    }

    @Test fun `calibrar sin muestras no cambia nada (ni deja valores invalidos)`() {
        assertEquals(1f, Calibration.calibrate("girar", emptyList()), 0f)
        assertEquals(1f, Calibration.calibrate("girar", listOf(emptyList())), 0f)
        assertEquals(1f, Calibration.factor("girar"), 0f)
    }

    @Test fun `un gesto que no se calibra devuelve 1`() =
        assertEquals(1f, Calibration.calibrate("dib_circulo", listOf(Motions.circle())), 0f)

    @Test fun `restablecer vuelve el gesto a normal`() {
        Calibration.calibrate("alzar", List(3) { Motions.raise(force = 8f) })
        Calibration.reset("alzar")
        assertEquals(1f, Calibration.factor("alzar"), 0f)
    }

    @Test fun `la calibracion cambia lo que reconoce el clasificador`() {
        assertEquals(null, BasicClassifier.classify(Motions.raise(force = 7f), 1f))
        Calibration.calibrate("alzar", List(3) { Motions.raise(force = 7f) })
        assertEquals("alzar", BasicClassifier.classify(Motions.raise(force = 7f), 1f))
    }
}
