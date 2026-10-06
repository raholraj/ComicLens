package com.comiclens.app.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class Settings(
    val onboarded: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.System,
    val accentIndex: Int = 0,
    val previewMode: PreviewMode = PreviewMode.Normal
)

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private val prefs = ctx.getSharedPreferences("comiclens_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private fun load(): Settings = Settings(
        onboarded = prefs.getBoolean("onboarded", false),
        themeMode = ThemeMode.entries.getOrElse(prefs.getInt("themeMode", 0)) { ThemeMode.System },
        accentIndex = prefs.getInt("accentIndex", 0),
        previewMode = PreviewMode.entries.getOrElse(prefs.getInt("previewMode", 0)) { PreviewMode.Normal }
    )

    private fun save(s: Settings) {
        prefs.edit()
            .putBoolean("onboarded", s.onboarded)
            .putInt("themeMode", s.themeMode.ordinal)
            .putInt("accentIndex", s.accentIndex)
            .putInt("previewMode", s.previewMode.ordinal)
            .apply()
    }

    fun update(block: (Settings) -> Settings) {
        _settings.update { old ->
            val next = block(old)
            save(next)
            next
        }
    }

    fun finishOnboarding() = update { it.copy(onboarded = true) }
}
