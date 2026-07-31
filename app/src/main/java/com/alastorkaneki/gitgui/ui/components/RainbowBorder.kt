package com.alastorkaneki.gitgui.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.animatedRainbowBorder(
    enabled: Boolean,
    reverse: Boolean,
    durationMs: Int,
    width: Dp = 1.5.dp,
    cornerRadius: Dp = 18.dp
): Modifier = composed {
    if (!enabled) return@composed this
    val transition = rememberInfiniteTransition(label = "rainbow-border")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs.coerceAtLeast(500), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rainbow-phase"
    ).value
    val colors = listOf(
        Color(0xFFFF1744),
        Color(0xFFFF9100),
        Color(0xFFFFFF00),
        Color(0xFF00E676),
        Color(0xFF00E5FF),
        Color(0xFF2979FF),
        Color(0xFFD500F9),
        Color(0xFFFF1744)
    )
    drawWithContent {
        drawContent()
        val period = (size.width + size.height).coerceAtLeast(1f)
        val direction = if (reverse) 1f else -1f
        val shift = phase * period * direction
        val brush = Brush.linearGradient(
            colors = colors,
            start = Offset(shift, 0f),
            end = Offset(shift + period, size.height),
            tileMode = TileMode.Repeated
        )
        val stroke = width.toPx()
        drawRoundRect(
            brush = brush,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = size.copy(width = size.width - stroke, height = size.height - stroke),
            cornerRadius = CornerRadius(cornerRadius.toPx()),
            style = Stroke(stroke)
        )
    }
}

@Composable
fun rainbowModifier(
    settings: com.alastorkaneki.gitgui.data.AppSettings,
    cornerRadius: Dp = 18.dp
): Modifier = Modifier.animatedRainbowBorder(
    enabled = settings.rainbowEnabled,
    reverse = settings.reverseRainbow,
    durationMs = settings.rainbowSpeedMs,
    cornerRadius = cornerRadius
)
