package com.gestores.app.language

import com.gestores.app.*
import com.gestores.app.watch.*
import com.gestores.app.apps.*
import com.gestores.app.gestures.*
import com.gestores.app.state.*
import com.gestores.app.errors.*

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
 * va a mostrar, con el diccionario de Translations.kt. En español no se hace nada.
 * {} es un dato cualquiera; {#} solo un número.
 */
internal class Translator(dic: Map<String, String>) {
    private class TextPattern(val re: Regex, val en: String, val weight: Int)

    private val exactMatches = dic.filterKeys { !PLACEHOLDER.containsMatchIn(it) }
    private val uppercase = exactMatches.entries.associate { it.key.uppercase() to it.value.uppercase() }
    private val patterns = dic.filterKeys { PLACEHOLDER.containsMatchIn(it) }.map { (es, en) -> compile(es, en) }.sortedByDescending { it.weight }
    private val memory = HashMap<String, String>()

    private fun compile(es: String, en: String): TextPattern {
        val parts = Regex("\\{#?\\}|[^{]+|\\{").findAll(es).map { it.value }.toList()
        val source = parts.joinToString("") { p ->
            when (p) { "{}" -> "(.+?)"; "{#}" -> "($NUMBER)"; else -> Regex.escape(p) }
        }
        return TextPattern(Regex("^$source$", RegexOption.DOT_MATCHES_ALL), en, parts.filter { !PLACEHOLDER.matches(it) }.sumOf { it.length })
    }

    @Synchronized
    fun translate(text: String): String {
        if (text.count { it.isLetter() } < 2) return text
        memory[text]?.let { return it }
        val clean = text.trim()
        val done = search(clean, 0)
        val output = if (done == clean) text else text.takeWhile { it.isWhitespace() } + done + text.takeLastWhile { it.isWhitespace() }
        if (memory.size > 2000) memory.clear()
        memory[text] = output
        return output
    }

    private fun search(clean: String, depth: Int): String {
        exactMatches[clean]?.let { return it }
        uppercase[clean]?.let { return it }
        if (depth > 3 || clean.length > MAX_LENGTH) return clean
        for (p in patterns) {
            val m = p.re.find(clean) ?: continue
            var i = 1
            return PLACEHOLDER.replace(p.en) { search(m.groupValues[i++].trim(), depth + 1) }
        }
        if (" · " in clean) return clean.split(" · ").joinToString(" · ") { search(it, depth + 1) }
        // "⚙️ Ajustes": el ícono se deja y se traduce el resto
        val icon = clean.takeWhile { !it.isLetter() }
        if (icon.isNotEmpty() && icon.length < clean.length) return icon + search(clean.substring(icon.length), depth + 1)
        return clean
    }

    private companion object {
        val PLACEHOLDER = Regex("\\{#?\\}")
        const val NUMBER = "[\\d.,:]+(?:\\s?[a-zA-Z%]{1,3})?"
        const val MAX_LENGTH = 260
    }
}

object Language {
    private val current = mutableStateOf("es")
    private val translator by lazy { Translator(TRANSLATIONS) }

    /** "es" o "en". Al leerlo en una pantalla, esta se repinta sola si cambia. */
    val code: String get() = current.value
    val locale: Locale get() = if (code == "en") Locale.ENGLISH else Locale("es")

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("gestores_idioma", Context.MODE_PRIVATE)

    fun load(ctx: Context) { current.value = prefs(ctx).getString("idioma", "es") ?: "es" }

    fun change(ctx: Context, newValue: String) {
        current.value = newValue
        prefs(ctx).edit().putString("idioma", newValue).apply()
    }

    internal fun translate(text: String) = if (code == "en") translator.translate(text) else text
}

/** Traduce un texto que no pasa por Text (notificaciones). */
fun tr(text: String): String = Language.translate(text)

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
