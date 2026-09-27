package com.squeeze.app.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.squeeze.app.ui.theme.SqueezeTheme
import com.squeeze.app.ui.theme.ThemeMode

/**
 * What the app does with Health Connect data, shown when the user taps the privacy link in
 * the Health Connect permission screen. Health Connect requires one; without it the
 * permission dialog does not open at all.
 */
class HealthPrivacyActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SqueezeTheme(themeMode = ThemeMode.SYSTEM) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("How Squeeze uses your health data", style = MaterialTheme.typography.titleLarge)
                        POINTS.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                        TextButton(onClick = ::finish) { Text("Close") }
                    }
                }
            }
        }
    }

    private companion object {
        val POINTS = listOf(
            "Squeeze has no internet permission. Nothing read from Health Connect can leave your phone — the operating system will not let the app open a network connection.",
            "Steps, active calories and workouts from your watch or phone show your day's activity, and a workout recorded by your watch counts as today's session.",
            "Sleep and resting heart rate add a short recovery note on days that stand out.",
            "Food logged in a nutrition app is compared with today's calorie and protein targets.",
            "Workouts you log in Squeeze, and your weight and body fat from a scan, are written back so your other apps see them too.",
            "You choose each type separately, and can change or revoke them any time in Health Connect.",
        )
    }
}
