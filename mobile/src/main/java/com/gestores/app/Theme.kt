package com.gestores.app

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/* Paleta "modo ingeniero": cámbiala aquí */
data class EngPalette(
    val bg: Color, val panel: Color, val border: Color,
    val primary: Color,   // verde terminal
    val alert: Color,     // rojo
    val text: Color, val muted: Color, val isDark: Boolean,
)

val DarkEng = EngPalette(
    bg = Color(0xFF000000), panel = Color(0xFF0A0F0A), border = Color(0xFF1F3B1F),
    primary = Color(0xFF00FF66), alert = Color(0xFFFF3B3B),
    text = Color(0xFFD8FFD8), muted = Color(0xFF5F8F6A), isDark = true,
)

val LightEng = EngPalette(
    bg = Color(0xFFFFFFFF), panel = Color(0xFFF2F5F2), border = Color(0xFFC5D6C5),
    primary = Color(0xFF00A040), alert = Color(0xFFD32F2F),
    text = Color(0xFF0A0F0A), muted = Color(0xFF5A6B5E), isDark = false,
)

val LocalEng = staticCompositionLocalOf { DarkEng }

/** Guarda si el usuario eligió tema oscuro (negro) o claro (blanco). */
object ThemePrefs {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE)
    fun load(ctx: Context) = prefs(ctx).getBoolean("dark", true)
    fun save(ctx: Context, dark: Boolean) = prefs(ctx).edit().putBoolean("dark", dark).apply()
}

private val Mono: Typography = Typography().run {
    val m = FontFamily.Monospace
    copy(
        displayLarge = displayLarge.copy(fontFamily = m), displayMedium = displayMedium.copy(fontFamily = m),
        displaySmall = displaySmall.copy(fontFamily = m), headlineLarge = headlineLarge.copy(fontFamily = m),
        headlineMedium = headlineMedium.copy(fontFamily = m), headlineSmall = headlineSmall.copy(fontFamily = m),
        titleLarge = titleLarge.copy(fontFamily = m), titleMedium = titleMedium.copy(fontFamily = m),
        titleSmall = titleSmall.copy(fontFamily = m), bodyLarge = bodyLarge.copy(fontFamily = m),
        bodyMedium = bodyMedium.copy(fontFamily = m), bodySmall = bodySmall.copy(fontFamily = m),
        labelLarge = labelLarge.copy(fontFamily = m), labelMedium = labelMedium.copy(fontFamily = m),
        labelSmall = labelSmall.copy(fontFamily = m),
    )
}

@Composable
fun GestoresTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) DarkEng else LightEng
    val scheme = if (dark) darkColorScheme(
        primary = p.primary, onPrimary = Color.Black, secondary = p.alert, error = p.alert,
        background = p.bg, surface = p.panel, onSurface = p.text, onBackground = p.text,
        surfaceVariant = p.panel, onSurfaceVariant = p.muted, outline = p.border,
        surfaceContainerHigh = p.panel,
    ) else lightColorScheme(
        primary = p.primary, onPrimary = Color.White, secondary = p.alert, error = p.alert,
        background = p.bg, surface = p.panel, onSurface = p.text, onBackground = p.text,
        surfaceVariant = p.panel, onSurfaceVariant = p.muted, outline = p.border,
        surfaceContainerHigh = p.panel,
    )
    CompositionLocalProvider(LocalEng provides p) {
        MaterialTheme(colorScheme = scheme, typography = Mono, content = content)
    }
}
