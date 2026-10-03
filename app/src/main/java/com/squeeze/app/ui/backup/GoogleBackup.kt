package com.squeeze.app.ui.backup

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.squeeze.app.data.backup.BackupManager
import com.squeeze.app.data.backup.BackupStatus
import com.squeeze.app.data.backup.DriveAccess
import com.squeeze.app.data.backup.RemoteBackup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

data class BackupUi(
    /** Google's consent screen, waiting to be launched. */
    val consent: PendingIntent? = null,
    /** A backup found in Drive, waiting for the user to restore it or not. */
    val found: RemoteBackup? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

/** What to do once Drive access is granted. */
private enum class Then { CHECK, BACKUP }

@HiltViewModel
class BackupViewModel @Inject constructor(private val backup: BackupManager) : ViewModel() {

    val status: StateFlow<BackupStatus> = backup.status

    private val _ui = MutableStateFlow(BackupUi())
    val ui: StateFlow<BackupUi> = _ui.asStateFlow()

    private var token: String? = null
    private var then = Then.CHECK

    /** Sign in, get Drive access, then look for a backup to restore. */
    fun connect(activity: Activity) = withDrive(Then.CHECK) { backup.signIn(activity) }

    fun backupNow(activity: Activity) = withDrive(Then.BACKUP) { backup.authorize(activity) }

    fun reconnect(activity: Activity) = withDrive(Then.BACKUP) { backup.authorize(activity) }

    private fun withDrive(next: Then, access: suspend () -> DriveAccess) {
        then = next
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null)
            try {
                when (val a = access()) {
                    is DriveAccess.Granted -> proceed(a.token)
                    is DriveAccess.NeedsConsent -> _ui.value = _ui.value.copy(consent = a.intent, busy = false)
                }
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(busy = false, error = e.message ?: "Couldn't reach Google")
            }
        }
    }

    fun consentLaunched() {
        _ui.value = _ui.value.copy(consent = null)
    }

    fun onConsent(data: android.content.Intent?) {
        val granted = backup.onConsentResult(data)
        if (granted == null) {
            _ui.value = _ui.value.copy(busy = false, error = "Drive access wasn't granted, so nothing can be backed up.")
            return
        }
        viewModelScope.launch { proceed(granted) }
    }

    private suspend fun proceed(granted: String) {
        token = granted
        try {
            when (then) {
                Then.CHECK -> {
                    val found = backup.findBackup(granted)
                    if (found != null) _ui.value = _ui.value.copy(found = found) else backup.backupNow(granted)
                }
                Then.BACKUP -> backup.backupNow(granted)
            }
            _ui.value = _ui.value.copy(busy = false)
        } catch (e: Exception) {
            _ui.value = _ui.value.copy(busy = false, error = e.message ?: "Couldn't reach Google Drive")
        }
    }

    fun restore() {
        val t = token ?: return
        val found = _ui.value.found ?: return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(found = null, busy = true)
            runCatching { backup.restore(t, found) }
                .onFailure { _ui.value = _ui.value.copy(error = it.message) }
            _ui.value = _ui.value.copy(busy = false)
        }
    }

    /** Keeps this phone's data; it becomes the backup (a no-op before there is any). */
    fun keepLocal() {
        val t = token
        _ui.value = _ui.value.copy(found = null)
        if (t != null) viewModelScope.launch { backup.backupNow(t) }
    }

    fun signOut() {
        viewModelScope.launch { backup.signOut() }
    }
}

/**
 * Sign in with Google and Drive backup, for onboarding ([compact]) and Settings.
 */
@Composable
fun GoogleBackupCard(compact: Boolean, modifier: Modifier = Modifier, viewModel: BackupViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onConsent(result.data)
    }
    LaunchedEffect(ui.consent) {
        ui.consent?.let {
            launcher.launch(IntentSenderRequest.Builder(it.intentSender).build())
            viewModel.consentLaunched()
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (status.email == null) {
            OutlinedButton(
                onClick = { activity?.let(viewModel::connect) },
                enabled = !ui.busy && status.configured,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("G", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("   Continue with Google")
            }
            Text(
                if (status.configured) {
                    if (compact) "Already used Squeeze? Sign in to restore your backup. New here? Sign in and your data backs up to your own Google Drive automatically."
                    else "Back up all your data to your own Google Drive automatically."
                } else {
                    "Google sign-in isn't set up in this build yet."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text("Signed in as ${status.email}", style = MaterialTheme.typography.titleSmall)
            Text(
                when {
                    status.needsReconnect -> "Google Drive access expired — reconnect to keep backing up."
                    status.lastBackupMs != null -> "Backs up automatically · last backup " +
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(status.lastBackupMs!!))
                    else -> "Backs up automatically once you have data."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!compact) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (status.needsReconnect) {
                        OutlinedButton(onClick = { activity?.let(viewModel::reconnect) }, enabled = !ui.busy) { Text("Reconnect") }
                    } else {
                        OutlinedButton(onClick = { activity?.let(viewModel::backupNow) }, enabled = !ui.busy) { Text("Back up now") }
                    }
                    TextButton(onClick = viewModel::signOut, enabled = !ui.busy) { Text("Sign out") }
                }
            }
        }
        Text(
            "Photos never leave this phone — only your numbers, logs and settings are backed up.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (ui.busy || status.working) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Talking to Google…", style = MaterialTheme.typography.bodySmall)
            }
        }
        (ui.error ?: status.message)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = if (ui.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
    }

    ui.found?.let { found ->
        AlertDialog(
            onDismissRequest = viewModel::keepLocal,
            title = { Text("Restore your backup?") },
            text = {
                Text(
                    "A Squeeze backup is in your Google Drive" +
                        (found.modifiedMs?.let { " from " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)) } ?: "") +
                        ". Restoring brings back your profile, measurements, workouts and plans" +
                        (if (compact) ". Starting fresh replaces that backup with your new data." else ", replacing what's on this phone."),
                )
            },
            confirmButton = { TextButton(onClick = viewModel::restore) { Text("Restore") } },
            dismissButton = {
                TextButton(onClick = viewModel::keepLocal) { Text(if (compact) "Start fresh" else "Keep this phone's data") }
            },
        )
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
