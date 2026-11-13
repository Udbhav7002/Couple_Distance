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

// Apple Human Interface Guidelines System Tints
val AppleRose = Color(0xFFFF375F)
val AppleRoseSoft = Color(0xFFFF8599)
val AppleIndigo = Color(0xFF5E5CE6)
val AppleTeal = Color(0xFF30D158)
val AppleAmber = Color(0xFFFF9F0A)

// ---- Dark (Velvety OLED Charcoal Matte) ----
private val DarkBg = Color(0xFF0D0E11)
private val DarkSurface = Color(0xFF16181D)
private val DarkSurfaceHigh = Color(0xFF1E2129)
private val DarkOnBg = Color(0xFFF2F4F7)
private val DarkOnBgDim = Color(0xFF8E95A2)
private val DarkOutline = Color(0xFF262933)

private val CloserDark = darkColorScheme(
    primary = AppleRose,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A1722),
    onPrimaryContainer = AppleRoseSoft,
    secondary = AppleIndigo,
    onSecondary = Color.White,
    background = DarkBg,
    onBackground = DarkOnBg,
    surface = DarkSurface,
    onSurface = DarkOnBg,
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = DarkOnBgDim,
    outline = DarkOutline,
    error = Color(0xFFFF453A),
)

// ---- Light (Refined Paper Surface) ----
private val LightBg = Color(0xFFF8F7F4)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceHigh = Color(0xFFF0ECE8)
private val LightOnBg = Color(0xFF1C1D20)
private val LightOnBgDim = Color(0xFF6B7280)

private val CloserLight = lightColorScheme(
    primary = AppleRose,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE4EB),
    onPrimaryContainer = Color(0xFF5C1024),
    secondary = AppleIndigo,
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

/**
 * Apple Human Interface Guidelines System Typography Scale
 */
val CloserTypography = Typography(
    // Apple Large Title: 34sp, Bold, -0.8sp tracking
    headlineLarge = TextStyle(
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.8).sp,
        lineHeight = 41.sp,
    ),
    // Apple Title 1: 28sp, Bold, -0.5sp tracking
    headlineMedium = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
        lineHeight = 34.sp,
    ),
    // Apple Title 2: 22sp, SemiBold, -0.3sp tracking
    headlineSmall = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
        lineHeight = 28.sp,
    ),
    // Apple Title 3: 20sp, SemiBold, -0.2sp tracking
    titleLarge = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
        lineHeight = 25.sp,
    ),
    // Apple Headline: 17sp, SemiBold, -0.2sp tracking
    titleMedium = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
        lineHeight = 22.sp,
    ),
    // Apple Subhead: 15sp, Medium
    titleSmall = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 20.sp,
    ),
    // Apple Body: 17sp, Normal, -0.2sp tracking
    bodyLarge = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.2).sp,
        lineHeight = 22.sp,
    ),
    // Apple Callout / Subhead: 15sp, Normal
    bodyMedium = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
    ),
    // Apple Footnote: 13sp, Normal
    bodySmall = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 18.sp,
    ),
    // Apple Caption 1: 12sp, Medium, +0.2sp tracking
    labelLarge = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp,
        lineHeight = 16.sp,
    ),
    // Apple Caption 2: 11sp, Medium, +0.5sp tracking
    labelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp,
        lineHeight = 13.sp,
    ),
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
