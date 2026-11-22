package com.gestores.app

import com.gestores.app.reloj.*
import com.gestores.app.apps.*
import com.gestores.app.gestos.*
import com.gestores.app.estado.*
import com.gestores.app.idioma.*
import com.gestores.app.fallos.*

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** Accesibilidad: contraste de colores (WCAG AA 4,5:1) y letras de al menos 12 sp. */
class AccesibilidadTest {
    private fun contraste(a: Color, b: Color): Double {
        val x = a.luminance().toDouble(); val y = b.luminance().toDouble()
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }

    private fun revisar(nombre: String, p: EngPalette, sobrePrincipal: Color) {
        listOf(
            "texto/fondo" to contraste(p.text, p.bg),
            "texto/panel" to contraste(p.text, p.panel),
            "secundario/fondo" to contraste(p.muted, p.bg),
            "secundario/panel" to contraste(p.muted, p.panel),
            "cobre/fondo" to contraste(p.primary, p.bg),
            "texto sobre botón" to contraste(sobrePrincipal, p.primary),
        ).forEach { (par, v) -> assertTrue("$nombre $par: %.2f".format(v), v >= 4.5) }
    }

    @Test fun `modo oscuro cumple contraste`() = revisar("oscuro", DarkEng, Color(0xFF1A120B))
    @Test fun `modo claro cumple contraste`() = revisar("claro", LightEng, Color(0xFFFFF8F0))

    @Test fun `ningun texto mide menos de 12 sp`() {
        val pequenos = File("src/main/java/com/gestores/app").walkTopDown().toList().filter { it.extension == "kt" }
            .flatMap { f -> Regex("""fontSize = (\d+(?:\.\d+)?)\.sp""").findAll(f.readText()).map { f.name to it.groupValues[1].toDouble() }.toList() }
            .filter { it.second < 12 }
        assertEquals(emptyList<Pair<String, Double>>(), pequenos)
    }
}
