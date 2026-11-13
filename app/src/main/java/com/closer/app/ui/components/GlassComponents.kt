package com.closer.app.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Apple-Style Matte Design System & System Tints
 */
object GlassTheme {
    // Apple Human Interface Guidelines System Tints
    val AppleRose = Color(0xFFFF375F)
    val AppleIndigo = Color(0xFF5E5CE6)
    val AppleTeal = Color(0xFF30D158)
    val AppleAmber = Color(0xFFFF9F0A)

    // Palette aliases for backwards compatibility
    val RoseGlow = AppleRose
    val AmberGlow = AppleAmber
    val VioletGlow = AppleIndigo

    // Velvety dark surfaces and subtle micro-borders
    val MatteBackground = Color(0xFF0D0E11)
    val CardBackground = Color(0xFF16181D)
    val CardBorder = Color(0xFF262933)

    val RoseGradient = Brush.horizontalGradient(
        listOf(AppleRose, Color(0xFFFF5277))
    )
    val VioletGradient = Brush.horizontalGradient(
        listOf(AppleIndigo, Color(0xFF7D7AFF))
    )

    val CardBrush = Brush.verticalGradient(
        listOf(
            Color(0xFF16181D),
            Color(0xFF16181D),
        )
    )

    val CardBorderBrush = Brush.verticalGradient(
        listOf(
            Color(0xFF2E323D),
            Color(0xFF21242C),
        )
    )

    val CardBorderRoseBrush = Brush.verticalGradient(
        listOf(
            AppleRose.copy(alpha = 0.45f),
            Color(0xFF262933),
        )
    )
}

/**
 * Velvety dark matte background container.
 * Eliminates pulsating radial blur orbs in favor of pure OLED charcoal matte depth.
 */
@Composable
fun AmbientGlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GlassTheme.MatteBackground)
    ) {
        content()
    }
}

/**
 * Matte container card with subtle 1dp micro-border.
 */
@Composable
fun FrostedCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    borderBrush: Brush = GlassTheme.CardBorderBrush,
    contentPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(shape)
            .background(GlassTheme.CardBackground)
            .border(width = 1.dp, brush = borderBrush, shape = shape)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * Apple-style matte primary action button with tactile micro-haptics.
 */
@Composable
fun GlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    gradient: Brush = GlassTheme.RoseGradient,
) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(
                if (enabled) gradient
                else Brush.linearGradient(listOf(Color(0xFF22252D), Color(0xFF1B1D23)))
            )
            .border(
                width = 1.dp,
                color = if (enabled) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f),
                shape = shape,
            )
            .clickable(enabled = enabled && !loading) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.5.dp,
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
                )
            }
        }
    }
}

/**
 * Apple-style secondary matte button with subtle micro-border and micro-haptics.
 */
@Composable
fun FrostedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF1F222A))
            .border(width = 1.dp, color = Color(0xFF2E323D), shape = shape)
            .clickable(enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 20.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
        }
    }
}

/**
 * 6-Tile Styled Code Display with matte dark tiles and subtle micro-borders.
 */
@Composable
fun StylizedCodeDisplay(
    code: String,
    modifier: Modifier = Modifier,
) {
    val cleanCode = code.trim().uppercase()
    Row(
        modifier = modifier.semantics { contentDescription = "Pairing code: ${cleanCode.chunked(1).joinToString(" ")}" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until 6) {
            val char = cleanCode.getOrNull(i)?.toString() ?: "•"
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1C1E26))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                GlassTheme.AppleRose.copy(alpha = 0.45f),
                                Color(0xFF262933),
                            )
                        ),
                        shape = RoundedCornerShape(14.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = char,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Launches Android Native Share Intent to easily send the code via WhatsApp / SMS / Telegram
 */
fun shareCoupleInvite(context: Context, code: String) {
    val cleanCode = code.trim().uppercase()
    val text = """
        Hey 💕 Join me on Closer so we can see our live distance in real-time!

        1. Download the app: https://github.com/Udbhav7002/Couple_Distance/releases/latest
        2. Open Closer and enter our pairing code: $cleanCode

        Can't wait to see how close we get! ✨
    """.trimIndent()

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share invite with your partner"))
}
