package com.squeeze.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.squeeze.app.data.CoachRepository
import com.squeeze.core.coach.ReminderPlanner
import com.squeeze.core.coach.ReminderSlot
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Wakes at each reminder slot, decides from the user's own data whether there is anything
 * worth saying, posts it, and arms the next alarm. Also re-arms after a reboot, an app
 * update or a clock change, which clear or skew alarms.
 *
 * Everything is decided on the device from the local database; the app has no network
 * permission and a notification never needs one.
 */
class ReminderReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun coach(): CoachRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        if (intent.action != ReminderScheduler.ACTION_FIRE) {
            // BOOT_COMPLETED, MY_PACKAGE_REPLACED, TIME_SET, TIMEZONE_CHANGED.
            ReminderScheduler.scheduleAll(app)
            return
        }
        val slot = runCatching { ReminderSlot.valueOf(intent.getStringExtra(ReminderScheduler.EXTRA_SLOT) ?: "") }
            .getOrNull() ?: return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // A receiver has about ten seconds; a database that is slow to open must not
                // turn into an ANR, so the decision gets a budget and is skipped past it.
                withTimeoutOrNull(BUDGET_MS) {
                    val enabled = NotificationSettings(app).enabled.value
                    if (enabled.isEmpty()) return@withTimeoutOrNull
                    val coach = EntryPointAccessors.fromApplication(app, Dependencies::class.java).coach()
                    ReminderPlanner.plan(slot, coach.reminderFacts(), enabled)?.let { Notifier.post(app, it) }
                }
            } catch (_: Exception) {
                // A reminder that cannot be composed is simply not sent.
            } finally {
                ReminderScheduler.schedule(app, slot)
                pending.finish()
            }
        }
    }

    private companion object {
        const val BUDGET_MS = 8_000L
    }
}
