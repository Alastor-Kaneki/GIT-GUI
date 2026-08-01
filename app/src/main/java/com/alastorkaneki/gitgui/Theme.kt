package com.alastorkaneki.gitgui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AmoledColors = darkColorScheme(
    primary = Color(0xFFFF2D78),
    onPrimary = Color.White,
    secondary = Color(0xFF9B5CFF),
    onSecondary = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF050505),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF111111),
    onSurfaceVariant = Color(0xFFD6D6D6),
    outline = Color(0xFF363636),
    error = Color(0xFFFF5370)
)

@Composable
fun GitGuiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AmoledColors, content = content)
}
