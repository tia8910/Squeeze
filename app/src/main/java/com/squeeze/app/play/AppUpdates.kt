package com.squeeze.app.play

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Play in-app updates.
 *
 * Normal releases are offered as a flexible update: it downloads in the background while the
 * app stays usable, then [readyToInstall] shows a small "Restart to update" card. A release
 * published with update priority [URGENT] or higher is installed immediately instead, for the
 * rare fix that must not wait.
 *
 * A declined flexible update is not offered again for that version until it is [STALE_DAYS]
 * old, so the prompt never becomes a nag. Builds not installed from Play see nothing.
 */
@Singleton
class AppUpdates @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val manager = AppUpdateManagerFactory.create(context)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _readyToInstall = MutableStateFlow(false)

    /** A flexible update has downloaded and needs a restart to install. */
    val readyToInstall: StateFlow<Boolean> = _readyToInstall.asStateFlow()

    /** Offered at most once per app process, so returning to the app does not ask again. */
    private var offeredThisRun = false
    private var offeredVersion = 0

    init {
        manager.registerListener(
            InstallStateUpdatedListener { state ->
                if (state.installStatus() == InstallStatus.DOWNLOADED) _readyToInstall.value = true
            },
        )
    }

    /** Checks Play for an update and starts the right flow. Never throws. */
    suspend fun check(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        val info = runCatching { manager.requestAppUpdateInfo() }.getOrNull() ?: return

        if (info.installStatus() == InstallStatus.DOWNLOADED) {
            _readyToInstall.value = true
            return
        }

        val availability = info.updateAvailability()
        runCatching {
            when {
                // An immediate update the user left part way: resume it.
                availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS ->
                    manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE))

                availability != UpdateAvailability.UPDATE_AVAILABLE || offeredThisRun -> Unit

                info.updatePriority() >= URGENT && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> {
                    offeredThisRun = true
                    manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE))
                }

                info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {
                    val declined = prefs.getInt(KEY_DECLINED, 0) == info.availableVersionCode()
                    val stale = (info.clientVersionStalenessDays() ?: 0) >= STALE_DAYS
                    if (!declined || stale) {
                        offeredThisRun = true
                        offeredVersion = info.availableVersionCode()
                        manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE))
                    }
                }
            }
        }
    }

    /** The user said no to the update sheet. */
    fun declined() {
        if (offeredVersion != 0) prefs.edit().putInt(KEY_DECLINED, offeredVersion).apply()
    }

    /** Restarts the app into the downloaded update. */
    fun install() {
        manager.completeUpdate()
    }

    private companion object {
        const val PREFS = "squeeze_updates"
        const val KEY_DECLINED = "declined_version"
        const val URGENT = 4
        const val STALE_DAYS = 7
    }
}
