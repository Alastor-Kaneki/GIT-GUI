package com.alastorkaneki.gitgui.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("git_gui_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val rainbowEnabled = booleanPreferencesKey("rainbow_enabled")
        val reverseRainbow = booleanPreferencesKey("reverse_rainbow")
        val rainbowSpeedMs = intPreferencesKey("rainbow_speed_ms")
        val immersiveMode = booleanPreferencesKey("immersive_mode")
        val hapticsEnabled = booleanPreferencesKey("haptics_enabled")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { preferences ->
        AppSettings(
            rainbowEnabled = preferences[Keys.rainbowEnabled] ?: true,
            reverseRainbow = preferences[Keys.reverseRainbow] ?: false,
            rainbowSpeedMs = preferences[Keys.rainbowSpeedMs] ?: 4500,
            immersiveMode = preferences[Keys.immersiveMode] ?: true,
            hapticsEnabled = preferences[Keys.hapticsEnabled] ?: true
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { preferences ->
            val current = AppSettings(
                rainbowEnabled = preferences[Keys.rainbowEnabled] ?: true,
                reverseRainbow = preferences[Keys.reverseRainbow] ?: false,
                rainbowSpeedMs = preferences[Keys.rainbowSpeedMs] ?: 4500,
                immersiveMode = preferences[Keys.immersiveMode] ?: true,
                hapticsEnabled = preferences[Keys.hapticsEnabled] ?: true
            )
            val updated = transform(current)
            preferences[Keys.rainbowEnabled] = updated.rainbowEnabled
            preferences[Keys.reverseRainbow] = updated.reverseRainbow
            preferences[Keys.rainbowSpeedMs] = updated.rainbowSpeedMs
            preferences[Keys.immersiveMode] = updated.immersiveMode
            preferences[Keys.hapticsEnabled] = updated.hapticsEnabled
        }
    }
}
