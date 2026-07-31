package com.alastorkaneki.gitgui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alastorkaneki.gitgui.ui.GitGuiApp
import com.alastorkaneki.gitgui.ui.theme.GitGuiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: AppViewModel = viewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(state.settings.immersiveMode) {
                applyImmersive(state.settings.immersiveMode)
            }
            GitGuiTheme {
                GitGuiApp(state = state, viewModel = viewModel)
            }
        }
    }

    private fun applyImmersive(enabled: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, !enabled)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            if (enabled) {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}
