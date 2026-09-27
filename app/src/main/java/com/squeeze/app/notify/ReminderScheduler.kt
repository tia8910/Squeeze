package com.squeeze.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.squeeze.core.coach.ReminderSlot
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Arms the two daily alarms that decide whether to notify.
 *
 * **Inexact on purpose.** `setAndAllowWhileIdle` lets the system batch the wake-up with
 * others, so it may land a few minutes late, and in exchange it needs no exact-alarm
 * permission and costs almost no battery. A workout reminder at 08:04 instead of 08:00 is
 * fine; an app that drains the battery to be punctual is not.
 *
 * **One shot, re-armed on every fire.** A repeating alarm drifts under Doze and survives a
 * time-zone change pointing at the wrong hour. Arming the next occurrence each time, and
 * again on boot and on clock changes, keeps it on the user's local time.
 */
object ReminderScheduler {

    const val ACTION_FIRE = "com.squeeze.app.notify.FIRE"
    const val EXTRA_SLOT = "slot"

    fun scheduleAll(context: Context) {
        ReminderSlot.entries.forEach { schedule(context, it) }
    }

    fun schedule(context: Context, slot: ReminderSlot) {
        val settings = NotificationSettings(context)
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val minutes = when (slot) {
            ReminderSlot.MORNING -> settings.morningMinutes.value
            ReminderSlot.EVENING -> settings.eveningMinutes.value
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAt(minutes), pendingIntent(context, slot))
    }

    fun cancelAll(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        ReminderSlot.entries.forEach { alarms.cancel(pendingIntent(context, it)) }
    }

    /** The next wall-clock moment at [minutesOfDay], today if still ahead, else tomorrow. */
    internal fun nextAt(minutesOfDay: Int, now: LocalDateTime = LocalDateTime.now()): Long {
        val time = LocalTime.of(minutesOfDay / 60, minutesOfDay % 60)
        val today = LocalDate.from(now).atTime(time)
        val next = if (today.isAfter(now.plusSeconds(30))) today else today.plusDays(1)
        return next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun pendingIntent(context: Context, slot: ReminderSlot): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            slot.ordinal,
            Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION_FIRE)
                .putExtra(EXTRA_SLOT, slot.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
