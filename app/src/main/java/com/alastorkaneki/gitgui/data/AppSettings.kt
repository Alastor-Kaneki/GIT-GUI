package com.alastorkaneki.gitgui.data

data class AppSettings(
    val rainbowEnabled: Boolean = true,
    val reverseRainbow: Boolean = false,
    val rainbowSpeedMs: Int = 4500,
    val immersiveMode: Boolean = true,
    val hapticsEnabled: Boolean = true
)
