package com.jingcai.predict.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/* ============ 品牌色 ============ */
val Green = Color(0xFF00C853)
val GreenStrong = Color(0xFF00E676)
val Warn = Color(0xFFFFB020)
val Info = Color(0xFF4DABF7)
val Danger = Color(0xFFFF4757)

/* ============ 深色 ============ */
val DarkBg = Color(0xFF0A0E13)
val DarkSurface = Color(0xFF11161D)
val DarkSurface2 = Color(0xFF171E28)
val DarkSurface3 = Color(0xFF1E2836)
val DarkText = Color(0xFFE7EDF5)
val DarkTextSub = Color(0xFF93A1B4)
val DarkTextDim = Color(0xFF5F6D80)

/* ============ 浅色 ============ */
val LightBg = Color(0xFFEEF2F7)
val LightSurface = Color(0xFFFFFFFF)
val LightSurface2 = Color(0xFFF4F7FB)
val LightSurface3 = Color(0xFFE9EEF6)
val LightText = Color(0xFF16202E)
val LightTextSub = Color(0xFF5C6B7D)
val LightTextDim = Color(0xFF8A97A8)

private val DarkColors = darkColorScheme(
    primary = Green,
    onPrimary = Color(0xFF04140B),
    primaryContainer = Color(0xFF0E3D24),
    onPrimaryContainer = GreenStrong,
    background = DarkBg,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurface2,
    onSurfaceVariant = DarkTextSub,
    surfaceContainer = DarkSurface2,
    surfaceContainerHigh = DarkSurface3,
    outline = DarkTextDim,
    outlineVariant = DarkTextDim,
    error = Danger,
    secondary = Info,
    tertiary = Warn,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00A84F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F5E2),
    onPrimaryContainer = Color(0xFF00692F),
    background = LightBg,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurface2,
    onSurfaceVariant = LightTextSub,
    surfaceContainer = LightSurface2,
    surfaceContainerHigh = LightSurface3,
    outline = LightTextDim,
    outlineVariant = LightTextDim,
    error = Color(0xFFEF4444),
    secondary = Info,
    tertiary = Warn,
)

@Composable
fun JingCaiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
