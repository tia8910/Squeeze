package com.squeeze.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.squeeze.app.MainActivity
import com.squeeze.app.R
import com.squeeze.core.coach.Reminder
import com.squeeze.core.coach.ReminderKind

/**
 * Posts a [Reminder] as a system notification.
 *
 * **One channel per kind**, so the user can silence the weekly summary in system settings
 * without losing workout reminders — Android's own controls, not only the app's.
 *
 * **Private on the lock screen.** The full text names sessions and counts; the public
 * version shown on a locked phone says only that something is ready. Body data is what this
 * app goes furthest to protect (it has no network permission at all), and a notification is
 * the one place it could be read without unlocking the phone.
 */
object Notifier {

    const val EXTRA_STEP = "open_step"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ReminderKind.entries.forEach { kind ->
            val (name, description) = channelText(kind)
            manager.createNotificationChannel(
                NotificationChannel(channelId(kind), name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    this.description = description
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                },
            )
        }
    }

    fun canPost(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
                )

    fun post(context: Context, reminder: Reminder) {
        if (!canPost(context)) return
        ensureChannels(context)

        val open = PendingIntent.getActivity(
            context,
            reminder.kind.ordinal,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .apply { reminder.opens?.let { putExtra(EXTRA_STEP, it.name) } },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val publicVersion = NotificationCompat.Builder(context, channelId(reminder.kind))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.publicTitle)
            .build()

        val notification = NotificationCompat.Builder(context, channelId(reminder.kind))
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(reminder.title)
            .setContentText(reminder.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .build()

        try {
            // One live notification per kind: a newer reminder replaces an unread older one.
            NotificationManagerCompat.from(context).notify(reminder.kind.ordinal + NOTIFICATION_BASE, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post; nothing to do.
        }
    }

    private fun channelId(kind: ReminderKind) = "reminders_${kind.name.lowercase()}"

    private fun channelText(kind: ReminderKind): Pair<String, String> = when (kind) {
        ReminderKind.WORKOUT -> "Workout reminders" to
            "Today's planned session in the morning, and a nudge if it is still open in the evening."
        ReminderKind.CHECK_IN -> "Weekly check-in" to
            "When a week has passed since your last scan, with tips for a photo that compares accurately."
        ReminderKind.WEEK_SUMMARY -> "Weekly summary" to
            "Sunday evening: sessions and sets done against the plan."
    }

    private const val NOTIFICATION_BASE = 4100
}
