package com.closer.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.closer.app.ui.theme.Palette

/**
 * Apple-style matte spectacles: two crisp matte lenses with initials, a connected bridge line,
 * and a clean solid distance bead pill sitting on the bridge.
 */
@Composable
fun SpectaclesVisual(
    myInitial: String,
    partnerInitial: String,
    myColorIndex: Int,
    partnerColorIndex: Int,
    distanceText: String,
    nearby: Boolean,
    refreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()

    Canvas(modifier) {
        val h = size.height
        val w = size.width
        val lensR = h * 0.26f
        val marginX = w * 0.06f
        val left = Offset(marginX + lensR, h / 2f)
        val right = Offset(w - marginX - lensR, h / 2f)

        // ---- bridge line ----
        val bridgeColor = if (nearby) Color(0xFFFF375F)
            else if (isDark) Color.White.copy(alpha = 0.35f)
            else Color.Black.copy(alpha = 0.25f)
        drawLine(
            color = bridgeColor,
            start = Offset(left.x + lensR * 0.9f, h / 2f),
            end = Offset(right.x - lensR * 0.9f, h / 2f),
            strokeWidth = h * 0.024f,
            cap = StrokeCap.Round,
        )

        // ---- lenses ----
        val pairs = listOf(
            Triple(left, myColorIndex, myInitial),
            Triple(right, partnerColorIndex, partnerInitial),
        )
        for ((center, colorIndex, initial) in pairs) {
            // Crisp matte lens fill
            drawCircle(
                brush = Brush.linearGradient(
                    listOf(
                        Color(Palette.startArgb(colorIndex)),
                        Color(Palette.endArgb(colorIndex)),
                    ),
                    start = Offset(center.x - lensR, center.y - lensR),
                    end = Offset(center.x + lensR, center.y + lensR),
                ),
                radius = lensR,
                center = center,
            )
            // Subtle 1dp micro-border
            drawCircle(
                color = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f),
                radius = lensR,
                center = center,
                style = Stroke(width = 1.2f),
            )
            drawIntoCanvas { canvas ->
                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    textSize = lensR * 0.85f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                }
                val baseline = center.y - (p.ascent() + p.descent()) / 2f
                canvas.nativeCanvas.drawText(initial.take(1), center.x, baseline, p)
            }
        }

        // ---- distance bead on the line ----
        val beadH = h * 0.22f
        drawIntoCanvas { canvas ->
            val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD,
                )
                textSize = beadH * 0.52f
                textAlign = android.graphics.Paint.Align.CENTER
                color = if (nearby) android.graphics.Color.WHITE
                    else if (isDark) 0xFFF2F4F7.toInt()
                    else 0xFF14181C.toInt()
            }
            val text = if (refreshing) "…" else distanceText
            val textW = textPaint.measureText(text)
            val padX = beadH * 0.62f
            val beadW = textW + padX * 2
            val cx = (left.x + lensR + right.x - lensR) / 2f
            val rect = Rect(
                Offset(cx - beadW / 2f, h / 2f - beadH / 2f),
                Size(beadW, beadH),
            )
            val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                if (nearby) {
                    color = 0xFFFF375F.toInt() // Solid Apple Rose
                } else {
                    color = if (isDark) 0xFF1E2129.toInt() else 0xFFE2E4EA.toInt()
                }
            }
            canvas.nativeCanvas.drawRoundRect(
                rect.left, rect.top, rect.right, rect.bottom, beadH / 2f, beadH / 2f, fill,
            )
            // Subtle 1dp micro-border around distance bead pill
            val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 1.5f
                color = if (nearby) 0x33FFFFFF else if (isDark) 0xFF2E323D.toInt() else 0x22000000
            }
            canvas.nativeCanvas.drawRoundRect(
                rect.left, rect.top, rect.right, rect.bottom, beadH / 2f, beadH / 2f, borderPaint,
            )
            val baseline = h / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
            canvas.nativeCanvas.drawText(text, cx, baseline, textPaint)
        }
    }
}
