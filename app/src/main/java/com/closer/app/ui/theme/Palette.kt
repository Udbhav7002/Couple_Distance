package com.closer.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Accent gradients a person picks during onboarding. Stored as a colorIndex
 * so both the Compose UI and the widget bitmap renderer share one source of truth.
 */
data class Accent(val start: Color, val end: Color) {
    val mid: Color get() = Color(
        ((start.red + end.red) / 2 * 255).toInt(),
        ((start.green + end.green) / 2 * 255).toInt(),
        ((start.blue + end.blue) / 2 * 255).toInt(),
    )
}

object Palette {
    val accents = listOf(
        Accent(Color(0xFFFF6B81), Color(0xFFFF9671)), // 0 rose
        Accent(Color(0xFF6C5CE7), Color(0xFF8E7CFF)), // 1 indigo
        Accent(Color(0xFF00B8A9), Color(0xFF3ECFC0)), // 2 teal
        Accent(Color(0xFFFFB300), Color(0xFFFF8A65)), // 3 amber
        Accent(Color(0xFF2E86DE), Color(0xFF54D3F0)), // 4 ocean
        Accent(Color(0xFFB565D8), Color(0xFF7C4DFF)), // 5 violet
    )

    fun accent(index: Int): Accent = accents[index.coerceIn(0, accents.lastIndex)]

    /** Android graphics ints for the widget renderer. */
    fun startArgb(index: Int): Int = toArgb(accent(index).start)
    fun endArgb(index: Int): Int = toArgb(accent(index).end)

    fun toArgb(c: Color): Int = android.graphics.Color.argb(
        (c.alpha * 255).toInt(), (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt(),
    )
}
