package com.ladb.reborn.core

import android.app.Application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AppSettings(
    val themeMode: Int = 0,          // 0 system, 1 light, 2 dark
    val fontSp: Int = 13,            // terminal font size
    val startupCommand: String = "",
    val keepScreenOn: Boolean = true,
)

class SettingsRepository(app: Application) {

    private val prefs = app.getSharedPreferences("ladb_settings", Application.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings

    private fun load(): AppSettings = AppSettings(
        themeMode = prefs.getInt("theme_mode", 0),
        fontSp = prefs.getInt("font_sp", 13),
        startupCommand = prefs.getString("startup_command", "") ?: "",
        keepScreenOn = prefs.getBoolean("keep_screen_on", true),
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit()
            .putInt("theme_mode", next.themeMode)
            .putInt("font_sp", next.fontSp)
            .putString("startup_command", next.startupCommand)
            .putBoolean("keep_screen_on", next.keepScreenOn)
            .apply()
    }

    /** Last successfully connected port — enables fast reconnect on relaunch. */
    var lastPort: Int?
        get() = prefs.getInt("last_port", -1).takeIf { it != -1 }
        set(value) = prefs.edit().putInt("last_port", value ?: -1).apply()

    var pairedOnce: Boolean
        get() = prefs.getBoolean("paired_once", false)
        set(value) = prefs.edit().putBoolean("paired_once", value).apply()
}
