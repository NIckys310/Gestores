package com.gestores.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IdiomaTest {
    private val t = Traductor(mapOf("Guardar" to "Save", "AJUSTES" to "SETTINGS", "Ajustes" to "Settings", "Último: {} → {}" to "Last: {} → {}", "Sacudir" to "Shake", "{#} de {#}" to "{#} of {#}"))

    @Test fun `por defecto la app esta en espanol`() {
        assertEquals("es", Idioma.codigo)
        assertEquals("Guardar", tr("Guardar"))
    }

    @Test fun `traduce textos exactos y en mayusculas`() {
        assertEquals("Save", t.traducir("Guardar"))
        assertEquals("SAVE", t.traducir("GUARDAR"))
    }

    @Test fun `traduce los datos conocidos dentro de una frase`() {
        assertEquals("Last: Shake → ✓ Listo", t.traducir("Último: Sacudir → ✓ Listo"))
    }

    @Test fun `el hueco numerico no toca frases de una persona`() {
        assertEquals("3 of 5", t.traducir("3 de 5"))
        assertEquals("Casa de mama", t.traducir("Casa de mama"))
    }

    @Test fun `los textos separados por punto medio se traducen por partes`() {
        assertEquals("Lucas · Save", t.traducir("Lucas · Guardar"))
    }

    @Test fun `el icono se conserva y se traduce el nombre`() {
        assertEquals("⚙️ Save", t.traducir("⚙️ Guardar"))
    }

    @Test fun `lo desconocido se queda igual y conserva espacios`() {
        assertEquals("Mi nota", t.traducir("Mi nota"))
        assertEquals("  Save ", t.traducir("  Guardar "))
    }

    @Test fun `el diccionario tiene los huecos parejos`() {
        val hueco = Regex("\\{#?\\}")
        TRADUCCIONES.forEach { (es, en) ->
            assertEquals(es, hueco.findAll(es).count(), hueco.findAll(en).count())
            assertTrue(es, en.isNotBlank())
        }
    }

    /** Si alguien agrega un Text("…") nuevo y olvida traducirlo, esta prueba avisa. */
    @Test fun `todos los textos fijos de la app estan traducidos`() {
        val llamada = Regex("""\b(?:Text|Title|Small|Pill)\("((?:[^"\\$]|\\.)+)"""")
        val iguales = setOf("Normal", "Digital", "Auto", "No", "ON", "OFF", "Error", "GESTORES", "Gestores", "✗ No")
        val t = Traductor(TRADUCCIONES)
        val faltan = File("src/main/java/com/gestores/app").listFiles()!!.filter { it.extension == "kt" && it.name != "Traducciones.kt" }
            .flatMap { f -> llamada.findAll(f.readText()).map { it.groupValues[1] }.toList() }
            .filter { it.count(Char::isLetter) >= 2 && '%' !in it && it !in iguales && t.traducir(it) == it }
        assertEquals(emptyList<String>(), faltan.distinct())
    }
}
