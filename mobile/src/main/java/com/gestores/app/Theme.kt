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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/* =========================================================
   Estilo "reloj clásico de lujo": negro, oro y acero (noche)
   o marfil, oro y acero (día). Cámbialo aquí.
   ========================================================= */
data class EngPalette(
    val bg: Color, val panel: Color, val border: Color,
    val primary: Color,   // oro: acción principal y manecillas
    val alert: Color,     // rubí: alertas y segundero
    val text: Color, val muted: Color, val isDark: Boolean,
    val steel: Color = Color(0xFFB8BEC6),     // acero cepillado
    val raised: Color = panel,
    val goldLight: Color = Color(0xFFF3D27A),
)

val DarkEng = EngPalette(
    bg = Color(0xFF0B0A08), panel = Color(0xFF17140F), border = Color(0xFF6E5A2C),
    primary = Color(0xFFD4AF37), alert = Color(0xFFC8423B),
    text = Color(0xFFF3EBD8), muted = Color(0xFF9C927E), isDark = true,
    steel = Color(0xFFB8BEC6), raised = Color(0xFF221D15), goldLight = Color(0xFFF3D27A),
)

val LightEng = EngPalette(
    bg = Color(0xFFF7F2E7), panel = Color(0xFFFFFDF7), border = Color(0xFFCBB27A),
    primary = Color(0xFFA8842C), alert = Color(0xFFA93226),
    text = Color(0xFF1F1A12), muted = Color(0xFF6E6553), isDark = false,
    steel = Color(0xFF6E747C), raised = Color(0xFFEFE6D2), goldLight = Color(0xFFD4AF37),
)

val LocalEng = staticCompositionLocalOf { DarkEng }

/** Borde dorado degradado (como el bisel de un reloj). */
fun EngPalette.goldBrush() = Brush.linearGradient(listOf(goldLight, primary, border, primary, goldLight))

/** Tema: "system" (sigue al celular), "dark" o "light". */
object ThemePrefs {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE)
    fun loadMode(ctx: Context) = prefs(ctx).getString("mode", "system") ?: "system"
    fun saveMode(ctx: Context, mode: String) = prefs(ctx).edit().putString("mode", mode).apply()
}

/* Serif elegante para títulos y la hora; letra normal para leer cómodo */
private val Serif = FontFamily.Serif
private val Lux = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Serif), displayMedium = displayMedium.copy(fontFamily = Serif),
        displaySmall = displaySmall.copy(fontFamily = Serif), headlineLarge = headlineLarge.copy(fontFamily = Serif),
        headlineMedium = headlineMedium.copy(fontFamily = Serif), headlineSmall = headlineSmall.copy(fontFamily = Serif),
        titleLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = 0.5.sp),
        titleMedium = titleMedium.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
    )
}

@Composable
fun isDarkMode(mode: String) = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }

@Composable
fun GestoresTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) DarkEng else LightEng
    val scheme = if (dark) darkColorScheme(
        primary = p.primary, onPrimary = Color(0xFF1A1405), secondary = p.steel, error = p.alert,
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
        MaterialTheme(colorScheme = scheme, typography = Lux, content = content)
    }
}
