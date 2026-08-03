package com.squeeze.app.data.settings

import android.content.Context
import com.squeeze.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Appearance and first-run state.
 *
 * Separate from [SecuritySettings] because these carry no security meaning: mixing a
 * cosmetic preference into the class that gates screenshots would make it harder to see,
 * later, which settings actually matter.
 *
 * Stored in plain preferences and read before the database opens, since the theme must be
 * known to draw the very first frame — including the biometric lock screen, which appears
 * before storage is unlocked.
 */
@Singleton
class UiSettings @Inject constructor(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM),
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _landingSeen = MutableStateFlow(prefs.getBoolean(KEY_LANDING_SEEN, false))

    /** False only until the user has dismissed the landing screen once. */
    val landingSeen: StateFlow<Boolean> = _landingSeen.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    fun markLandingSeen() {
        prefs.edit().putBoolean(KEY_LANDING_SEEN, true).apply()
        _landingSeen.value = true
    }

    private companion object {
        const val PREFS = "squeeze_ui"
        const val KEY_THEME = "theme_mode"
        const val KEY_LANDING_SEEN = "landing_seen"
    }
}
