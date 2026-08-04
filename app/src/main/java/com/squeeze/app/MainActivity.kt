package com.squeeze.app

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.squeeze.app.audio.LocalSoundEngine
import com.squeeze.app.audio.SoundEngine
import com.squeeze.app.data.settings.SecuritySettings
import com.squeeze.app.data.settings.UiSettings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import com.squeeze.app.ui.SqueezeApp
import com.squeeze.app.ui.lock.LockScreen
import com.squeeze.app.ui.theme.SqueezeTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hosts the whole app.
 *
 * Two window-level protections are applied here rather than per screen, because getting
 * them wrong on one screen is the same as not having them:
 *
 *  - [WindowManager.LayoutParams.FLAG_SECURE], applied when the user has asked for it via
 *    [SecuritySettings.blockScreenshots]. It blocks screenshots and screen recording, and
 *    keeps the app's contents out of the recent-apps thumbnail.
 *  - A biometric gate stands in front of the UI whenever the device has one enrolled.
 *
 * Screenshots are permitted by default. `FLAG_SECURE` is all-or-nothing, so leaving it on
 * unconditionally would stop the user capturing their own progress to share and would make
 * store listing screenshots impossible to produce. It stays one tap away in Settings for
 * anyone who wants it.
 *
 * A screen that renders a captured body photo should set `FLAG_SECURE` on itself regardless
 * of this preference. The user opting into screenshots of their charts is not the same as
 * opting into screenshots of their body, and the recents thumbnail — which they never chose
 * to create — is the disclosure that actually matters.
 *
 * Extends [FragmentActivity] because BiometricPrompt requires a fragment host.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var securitySettings: SecuritySettings
    @Inject lateinit var uiSettings: UiSettings
    @Inject lateinit var soundEngine: SoundEngine

    private var unlocked by mutableStateOf(false)

    /**
     * True once the user has got in at least once during this activity's life.
     *
     * This is what keeps the app's UI composed behind the lock instead of being torn down
     * and rebuilt. See [setContent] below for why that matters.
     */
    private var everUnlocked by mutableStateOf(false)

    /** Guards against stacking prompts when onStart runs again with one already showing. */
    private var promptShowing = false

    /** Elapsed-realtime clock reading from when the app last left the foreground. */
    private var leftForegroundAt = 0L

    private fun biometricsAvailable(): Boolean =
        BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        applyScreenshotPolicy()
        applyAmbientPolicy()

        // With no enrolled biometric there is nothing to prompt for. Blocking access would
        // lock the user out of their own data with no recovery path, since there is no
        // account to reset against.
        unlocked = !biometricsAvailable()
        everUnlocked = unlocked

        setContent {
            // Read here rather than inside SqueezeApp so the lock screen — which renders
            // before the main UI exists — already wears the user's chosen theme.
            val themeMode by uiSettings.themeMode.collectAsState()

            SqueezeTheme(themeMode = themeMode) {
                CompositionLocalProvider(LocalSoundEngine provides soundEngine) {
                    Box(Modifier.fillMaxSize()) {
                        // Kept in the composition while locked, and covered rather than
                        // replaced. Swapping it out for the lock screen destroyed the
                        // NavController and every ViewModel hanging off it, so returning
                        // from a biometric prompt dropped the user back at the dashboard
                        // and threw away whatever they were part-way through. A scan whose
                        // results had not been saved yet was simply gone — the app looked
                        // like it had reset itself, because it had.
                        //
                        // Not composed at all before the first unlock: there is no state to
                        // preserve yet, and building the dashboard behind the gate would
                        // read the database before the user has proven the phone is theirs.
                        if (everUnlocked) SqueezeApp()

                        // LockScreen draws an opaque Surface over the full size, so nothing
                        // underneath is visible here or in the recents thumbnail.
                        if (!unlocked) LockScreen(onAuthenticate = ::promptForBiometric)
                    }
                }
            }
        }
    }

    /**
     * Asks for the biometric on the way in, rather than making the user tap first.
     *
     * In [onStart] rather than [onCreate] so it also covers coming back from the background,
     * which is the common case: the previous version prompted only on creation, so every
     * return left the user staring at a lock screen waiting to be tapped.
     */
    override fun onStart() {
        super.onStart()

        if (unlocked || !biometricsAvailable()) return

        // A short absence is not a handover. Stepping out to the gallery to pick a photo,
        // answering a permission dialog, or glancing at a notification all stop the activity,
        // and demanding a fingerprint for each of them teaches the user to resent the lock.
        // Longer than this and the phone has plausibly left the user's hands, which is the
        // case the gate exists for.
        if (everUnlocked &&
            leftForegroundAt != 0L &&
            SystemClock.elapsedRealtime() - leftForegroundAt < GRACE_PERIOD_MS
        ) {
            unlocked = true
            return
        }

        promptForBiometric()
    }

    /**
     * Keeps the window's secure flag in step with the user's preference.
     *
     * Collected for the lifetime of the activity rather than read once, so toggling the
     * setting takes effect immediately instead of on the next launch — a setting that
     * appears not to work is worse than no setting.
     */
    private fun applyScreenshotPolicy() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                securitySettings.blockScreenshots.collect { block ->
                    if (block) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }
    }

    /**
     * Runs the ambient bed only while the app is genuinely in front of the user.
     *
     * Scoped to RESUMED rather than STARTED, and stopped when that scope ends, so the music
     * dies the moment the app is backgrounded or a dialog takes over. A backing track that
     * outlives the screen it belongs to is the single most common way this feature becomes
     * the reason someone uninstalls an app.
     *
     * Gated on [unlocked] as well: nothing should play over the biometric gate, when the
     * user has not yet proven the phone is theirs.
     */
    private fun applyAmbientPolicy() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try {
                    uiSettings.ambientEnabled.collect { enabled ->
                        if (enabled && unlocked) {
                            soundEngine.startAmbient()
                        } else {
                            soundEngine.stopAmbient()
                        }
                    }
                } finally {
                    // Reached when the RESUMED scope is cancelled, i.e. on pause. The engine
                    // stops on its own dispatcher, so this survives the cancellation.
                    soundEngine.stopAmbient()
                }
            }
        }
    }

    private fun promptForBiometric() {
        if (promptShowing) return
        promptShowing = true

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    promptShowing = false
                    unlocked = true
                    everUnlocked = true
                    // The ambient collector already ran while locked and chose silence, and
                    // it will not re-emit for a change it cannot see. Start it here instead.
                    if (uiSettings.ambientEnabled.value) soundEngine.startAmbient()
                }

                // Cancelling leaves the lock screen up with its button, which is the right
                // outcome. What this has to do is clear the flag, or that button and the
                // next onStart would both be dead.
                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    promptShowing = false
                }
            },
        )

        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.lock_title))
                .setSubtitle(getString(R.string.lock_subtitle))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .setNegativeButtonText(getString(R.string.lock_cancel))
                .build(),
        )
    }

    override fun onStop() {
        super.onStop()
        soundEngine.stopAmbient()

        // Cover the UI the moment the app leaves the foreground, so handing someone the
        // phone does not hand them the measurement history, and so the recents thumbnail
        // shows the lock screen rather than the dashboard.
        //
        // Whether the user has to authenticate again is decided in onStart, not here: this
        // only draws the cover. The app itself stays composed underneath, so restoring is
        // free and nothing they were doing is lost either way.
        if (biometricsAvailable()) {
            leftForegroundAt = SystemClock.elapsedRealtime()
            unlocked = false
        }
    }

    private companion object {
        /**
         * How long the app may be away before it insists on a fingerprint again.
         *
         * Short enough that a phone put down on a desk is protected within about the time it
         * takes to walk away from it, long enough to cover the gallery picker and permission
         * dialogs the app itself puts the user through.
         */
        const val GRACE_PERIOD_MS = 30_000L
    }
}
