package com.gestores.app

import com.gestores.app.watch.*
import com.gestores.app.apps.*
import com.gestores.app.gestures.*
import com.gestores.app.state.*
import com.gestores.app.language.*
import com.gestores.app.errors.*

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** Accesibilidad: contraste de colores (WCAG AA 4,5:1) y letras de al menos 12 sp. */
class AccessibilityTest {
    private fun contrast(a: Color, b: Color): Double {
        val x = a.luminance().toDouble(); val y = b.luminance().toDouble()
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }

    private fun check(name: String, p: EngPalette, overMain: Color) {
        listOf(
            "texto/fondo" to contrast(p.text, p.bg),
            "texto/panel" to contrast(p.text, p.panel),
            "secundario/fondo" to contrast(p.muted, p.bg),
            "secundario/panel" to contrast(p.muted, p.panel),
            "cobre/fondo" to contrast(p.primary, p.bg),
            "texto sobre botón" to contrast(overMain, p.primary),
        ).forEach { (par, v) -> assertTrue("$name $par: %.2f".format(v), v >= 4.5) }
    }

    @Test fun `modo oscuro cumple contraste`() = check("oscuro", DarkEng, Color(0xFF1A120B))
    @Test fun `modo claro cumple contraste`() = check("claro", LightEng, Color(0xFFFFF8F0))

    @Test fun `ningun texto mide menos de 12 sp`() {
        val small = File("src/main/java/com/gestores/app").walkTopDown().toList().filter { it.extension == "kt" }
            .flatMap { f -> Regex("""fontSize = (\d+(?:\.\d+)?)\.sp""").findAll(f.readText()).map { f.name to it.groupValues[1].toDouble() }.toList() }
            .filter { it.second < 12 }
        assertEquals(emptyList<Pair<String, Double>>(), small)
    }
}
