package com.gestores.app

import com.gestores.app.watch.*
import com.gestores.app.apps.*
import com.gestores.app.gestures.*
import com.gestores.app.state.*
import com.gestores.app.language.*
import com.gestores.app.errors.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** La IA de gestos propios (DTW + vecino más cercano). */
class LearnerTest {
    @Before fun limpiar() { Learner.resetForTests(); Calibration.resetForTests() }

    private fun teach(id: String, create: (Float, Int) -> List<Sample>, times: Int = 5) =
        repeat(times) { Learner.add(id, create(0.8f, it)) }

    @Test fun `sin gestos aprendidos no reconoce nada`() = assertNull(Learner.match(Motions.circle()))

    @Test fun `aprende un circulo y lo reconoce`() {
        teach("dib_circulo", Motions::circle)
        assertEquals("dib_circulo", Learner.match(Motions.circle(0.8f, 99))?.first)
    }

    @Test fun `distingue el circulo de la Z`() {
        teach("dib_circulo", Motions::circle)
        teach("dib_z", Motions::zeta)
        assertEquals("dib_circulo", Learner.match(Motions.circle(0.8f, 42))?.first)
        assertEquals("dib_z", Learner.match(Motions.zeta(0.8f, 42))?.first)
    }

    @Test fun `un movimiento muy distinto no se confunde con lo aprendido`() {
        teach("dib_circulo", Motions::circle)
        assertNull(Learner.match(Motions.knocks(3)))
    }

    @Test fun `la confianza va de 0 a 1 y es alta para el mismo gesto`() {
        teach("dib_circulo", Motions::circle)
        val (_, conf) = Learner.match(Motions.circle())!!
        assertTrue("confianza $conf", conf in 0.5f..1f)
    }

    @Test fun `cuenta las muestras de cada gesto`() {
        teach("ia_1", Motions::circle, times = 3)
        assertEquals(3, Learner.count("ia_1"))
        assertEquals(0, Learner.count("ia_2"))
    }

    @Test fun `guarda maximo 12 muestras y se queda con las mas recientes`() {
        teach("ia_1", Motions::circle, times = 20)
        assertEquals(12, Learner.count("ia_1"))
    }

    @Test fun `aprende mientras usas solo con buena confianza`() {
        teach("ia_1", Motions::circle, times = 3)
        Learner.learnFromUse("ia_1", Motions.circle(), confidence = 0.5f)
        assertEquals(3, Learner.count("ia_1"))
        Learner.learnFromUse("ia_1", Motions.circle(), confidence = 0.9f)
        assertEquals(4, Learner.count("ia_1"))
    }

    @Test fun `no aprende con el uso un gesto que tiene menos de 3 muestras`() {
        teach("ia_1", Motions::circle, times = 2)
        Learner.learnFromUse("ia_1", Motions.circle(), confidence = 0.95f)
        assertEquals(2, Learner.count("ia_1"))
    }

    @Test fun `borrar un gesto olvida sus muestras`() {
        teach("ia_1", Motions::circle)
        Learner.clear("ia_1")
        assertEquals(0, Learner.count("ia_1"))
    }

    @Test fun `un movimiento vacio o de una sola muestra no rompe la IA`() {
        teach("ia_1", Motions::circle)
        Learner.add("ia_1", emptyList())
        assertEquals(5, Learner.count("ia_1"))
        assertNull(Learner.match(emptyList()))
        assertNull(Learner.match(Motions.circle().take(1)))
    }

    @Test fun `el nombre del gesto se limpia y se corta a 24 letras`() {
        Learner.rename("ia_1", "  Saludo de la mano izquierda larga  ")
        assertEquals("Saludo de la mano izquie", Learner.nameOf("ia_1"))
        Learner.rename("ia_1", "   ")
        assertNull(Learner.nameOf("ia_1"))
    }

    @Test fun `exportar e importar deja todo igual (gestos, nombres y calibracion)`() {
        teach("dib_circulo", Motions::circle)
        Learner.rename("ia_3", "Ola")
        Calibration.calibrate("alzar", List(3) { Motions.raise(force = 8f) })
        val factor = Calibration.factor("alzar")
        val copy = Learner.exportJson()

        Learner.resetForTests(); Calibration.resetForTests()
        assertEquals(1, Learner.importJson(copy))
        assertEquals(5, Learner.count("dib_circulo"))
        assertEquals("Ola", Learner.nameOf("ia_3"))
        assertEquals(factor, Calibration.factor("alzar"), 0f)
        assertNotNull(Learner.match(Motions.circle()))
    }

    @Test(expected = Exception::class)
    fun `importar un archivo que no es copia de Gestores falla`() { Learner.importJson("esto no es json") }
}
