package com.alastorkaneki.gitgui.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AmoledScheme = darkColorScheme(
    primary = Color(0xFFF05033),
    onPrimary = Color.Black,
    secondary = Color(0xFFBB86FC),
    onSecondary = Color.Black,
    tertiary = Color(0xFF00E5FF),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF050505),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF111111),
    onSurfaceVariant = Color(0xFFCCCCCC),
    outline = Color(0xFF3A3A3A),
    error = Color(0xFFFF5252),
    onError = Color.Black
)

@Composable
fun GitGuiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AmoledScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
