package com.squeeze.app.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User-controlled security preferences.
 *
 * Kept in plain preferences rather than the encrypted database on purpose: these settings
 * have to be readable before the database is opened, because [com.squeeze.app.MainActivity]
 * applies them to the window on create — before the biometric gate has run and therefore
 * before there is any reason to unlock storage. They also reveal nothing on their own.
 */
@Singleton
class SecuritySettings @Inject constructor(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _blockScreenshots = MutableStateFlow(
        prefs.getBoolean(KEY_BLOCK_SCREENSHOTS, DEFAULT_BLOCK_SCREENSHOTS),
    )

    /**
     * When true, `FLAG_SECURE` is applied to the window, which blocks screenshots and
     * screen recording and keeps the app out of the recent-apps thumbnail.
     *
     * The thumbnail is the reason this setting is worth having at all: it is the most
     * likely way a body photo or a measurement is disclosed by accident, and unlike a
     * screenshot the user never chose to create it.
     */
    val blockScreenshots: StateFlow<Boolean> = _blockScreenshots.asStateFlow()

    fun setBlockScreenshots(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BLOCK_SCREENSHOTS, enabled).apply()
        _blockScreenshots.value = enabled
    }

    private companion object {
        const val PREFS = "squeeze_security"
        const val KEY_BLOCK_SCREENSHOTS = "block_screenshots"

        /**
         * Screenshots are permitted by default.
         *
         * The privacy-maximal default would be to block them, but blocking is absolute:
         * with `FLAG_SECURE` set, the user cannot capture their own progress to share, and
         * store listing screenshots cannot be taken at all. Making it a visible, one-tap
         * setting keeps the protection available to anyone who wants it without imposing a
         * restriction most users would experience as the app being broken.
         *
         * Screens that display captured photos should apply `FLAG_SECURE` unconditionally
         * regardless of this setting; see MainActivity's documentation.
         */
        const val DEFAULT_BLOCK_SCREENSHOTS = false
    }
}
