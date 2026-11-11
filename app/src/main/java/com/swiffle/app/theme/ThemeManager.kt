package com.swiffle.app.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Three-tier Display Theme Modes matching Google System Settings.
 */
enum class ThemeMode(val key: String, val displayName: String, val subtitle: String) {
    SYSTEM("system", "System default", "Match device display settings"),
    LIGHT("light", "Light", "Always use light theme"),
    DARK("dark", "Dark", "Always use dark theme");

    companion object {
        fun fromKey(key: String?): ThemeMode {
            return entries.firstOrNull { it.key == key } ?: SYSTEM
        }
    }
}

/**
 * Reactive Theme Manager for Swiffle PDF.
 * Instant, frame-0 theme switching without Activity restarts or UI flashes.
 */
object ThemeManager {
    private const val PREFS_NAME = "OrigamiPrefs"
    private const val KEY_THEME_MODE = "app_theme_mode"

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private var isInitialized = false

    fun init(context: Context) {
        if (!isInitialized) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val saved = prefs.getString(KEY_THEME_MODE, "light")
            _themeMode.value = ThemeMode.fromKey(saved)
            isInitialized = true
        }
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        _themeMode.value = mode
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, mode.key).apply()
    }

    @Composable
    fun isDarkThemeActive(): Boolean {
        val currentMode = _themeMode.collectAsState().value
        return when (currentMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
        }
    }
}
