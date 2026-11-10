package com.gestores.app

import android.content.Context
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import java.util.Locale

/**
 * IDIOMA (español / inglés)
 * La app se escribe en español. Si se elige inglés, cada texto se traduce justo cuando se
 * va a mostrar, con el diccionario de Traducciones.kt. En español no se hace nada.
 * {} es un dato cualquiera; {#} solo un número.
 */
internal class Traductor(dic: Map<String, String>) {
    private class Patron(val re: Regex, val en: String, val peso: Int)

    private val exactas = dic.filterKeys { !HUECO.containsMatchIn(it) }
    private val mayusculas = exactas.entries.associate { it.key.uppercase() to it.value.uppercase() }
    private val patrones = dic.filterKeys { HUECO.containsMatchIn(it) }.map { (es, en) -> compilar(es, en) }.sortedByDescending { it.peso }
    private val memoria = HashMap<String, String>()

    private fun compilar(es: String, en: String): Patron {
        val partes = Regex("\\{#?\\}|[^{]+|\\{").findAll(es).map { it.value }.toList()
        val fuente = partes.joinToString("") { p ->
            when (p) { "{}" -> "(.+?)"; "{#}" -> "($NUMERO)"; else -> Regex.escape(p) }
        }
        return Patron(Regex("^$fuente$", RegexOption.DOT_MATCHES_ALL), en, partes.filter { !HUECO.matches(it) }.sumOf { it.length })
    }

    @Synchronized
    fun traducir(texto: String): String {
        if (texto.count { it.isLetter() } < 2) return texto
        memoria[texto]?.let { return it }
        val limpio = texto.trim()
        val hecho = buscar(limpio, 0)
        val salida = if (hecho == limpio) texto else texto.takeWhile { it.isWhitespace() } + hecho + texto.takeLastWhile { it.isWhitespace() }
        if (memoria.size > 2000) memoria.clear()
        memoria[texto] = salida
        return salida
    }

    private fun buscar(limpio: String, nivel: Int): String {
        exactas[limpio]?.let { return it }
        mayusculas[limpio]?.let { return it }
        if (nivel > 3 || limpio.length > LARGO_MAXIMO) return limpio
        for (p in patrones) {
            val m = p.re.find(limpio) ?: continue
            var i = 1
            return HUECO.replace(p.en) { buscar(m.groupValues[i++].trim(), nivel + 1) }
        }
        if (" · " in limpio) return limpio.split(" · ").joinToString(" · ") { buscar(it, nivel + 1) }
        // "⚙️ Ajustes": el ícono se deja y se traduce el resto
        val icono = limpio.takeWhile { !it.isLetter() }
        if (icono.isNotEmpty() && icono.length < limpio.length) return icono + buscar(limpio.substring(icono.length), nivel + 1)
        return limpio
    }

    private companion object {
        val HUECO = Regex("\\{#?\\}")
        const val NUMERO = "[\\d.,:]+(?:\\s?[a-zA-Z%]{1,3})?"
        const val LARGO_MAXIMO = 260
    }
}

object Idioma {
    private val actual = mutableStateOf("es")
    private val traductor by lazy { Traductor(TRADUCCIONES) }

    /** "es" o "en". Al leerlo en una pantalla, esta se repinta sola si cambia. */
    val codigo: String get() = actual.value
    val locale: Locale get() = if (codigo == "en") Locale.ENGLISH else Locale("es")

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("gestores_idioma", Context.MODE_PRIVATE)

    fun cargar(ctx: Context) { actual.value = prefs(ctx).getString("idioma", "es") ?: "es" }

    fun cambiar(ctx: Context, nuevo: String) {
        actual.value = nuevo
        prefs(ctx).edit().putString("idioma", nuevo).apply()
    }

    internal fun traducir(texto: String) = if (codigo == "en") traductor.traducir(texto) else texto
}

/** Traduce un texto que no pasa por Text (notificaciones). */
fun tr(texto: String): String = Idioma.traducir(texto)

/** El Text de siempre, pero en el idioma elegido. */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) = androidx.compose.material3.Text(
    tr(text), modifier, color, fontSize, fontStyle, fontWeight, fontFamily, letterSpacing, textDecoration,
    textAlign, lineHeight, overflow, softWrap, maxLines, minLines, onTextLayout, style,
)
