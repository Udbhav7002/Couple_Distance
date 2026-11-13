package com.closer.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Closer's own palette — warm rose accent, consistent across light and dark.
private val Rose = Color(0xFFFF6B81)
private val RoseSoft = Color(0xFFFFB3C6)
private val Indigo = Color(0xFF8E7CFF)

// ---- Dark ----
private val DarkBg = Color(0xFF0E1116)
private val DarkSurface = Color(0xFF161A21)
private val DarkSurfaceHigh = Color(0xFF1E242D)
private val DarkOnBg = Color(0xFFF2F4F7)
private val DarkOnBgDim = Color(0xFF9AA3AE)

private val CloserDark = darkColorScheme(
    primary = Rose,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A1E26),
    onPrimaryContainer = RoseSoft,
    secondary = Indigo,
    onSecondary = Color.White,
    background = DarkBg,
    onBackground = DarkOnBg,
    surface = DarkSurface,
    onSurface = DarkOnBg,
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = DarkOnBgDim,
    outline = Color(0x2EFFFFFF),
    error = Color(0xFFFF6B6B),
)

// ---- Light ----
private val LightBg = Color(0xFFF8F7F4)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceHigh = Color(0xFFF0ECE8)
private val LightOnBg = Color(0xFF1A1C1E)
private val LightOnBgDim = Color(0xFF6B7280)

private val CloserLight = lightColorScheme(
    primary = Rose,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0E6),
    onPrimaryContainer = Color(0xFF5C1024),
    secondary = Color(0xFF6C5CE7),
    onSecondary = Color.White,
    background = LightBg,
    onBackground = LightOnBg,
    surface = LightSurface,
    onSurface = LightOnBg,
    surfaceVariant = LightSurfaceHigh,
    onSurfaceVariant = LightOnBgDim,
    outline = Color(0x1A000000),
    error = Color(0xFFD32F2F),
)

val CloserTypography = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp),
)

@Composable
fun CloserTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) CloserDark else CloserLight,
        typography = CloserTypography,
        content = content,
    )
}
