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
import java.io.File

class LanguageTest {
    private val t = Translator(mapOf("Guardar" to "Save", "AJUSTES" to "SETTINGS", "Ajustes" to "Settings", "Último: {} → {}" to "Last: {} → {}", "Sacudir" to "Shake", "{#} de {#}" to "{#} of {#}"))

    @Test fun `por defecto la app esta en espanol`() {
        assertEquals("es", Language.code)
        assertEquals("Guardar", tr("Guardar"))
    }

    @Test fun `traduce textos exactos y en mayusculas`() {
        assertEquals("Save", t.translate("Guardar"))
        assertEquals("SAVE", t.translate("GUARDAR"))
    }

    @Test fun `traduce los datos conocidos dentro de una frase`() {
        assertEquals("Last: Shake → ✓ Listo", t.translate("Último: Sacudir → ✓ Listo"))
    }

    @Test fun `el hueco numerico no toca frases de una persona`() {
        assertEquals("3 of 5", t.translate("3 de 5"))
        assertEquals("Casa de mama", t.translate("Casa de mama"))
    }

    @Test fun `los textos separados por punto medio se traducen por partes`() {
        assertEquals("Lucas · Save", t.translate("Lucas · Guardar"))
    }

    @Test fun `el icono se conserva y se traduce el nombre`() {
        assertEquals("⚙️ Save", t.translate("⚙️ Guardar"))
    }

    @Test fun `lo desconocido se queda igual y conserva espacios`() {
        assertEquals("Mi nota", t.translate("Mi nota"))
        assertEquals("  Save ", t.translate("  Guardar "))
    }

    @Test fun `el diccionario tiene los huecos parejos`() {
        val placeholder = Regex("\\{#?\\}")
        TRANSLATIONS.forEach { (es, en) ->
            assertEquals(es, placeholder.findAll(es).count(), placeholder.findAll(en).count())
            assertTrue(es, en.isNotBlank())
        }
    }

    /** Si alguien agrega un Text("…") nuevo y olvida traducirlo, esta prueba avisa. */
    @Test fun `todos los textos fijos de la app estan traducidos`() {
        val call = Regex("""\b(?:Text|Title|Small|Pill)\("((?:[^"\\$]|\\.)+)"""")
        val equal = setOf("Normal", "Digital", "Auto", "No", "ON", "OFF", "Error", "GESTORES", "Gestores", "✗ No")
        val t = Translator(TRANSLATIONS)
        val missingItems = File("src/main/java/com/gestores/app").walkTopDown().toList().filter { it.extension == "kt" && it.name != "Translations.kt" }
            .flatMap { f -> call.findAll(f.readText()).map { it.groupValues[1] }.toList() }
            .filter { it.count(Char::isLetter) >= 2 && '%' !in it && it !in equal && t.translate(it) == it }
        assertEquals(emptyList<String>(), missingItems.distinct())
    }
}
