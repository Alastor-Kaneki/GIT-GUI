package com.alastorkaneki.gitgui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private val RainbowColors = listOf(
    Color(0xFFFF1744),
    Color(0xFFFF9100),
    Color(0xFFFFFF00),
    Color(0xFF00E676),
    Color(0xFF00B0FF),
    Color(0xFF7C4DFF),
    Color(0xFFE040FB),
    Color(0xFFFF1744)
)

fun Modifier.rainbowBorder(
    enabled: Boolean,
    speed: Float,
    reverse: Boolean,
    cornerRadius: Dp = 18.dp,
    width: Dp = 1.5.dp
): Modifier = composed {
    if (!enabled) return@composed this
    val transition = rememberInfiniteTransition(label = "rainbow")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (reverse) -360f else 360f,
        animationSpec = infiniteRepeatable(tween((9000 / speed.coerceIn(0.25f, 3f)).toInt(), easing = LinearEasing)),
        label = "angle"
    )
    drawWithContent {
        drawContent()
        val radians = Math.toRadians(angle.toDouble())
        val radius = hypot(size.width.toDouble(), size.height.toDouble()).toFloat()
        val center = Offset(size.width / 2f, size.height / 2f)
        val direction = Offset(cos(radians).toFloat() * radius, sin(radians).toFloat() * radius)
        drawRoundRect(
            brush = Brush.linearGradient(RainbowColors, center - direction, center + direction),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius.toPx()),
            style = Stroke(width.toPx())
        )
    }
}
