package com.gestores.app

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/* =========================================================
   Estilo "tecnología": negro y café oscuro con acento cobre (noche)
   o blanco cálido y café (día). Cámbialo aquí.
   ========================================================= */
data class EngPalette(
    val bg: Color, val panel: Color, val border: Color,
    val primary: Color,   // acento cobre: acción principal
    val alert: Color,     // rojo: alertas y segundero
    val text: Color, val muted: Color, val isDark: Boolean,
    val steel: Color = Color(0xFFB8BEC6),
    val raised: Color = panel,
    val goldLight: Color = Color(0xFFF0C08A),
)

val DarkEng = EngPalette(
    bg = Color(0xFF0A0908), panel = Color(0xFF17110D), border = Color(0xFF4A3626),
    primary = Color(0xFFD9A066), alert = Color(0xFFE5533D),
    text = Color(0xFFF2ECE6), muted = Color(0xFFA08C7A), isDark = true,
    steel = Color(0xFF8E8A86), raised = Color(0xFF241A13), goldLight = Color(0xFFF0C08A),
)

val LightEng = EngPalette(
    bg = Color(0xFFF4F1EE), panel = Color(0xFFFFFFFF), border = Color(0xFFCDBBAA),
    primary = Color(0xFF8A5A33), alert = Color(0xFFC0392B),
    text = Color(0xFF1A1410), muted = Color(0xFF6F6359), isDark = false,
    steel = Color(0xFF5E5A56), raised = Color(0xFFEAE3DC), goldLight = Color(0xFFB57A45),
)

val LocalEng = staticCompositionLocalOf { DarkEng }

/** Borde con degradado cobre (como el bisel de un reloj). */
fun EngPalette.goldBrush() = Brush.linearGradient(listOf(goldLight, primary, border, primary, goldLight))

/** Tema: "system" (sigue al celular), "dark" o "light". */
object ThemePrefs {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE)
    fun loadMode(ctx: Context) = prefs(ctx).getString("mode", "system") ?: "system"
    fun saveMode(ctx: Context, mode: String) = prefs(ctx).edit().putString("mode", mode).apply()
}

/* Letra moderna sin serifa, títulos en seminegrita con espaciado */
private val Tech = Typography().run {
    copy(
        titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = 0.5.sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
    )
}

@Composable
fun isDarkMode(mode: String) = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }

@Composable
fun GestoresTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) DarkEng else LightEng
    val scheme = if (dark) darkColorScheme(
        primary = p.primary, onPrimary = Color(0xFF1A120B), secondary = p.steel, error = p.alert,
        background = p.bg, surface = p.panel, onSurface = p.text, onBackground = p.text,
        surfaceVariant = p.raised, onSurfaceVariant = p.muted, outline = p.border,
        surfaceContainerHigh = p.raised, surfaceContainer = p.panel,
    ) else lightColorScheme(
        primary = p.primary, onPrimary = Color.White, secondary = p.steel, error = p.alert,
        background = p.bg, surface = p.panel, onSurface = p.text, onBackground = p.text,
        surfaceVariant = p.raised, onSurfaceVariant = p.muted, outline = p.border,
        surfaceContainerHigh = p.raised, surfaceContainer = p.panel,
    )
    CompositionLocalProvider(LocalEng provides p) {
        MaterialTheme(colorScheme = scheme, typography = Tech, content = content)
    }
}
