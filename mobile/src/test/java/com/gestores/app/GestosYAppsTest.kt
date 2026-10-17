package com.gestores.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Lista de gestos, sus acciones por defecto y las apps del reloj. */
class GestosYAppsTest {
    @Before fun limpiar() = Learner.resetForTests()

    @Test fun `hay 7 gestos basicos, 3 dibujos y 8 gestos propios`() {
        assertEquals(7, Gestures.builtIn.size)
        assertEquals(3, Gestures.shapes.size)
        assertEquals(8, Gestures.ai.size)
    }

    @Test fun `los identificadores de los gestos no se repiten`() {
        val ids = Gestures.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun `cada gesto explica como hacerlo`() {
        for (g in Gestures.all) assertTrue(g.id, g.hint.length > 10 && g.name.isNotBlank() && g.emoji.isNotBlank())
    }

    @Test fun `el nombre propio de un gesto IA aparece en la lista`() {
        Learner.rename("ia_2", "Saludo")
        assertEquals("Saludo", Gestures.get("ia_2")?.name)
        assertEquals("🧠 Saludo", Gestures.label("ia_2"))
    }

    @Test fun `un gesto desconocido muestra su identificador`() = assertEquals("xyz", Gestures.label("xyz"))

    @Test fun `cada gesto basico ya trae una accion al instalar la app`() {
        for (g in Gestures.builtIn) assertNotNull(g.id, ConfigStore.defaults.rules[g.id])
    }

    @Test fun `las acciones por defecto son validas`() {
        val tipos = setOf("camera", "flashlight", "face", "chrono", "media", "volume", "vibrate_mode", "app", "open_app", "none")
        for ((g, r) in ConfigStore.defaults.rules) {
            assertTrue("$g: ${r.type}", r.type in tipos)
            if (r.type == "app") assertNotNull("$g abre una app que no existe", WatchApps.get(r.value))
        }
    }

    @Test fun `hay 16 apps en el reloj, en 4 grupos`() {
        assertEquals(16, WatchApps.all.size)
        assertEquals(setOf("Reloj", "Salud", "Utilidades", "Gestos e IA"), WatchApps.all.map { it.group }.toSet())
    }

    @Test fun `las apps del reloj no repiten identificador`() {
        val ids = WatchApps.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun `la seccion Gestores existe dentro del reloj`() = assertEquals("Gestores", WatchApps.get("gestos")?.name)
}
