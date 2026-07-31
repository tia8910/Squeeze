package com.squeeze.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.squeeze.app.ui.SqueezeApp
import com.squeeze.app.ui.lock.LockScreen
import com.squeeze.app.ui.theme.SqueezeTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Hosts the whole app.
 *
 * Two window-level protections are applied here rather than per screen, because getting
 * them wrong on one screen is the same as not having them:
 *
 *  - [WindowManager.LayoutParams.FLAG_SECURE] blocks screenshots and screen recording, and
 *    keeps the app's contents out of the recent-apps thumbnail. On an app that displays
 *    body photos, that thumbnail is the most likely accidental disclosure.
 *  - A biometric gate stands in front of the UI whenever the device has one enrolled.
 *
 * Extends [FragmentActivity] because BiometricPrompt requires a fragment host.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private var unlocked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        val biometricsAvailable = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

        // With no enrolled biometric there is nothing to prompt for. Blocking access would
        // lock the user out of their own data with no recovery path, since there is no
        // account to reset against.
        unlocked = !biometricsAvailable

        setContent {
            SqueezeTheme {
                if (unlocked) {
                    SqueezeApp()
                } else {
                    LockScreen(onAuthenticate = ::promptForBiometric)
                }
            }
        }

        if (biometricsAvailable) promptForBiometric()
    }

    private fun promptForBiometric() {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlocked = true
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
        // Re-lock whenever the app leaves the foreground, so handing someone the phone
        // does not hand them the measurement history.
        if (BiometricManager.from(this)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
            unlocked = false
        }
    }
}
