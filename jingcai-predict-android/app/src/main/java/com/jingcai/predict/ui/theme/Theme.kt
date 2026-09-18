package com.jingcai.predict.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ============ 品牌色 ============ */
val Green = Color(0xFF00C853)
val GreenStrong = Color(0xFF00E676)
val Warn = Color(0xFFFFB020)
val Info = Color(0xFF4DABF7)

/* ============ 深色 ============ */
val DarkBg = Color(0xFF06090D)
val DarkSurface = Color(0xFF0D131A)
val DarkSurface2 = Color(0xFF101823)
val DarkSurface3 = Color(0xFF16202C)
val DarkText = Color(0xFFE9EFF6)
val DarkTextSub = Color(0xFF93A1B4)
val DarkTextDim = Color(0xFF5F6D80)

/* ============ 浅色 ============ */
val LightBg = Color(0xFFF4F6FA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurface2 = Color(0xFFF3F6FA)
val LightSurface3 = Color(0xFFEDF1F7)
val LightText = Color(0xFF131B26)
val LightTextSub = Color(0xFF5D6B7E)
val LightTextDim = Color(0xFF8A97A8)

private val DarkColors = darkColorScheme(
    primary = Green,
    onPrimary = Color(0xFF04140B),
    primaryContainer = Color(0xFF102E1F),
    onPrimaryContainer = GreenStrong,
    secondary = Info,
    onSecondary = Color(0xFF04121F),
    tertiary = Warn,
    background = DarkBg,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurface2,
    onSurfaceVariant = DarkTextSub,
    surfaceContainerLowest = Color(0xFF080C11),
    surfaceContainerLow = Color(0xFF0B1117),
    surfaceContainer = DarkSurface2,
    surfaceContainerHigh = DarkSurface3,
    surfaceContainerHighest = Color(0xFF1C2836),
    surfaceTint = Green,
    inverseSurface = Color(0xFFE9EFF6),
    inverseOnSurface = Color(0xFF11171E),
    inversePrimary = Color(0xFF00753A),
    outline = Color(0xFF2A3542),
    outlineVariant = Color(0xFF1E2833),
    error = Color(0xFFFF5A5F),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00A84F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F5E2),
    onPrimaryContainer = Color(0xFF00692F),
    secondary = Info,
    onSecondary = Color.White,
    tertiary = Warn,
    background = LightBg,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurface2,
    onSurfaceVariant = LightTextSub,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFBFD),
    surfaceContainer = LightSurface2,
    surfaceContainerHigh = LightSurface3,
    surfaceContainerHighest = Color(0xFFE7EDF5),
    surfaceTint = Color(0xFF00A84F),
    inverseSurface = Color(0xFF1B2430),
    inverseOnSurface = Color(0xFFF2F6FA),
    inversePrimary = GreenStrong,
    outline = Color(0xFFC9D3E0),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFE5484D),
)

/** 字体阶梯：字号收敛、字重分明，中文界面更精致 */
private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 13.5.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 11.5.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 11.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
)

/** 形状阶梯：与 Corner 令牌保持一致 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun JingCaiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
