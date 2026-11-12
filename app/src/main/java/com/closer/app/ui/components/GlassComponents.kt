package com.closer.app.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Reusable Frosted Glass Design System
 */
object GlassTheme {
    val RoseGlow = Color(0xFFFF5B79)
    val AmberGlow = Color(0xFFFF8E7C)
    val VioletGlow = Color(0xFF8E7CFF)

    val RoseGradient = Brush.horizontalGradient(listOf(RoseGlow, AmberGlow))
    val VioletGradient = Brush.horizontalGradient(listOf(Color(0xFF6C5CE7), VioletGlow))

    val CardBrush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.09f),
            Color.White.copy(alpha = 0.03f),
        )
    )

    val CardBorderBrush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.24f),
            Color.White.copy(alpha = 0.06f),
        )
    )

    val CardBorderRoseBrush = Brush.verticalGradient(
        listOf(
            RoseGlow.copy(alpha = 0.5f),
            Color.White.copy(alpha = 0.12f),
        )
    )
}

/**
 * Background container with subtle ambient floating orbs
 */
@Composable
fun AmbientGlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ambient")
    val pulse = infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(tween(3500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseAlpha",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0D14))
    ) {
        // Soft romantic rose ambient orb in top right
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.TopEnd)
                .blur(90.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFF5B79).copy(alpha = pulse.value * 0.28f),
                            Color.Transparent,
                        )
                    ),
                    shape = CircleShape,
                )
        )

        // Soft indigo ambient orb in bottom left
        Box(
            modifier = Modifier
                .size(360.dp)
                .align(Alignment.BottomStart)
                .blur(110.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF6C5CE7).copy(alpha = pulse.value * 0.22f),
                            Color.Transparent,
                        )
                    ),
                    shape = CircleShape,
                )
        )

        content()
    }
}

/**
 * Frosted Glass Card with hairline specular border
 */
@Composable
fun FrostedCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    borderBrush: Brush = GlassTheme.CardBorderBrush,
    contentPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(shape)
            .background(GlassTheme.CardBrush)
            .border(width = 1.2.dp, brush = borderBrush, shape = shape)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * Modern glowing action button
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
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(shape)
            .background(
                if (enabled) gradient
                else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.05f)))
            )
            .border(
                width = 1.dp,
                color = if (enabled) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.05f),
                shape = shape,
            )
            .clickable(enabled = enabled && !loading, onClick = onClick),
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
 * Frosted secondary action button
 */
@Composable
fun FrostedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.18f), shape = shape)
            .clickable(enabled = enabled, onClick = onClick)
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
 * 6-Tile Styled Code Display
 */
@Composable
fun StylizedCodeDisplay(
    code: String,
    modifier: Modifier = Modifier,
) {
    val cleanCode = code.trim().uppercase()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until 6) {
            val char = cleanCode.getOrNull(i)?.toString() ?: "•"
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(
                        width = 1.2.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                GlassTheme.RoseGlow.copy(alpha = 0.6f),
                                Color.White.copy(alpha = 0.15f),
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
