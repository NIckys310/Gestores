package com.gestores.app

import com.gestores.app.watch.*
import com.gestores.app.apps.*
import com.gestores.app.gestures.*
import com.gestores.app.state.*
import com.gestores.app.language.*
import com.gestores.app.errors.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** El clasificador reconoce los gestos básicos con reglas físicas. */
class BasicClassifierTest {
    @Before fun limpiar() = Calibration.resetForTests()

    private fun classifyMotion(m: List<Sample>, sensitivity: Float = 1f) = BasicClassifier.classify(m, sensitivity)

    @Test fun `alzar la mano rapido`() = assertEquals("alzar", classifyMotion(Motions.raise()))
    @Test fun `bajar la mano rapido`() = assertEquals("bajar", classifyMotion(Motions.lower()))
    @Test fun `girar la muneca`() = assertEquals("girar", classifyMotion(Motions.turn()))
    @Test fun `doble giro de muneca`() = assertEquals("doble_giro", classifyMotion(Motions.doubleTurn()))
    @Test fun `sacudir la mano`() = assertEquals("sacudir", classifyMotion(Motions.shake()))
    @Test fun `dos golpes secos`() = assertEquals("doble_golpe", classifyMotion(Motions.knocks(2)))
    @Test fun `tres toques en la mesa`() = assertEquals("toques", classifyMotion(Motions.knocks(3)))

    @Test fun `la mano quieta no es ningun gesto`() = assertNull(classifyMotion(Motions.still()))

    @Test fun `un movimiento vacio no rompe la app`() = assertNull(classifyMotion(emptyList()))

    @Test fun `un alzar muy suave no cuenta con sensibilidad normal`() =
        assertNull(classifyMotion(Motions.raise(force = 7f)))

    @Test fun `con sensibilidad alta el alzar suave si cuenta`() =
        assertEquals("alzar", classifyMotion(Motions.raise(force = 7f), sensitivity = 1.3f))

    @Test fun `golpes demasiado separados no son doble golpe`() =
        assertNull(classifyMotion(Motions.knocks(2, sampleSpacing = 50)))
}
