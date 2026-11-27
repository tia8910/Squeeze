package com.squeeze.app.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.squeeze.app.MainActivity
import com.squeeze.app.R
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Daily training reminders and a weekly scan check-in, delivered by the system alarm clock.
 *
 * Nothing here needs the app to be running: each alarm wakes [ReminderReceiver], which posts
 * the notification and books the next one. Alarms are cleared by a reboot or an update, so the
 * receiver also listens for those and books them again.
 *
 * The text never contains body data. Notifications show on the lock screen, and the database is
 * encrypted behind the app lock, so a reminder only ever says what to do, not what was measured.
 */
object Reminders {

    const val CHANNEL = "reminders"
    private const val PREFS = "squeeze_reminders"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_HOUR = "hour"
    private const val KEY_MINUTE = "minute"
    private const val KEY_ASKED = "permission_asked"
    private const val KEY_COUNT = "count"

    internal const val ACTION_DAILY = "com.squeeze.app.REMIND_DAILY"
    internal const val ACTION_WEEKLY = "com.squeeze.app.REMIND_WEEKLY"

    private const val DAILY_ID = 1001
    private const val WEEKLY_ID = 1002
    private const val DEFAULT_HOUR = 18

    private val daily = listOf(
        "Time to train" to "Your plan for today is ready. Show up and it adds up.",
        "Keep the streak going" to "One good session today beats a perfect one next week.",
        "Fuel check" to "Log today's meals so your plan stays on target.",
        "Small wins count" to "Even 20 minutes moves you forward. Open today's workout.",
        "Nobody does it for you" to "You show up, you do the work. Today's session is waiting.",
    )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, true)

    fun time(context: Context): LocalTime = prefs(context).let {
        LocalTime.of(it.getInt(KEY_HOUR, DEFAULT_HOUR), it.getInt(KEY_MINUTE, 0))
    }

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
        if (on) schedule(context) else cancel(context)
    }

    fun setTime(context: Context, time: LocalTime) {
        prefs(context).edit().putInt(KEY_HOUR, time.hour).putInt(KEY_MINUTE, time.minute).apply()
        if (enabled(context)) schedule(context)
    }

    /** True once the app has asked for the notification permission, so it asks only once by itself. */
    fun permissionAsked(context: Context) = prefs(context).getBoolean(KEY_ASKED, false)

    fun markPermissionAsked(context: Context) = prefs(context).edit().putBoolean(KEY_ASKED, true).apply()

    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Books both reminders if they are on. Safe to call any number of times. */
    fun schedule(context: Context) {
        createChannel(context)
        if (!enabled(context)) return
        val now = LocalDateTime.now()
        val time = time(context)

        var nextDaily = now.toLocalDate().atTime(time)
        if (!nextDaily.isAfter(now)) nextDaily = nextDaily.plusDays(1)
        book(context, ACTION_DAILY, DAILY_ID, nextDaily)

        var nextWeekly = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(10, 0)
        if (!nextWeekly.isAfter(now)) nextWeekly = nextWeekly.plusWeeks(1)
        book(context, ACTION_WEEKLY, WEEKLY_ID, nextWeekly)
    }

    fun cancel(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        alarms.cancel(pending(context, ACTION_DAILY, DAILY_ID))
        alarms.cancel(pending(context, ACTION_WEEKLY, WEEKLY_ID))
    }

    private fun book(context: Context, action: String, id: Int, at: LocalDateTime) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Inexact but allowed while the phone dozes: a reminder a few minutes late is fine, and
        // this needs no exact-alarm permission.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending(context, action, id))
    }

    private fun pending(context: Context, action: String, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, ReminderReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Training reminders and the weekly scan check-in"
            },
        )
    }

    internal fun post(context: Context, action: String) {
        if (!enabled(context) || !canPost(context)) return
        createChannel(context)
        val (title, text) = if (action == ACTION_WEEKLY) {
            "Weekly check-in" to "Take your scan today and see what changed this week."
        } else {
            val count = prefs(context).getInt(KEY_COUNT, 0)
            prefs(context).edit().putInt(KEY_COUNT, count + 1).apply()
            daily[count % daily.size]
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF1768FF.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(if (action == ACTION_WEEKLY) WEEKLY_ID else DAILY_ID, notification)
        }
    }
}

/** Posts a due reminder and books the next, and rebooks everything after a reboot or update. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Reminders.ACTION_DAILY, Reminders.ACTION_WEEKLY -> Reminders.post(context, intent.action!!)
        }
        Reminders.schedule(context)
    }
}
