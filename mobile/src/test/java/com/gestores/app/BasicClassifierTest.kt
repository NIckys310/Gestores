package com.gestores.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** El clasificador reconoce los gestos básicos con reglas físicas. */
class BasicClassifierTest {
    @Before fun limpiar() = Calibration.resetForTests()

    private fun clasificar(m: List<Sample>, sensibilidad: Float = 1f) = BasicClassifier.classify(m, sensibilidad)

    @Test fun `alzar la mano rapido`() = assertEquals("alzar", clasificar(Movimientos.alzar()))
    @Test fun `bajar la mano rapido`() = assertEquals("bajar", clasificar(Movimientos.bajar()))
    @Test fun `girar la muneca`() = assertEquals("girar", clasificar(Movimientos.girar()))
    @Test fun `doble giro de muneca`() = assertEquals("doble_giro", clasificar(Movimientos.dobleGiro()))
    @Test fun `sacudir la mano`() = assertEquals("sacudir", clasificar(Movimientos.sacudir()))
    @Test fun `dos golpes secos`() = assertEquals("doble_golpe", clasificar(Movimientos.golpes(2)))
    @Test fun `tres toques en la mesa`() = assertEquals("toques", clasificar(Movimientos.golpes(3)))

    @Test fun `la mano quieta no es ningun gesto`() = assertNull(clasificar(Movimientos.quieto()))

    @Test fun `un movimiento vacio no rompe la app`() = assertNull(clasificar(emptyList()))

    @Test fun `un alzar muy suave no cuenta con sensibilidad normal`() =
        assertNull(clasificar(Movimientos.alzar(fuerza = 7f)))

    @Test fun `con sensibilidad alta el alzar suave si cuenta`() =
        assertEquals("alzar", clasificar(Movimientos.alzar(fuerza = 7f), sensibilidad = 1.3f))

    @Test fun `golpes demasiado separados no son doble golpe`() =
        assertNull(clasificar(Movimientos.golpes(2, separacionMuestras = 50)))
}
