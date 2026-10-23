package com.example.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

/**
 * Theme preference store.
 * Uses SharedPreferences for synchronous reads at startup (avoids first-frame flash).
 * DataStore dependency remains enabled in build.gradle for future migration if desired.
 */
class ThemePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("aura_theme_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        ThemeMode.entries.getOrNull(prefs.getInt(KEY_THEME_MODE, 0)) ?: ThemeMode.SYSTEM
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        if (_themeMode.value != mode) {
            _themeMode.value = mode
            prefs.edit().putInt(KEY_THEME_MODE, mode.ordinal).apply()
        }
    }

    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
    }
}
