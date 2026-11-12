package com.closer.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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
 * The signature spectacles: two gradient lenses with initials, a bridge line,
 * and the distance sitting on the line like a bead.
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
    val glow = rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "glowAlpha",
    )

    Canvas(modifier) {
        val h = size.height
        val w = size.width
        val lensR = h * 0.26f
        val marginX = w * 0.06f
        val left = Offset(marginX + lensR, h / 2f)
        val right = Offset(w - marginX - lensR, h / 2f)

        // ---- bridge line ----
        val bridgeColor = if (nearby) Color(0xFFFF7A9C)
            else if (isDark) Color.White.copy(alpha = 0.55f)
            else Color.Black.copy(alpha = 0.25f)
        drawLine(
            color = bridgeColor,
            start = Offset(left.x + lensR * 0.9f, h / 2f),
            end = Offset(right.x - lensR * 0.9f, h / 2f),
            strokeWidth = h * 0.022f,
            cap = StrokeCap.Round,
        )

        // ---- lenses ----
        val pairs = listOf(
            Triple(left, myColorIndex, myInitial),
            Triple(right, partnerColorIndex, partnerInitial),
        )
        for ((center, colorIndex, initial) in pairs) {
            if (nearby) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFFFF7A9C).copy(alpha = glow.value * 0.55f), Color.Transparent),
                        center = center,
                        radius = lensR * 1.7f,
                    ),
                    radius = lensR * 1.7f,
                    center = center,
                )
            }
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
            drawCircle(
                color = if (isDark) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.65f),
                radius = lensR * 0.92f,
                center = center,
                style = Stroke(width = lensR * 0.09f),
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
                    else if (isDark) 0xE614181C.toInt()
                    else 0xE614181C.toInt()
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
                    shader = android.graphics.LinearGradient(
                        rect.left, rect.top, rect.right, rect.bottom,
                        0xFFFF7A9C.toInt(), 0xFFFF9671.toInt(),
                        android.graphics.Shader.TileMode.CLAMP,
                    )
                } else {
                    color = if (isDark) 0xD9FFFFFF.toInt() else 0xD9E8E4DF.toInt()
                }
            }
            canvas.nativeCanvas.drawRoundRect(
                rect.left, rect.top, rect.right, rect.bottom, beadH / 2f, beadH / 2f, fill,
            )
            val baseline = h / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
            canvas.nativeCanvas.drawText(text, cx, baseline, textPaint)
        }
    }
}
