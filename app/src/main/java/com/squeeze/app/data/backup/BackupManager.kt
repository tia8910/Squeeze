package com.squeeze.app.data.backup

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.squeeze.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Where backup stands.
 *
 * @param email the signed-in Google account, null when not connected
 * @param needsReconnect access to Drive was revoked or expired and only the user can grant it again
 */
data class BackupStatus(
    val configured: Boolean,
    val email: String? = null,
    val name: String? = null,
    val lastBackupMs: Long? = null,
    val working: Boolean = false,
    val message: String? = null,
    val needsReconnect: Boolean = false,
)

/** The outcome of asking for Drive access. */
sealed interface DriveAccess {
    data class Granted(val token: String) : DriveAccess
    /** Google wants the user to approve; launch this and pass the result to [BackupManager.onConsentResult]. */
    data class NeedsConsent(val intent: PendingIntent) : DriveAccess
}

/**
 * Sign in with Google and automatic backup of all data to the user's own Google Drive.
 *
 * **Two steps, two purposes.** Credential Manager's *Sign in with Google* says who the user is
 * (the account shown in Settings). Drive access is a separate authorisation for the
 * `drive.appdata` scope only, which Google asks the user to approve once.
 *
 * **Automatic.** [backupIfDue] runs whenever the app goes to the background, at most once an
 * hour, using the access Google already granted — no prompt. If access was revoked it stops and
 * marks the status for reconnecting rather than prompting out of nowhere.
 *
 * **What leaves the phone:** the JSON from [BackupCodec] — numbers and settings, no photographs.
 */
@Singleton
class BackupManager @Inject constructor(
    private val context: Context,
    private val codec: BackupCodec,
    private val drive: DriveClient,
    private val scope: CoroutineScope,
    private val entitlements: com.squeeze.app.billing.Entitlements,
) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Mutex()

    private val _status = MutableStateFlow(
        BackupStatus(
            configured = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank(),
            email = prefs.getString(KEY_EMAIL, null),
            name = prefs.getString(KEY_NAME, null),
            lastBackupMs = prefs.getLong(KEY_LAST, 0L).takeIf { it > 0 },
            needsReconnect = prefs.getBoolean(KEY_RECONNECT, false),
        ),
    )
    val status: StateFlow<BackupStatus> = _status.asStateFlow()

    private val _restores = MutableStateFlow(0)

    /** Counts restores, so screens that read the database once know to read it again. */
    val restores: StateFlow<Int> = _restores.asStateFlow()

    // ── Sign-in ──────────────────────────────────────────────────────────────────────────

    /**
     * Connects Google Drive in one step: Google's own sheet picks the account and grants the
     * hidden app folder together.
     *
     * This used to be two steps, a Credential Manager account picker and then authorization.
     * The picker needs the Web client ID to match as well as the Android client, and when any
     * part of that did not line up it reported "cancelled" without ever showing a screen, so a
     * setup problem looked like the user backing out. Authorization alone needs only the
     * Android client (package name and signing SHA-1), and its errors carry a code.
     */
    suspend fun signIn(activity: Activity): DriveAccess {
        check(_status.value.configured) { NOT_CONFIGURED }
        return authorize(activity)
    }

    /** Drive access: a token straight away if already granted, otherwise Google's consent screen. */
    suspend fun authorize(from: Context = context): DriveAccess {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA), Scope(EMAIL_SCOPE)))
            .build()
        val result = try {
            Identity.getAuthorizationClient(from).authorize(request).await()
        } catch (e: com.google.android.gms.common.api.ApiException) {
            throw IllegalStateException(explain(e.statusCode))
        }
        val pending = result.pendingIntent
        return if (result.hasResolution() && pending != null) {
            DriveAccess.NeedsConsent(pending)
        } else {
            remember(result)
            setReconnect(false)
            DriveAccess.Granted(result.accessToken ?: error("Google returned no access token"))
        }
    }

    /** Records which Google account holds the backup, for the backup card. */
    private fun remember(result: com.google.android.gms.auth.api.identity.AuthorizationResult) {
        val account = runCatching { result.toGoogleSignInAccount() }.getOrNull()
        val email = account?.email ?: _status.value.email ?: "Google account"
        val name = account?.displayName ?: _status.value.name
        prefs.edit().putString(KEY_EMAIL, email).putString(KEY_NAME, name).apply()
        _status.value = _status.value.copy(email = email, name = name, message = null)
    }

    /** Google's error codes, in words someone can act on. */
    private fun explain(code: Int): String = when (code) {
        com.google.android.gms.common.api.CommonStatusCodes.DEVELOPER_ERROR ->
            "Google sign in is not set up for this version of Squeeze yet (code 10)."
        com.google.android.gms.common.api.CommonStatusCodes.NETWORK_ERROR ->
            "No connection to Google. Check your internet and try again."
        com.google.android.gms.common.api.CommonStatusCodes.CANCELED, 12501 -> "Sign in cancelled"
        else -> "Google sign in failed (code $code). Try again in a moment."
    }

    /** The token from Google's consent screen, or null if the user declined. */
    fun onConsentResult(data: Intent?): String? {
        if (data == null) return null
        val result = runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data) }
            .getOrNull() ?: return null
        val token = result.accessToken ?: return null
        remember(result)
        setReconnect(false)
        return token
    }

    suspend fun signOut() {
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
        prefs.edit().clear().apply()
        _status.value = BackupStatus(configured = _status.value.configured)
    }

    // ── Backup and restore ───────────────────────────────────────────────────────────────

    /** The backup in Drive, if there is one. */
    suspend fun findBackup(token: String): RemoteBackup? = drive.find(token)

    suspend fun backupNow(token: String): Boolean = lock.withLock { upload(token) }

    private suspend fun upload(token: String): Boolean {
        if (!codec.hasData()) return false
        _status.value = _status.value.copy(working = true, message = null)
        return try {
            drive.upload(token, codec.export(), drive.find(token)?.id)
            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST, now).apply()
            _status.value = _status.value.copy(working = false, lastBackupMs = now, message = "Backed up to Google Drive")
            true
        } catch (e: Exception) {
            if (e is DriveUnauthorized) setReconnect(true)
            _status.value = _status.value.copy(working = false, message = "Backup failed: ${e.message}")
            false
        }
    }

    suspend fun restore(token: String, backup: RemoteBackup): Unit = lock.withLock {
        _status.value = _status.value.copy(working = true, message = null)
        try {
            codec.restore(drive.download(token, backup.id))
            _restores.value += 1
            // What was just restored is what Drive already holds.
            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST, now).apply()
            _status.value = _status.value.copy(working = false, lastBackupMs = now, message = "Restored from Google Drive")
        } catch (e: Exception) {
            _status.value = _status.value.copy(working = false, message = "Restore failed: ${e.message}")
            throw e
        }
    }

    /**
     * Backs up in the background when signed in and the last backup is over an hour old.
     * Silent: never shows a prompt, so a revoked grant just marks the status for reconnecting.
     */
    fun backupIfDue() {
        val s = _status.value
        if (!s.configured || s.email == null || s.needsReconnect || s.working) return
        // Automatic backup is part of Pro; a restore from onboarding stays open to everyone.
        if (!entitlements.state.value.pro) return
        if (System.currentTimeMillis() - (s.lastBackupMs ?: 0L) < MIN_INTERVAL_MS) return
        scope.launch {
            val access = runCatching { authorize() }.getOrNull() ?: return@launch
            when (access) {
                is DriveAccess.Granted -> backupNow(access.token)
                is DriveAccess.NeedsConsent -> setReconnect(true)
            }
        }
    }

    private fun setReconnect(value: Boolean) {
        prefs.edit().putBoolean(KEY_RECONNECT, value).apply()
        _status.value = _status.value.copy(needsReconnect = value)
    }

    private companion object {
        const val PREFS = "google_backup"
        const val KEY_EMAIL = "email"
        const val EMAIL_SCOPE = "email"
        const val KEY_NAME = "name"
        const val KEY_LAST = "last_backup_ms"
        const val KEY_RECONNECT = "needs_reconnect"
        const val DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
        const val MIN_INTERVAL_MS = 60 * 60 * 1000L
        const val NOT_CONFIGURED = "Google sign-in isn't set up in this build yet (no web client ID)."
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
