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

    /**
     * Light by default, rather than following the system.
     *
     * The brand sheet this app is built from is light-only: flat white cards, hairline
     * borders, navy text. That is the design as drawn and the one every screen was checked
     * against. A dark theme exists and is properly designed, but it is a variant — starting
     * a new user there shows them a version of the product nobody signed off as the default.
     *
     * Anyone who has already chosen keeps their choice. `getString` returns null only when
     * nothing was ever stored, so this default applies to first run and nothing else.
     */
    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.LIGHT),
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _landingSeen = MutableStateFlow(prefs.getBoolean(KEY_LANDING_SEEN, false))

    /** False only until the user has dismissed the landing screen once. */
    val landingSeen: StateFlow<Boolean> = _landingSeen.asStateFlow()

    private val _soundEnabled = MutableStateFlow(prefs.getBoolean(KEY_SOUND, true))

    /**
     * Short cues on saving, capturing and celebrating.
     *
     * On by default: they are brief, they follow the ringer, and they are tagged so the
     * system lets them sound alongside music rather than ducking it.
     */
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _ambientEnabled = MutableStateFlow(prefs.getBoolean(KEY_AMBIENT, false))

    /**
     * The looping motivational bed.
     *
     * Off by default, and that is a deliberate asymmetry with [soundEnabled]. This one is
     * music: it holds audio focus for as long as the app is open, and most people using a
     * fitness app already have something playing. Defaulting it on would mean the first
     * launch silences the user's own playlist, which is not a first impression worth having.
     */
    val ambientEnabled: StateFlow<Boolean> = _ambientEnabled.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    fun markLandingSeen() {
        prefs.edit().putBoolean(KEY_LANDING_SEEN, true).apply()
        _landingSeen.value = true
    }

    fun setSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND, enabled).apply()
        _soundEnabled.value = enabled
    }

    fun setAmbientEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AMBIENT, enabled).apply()
        _ambientEnabled.value = enabled
    }

    private companion object {
        const val PREFS = "squeeze_ui"
        const val KEY_THEME = "theme_mode"
        const val KEY_LANDING_SEEN = "landing_seen"
        const val KEY_SOUND = "sound_enabled"
        const val KEY_AMBIENT = "ambient_enabled"
    }
}
