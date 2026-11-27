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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** The kinds of notification the user can switch on and off one by one. */
enum class ReminderKind(val title: String, val detail: String, val channel: String, val channelName: String) {
    TRAINING("Training reminder", "A daily nudge to train, plus a scan check-in on Sundays.", "reminders", "Training reminders"),
    MEALS("Meal reminders", "Lunch and dinner from your Fuel plan, once you have built one.", "meals", "Meal reminders"),
    PROGRESS("Progress", "Monday recap, goal countdown and new personal records.", "progress", "Progress and records"),
    COMEBACK("Comeback", "A nudge after 3 and 7 days away, then nothing more.", "comeback", "Comeback reminders"),
}

/**
 * Every notification the app sends, delivered by the system alarm clock.
 *
 * Nothing here needs the app to be running: each alarm wakes [ReminderReceiver], which decides
 * whether anything is worth saying, posts it, and books the next alarm. Alarms are cleared by a
 * reboot or an update, so the receiver also listens for those and books them again.
 *
 * **What it may read.** The body data is encrypted behind the app lock and the receiver cannot
 * open it, nor should it. The app instead notes a few dates and counts here as they happen: the
 * days a session was saved, the last day the app was opened, the goal date, whether Fuel was set
 * up. Nothing about the body. Notifications show on the lock screen, so they only ever say what
 * to do or how often it was done, never what was measured.
 */
object Reminders {

    private const val PREFS = "squeeze_reminders"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_HOUR = "hour"
    private const val KEY_MINUTE = "minute"
    private const val KEY_ASKED = "permission_asked"
    private const val KEY_COUNT = "count"
    private const val KEY_SESSIONS = "session_days"
    private const val KEY_LAST_OPEN = "last_open_day"
    private const val KEY_GOAL_DAY = "goal_day"
    private const val KEY_FUEL = "fuel_used"

    internal const val ACTION_DAILY = "com.squeeze.app.REMIND_DAILY"
    internal const val ACTION_WEEKLY = "com.squeeze.app.REMIND_WEEKLY"
    internal const val ACTION_MORNING = "com.squeeze.app.REMIND_MORNING"
    internal const val ACTION_LUNCH = "com.squeeze.app.REMIND_LUNCH"
    internal const val ACTION_DINNER = "com.squeeze.app.REMIND_DINNER"

    private const val DAILY_ID = 1001
    private const val WEEKLY_ID = 1002
    private const val MORNING_ID = 1003
    private const val LUNCH_ID = 1004
    private const val DINNER_ID = 1005
    private const val RECORD_ID = 1006
    private const val DEFAULT_HOUR = 18

    private val MORNING = LocalTime.of(9, 30)
    private val LUNCH = LocalTime.of(12, 30)
    private val DINNER = LocalTime.of(19, 0)

    private val daily = listOf(
        "Time to train" to "Your plan for today is ready. Show up and it adds up.",
        "Keep the streak going" to "One good session today beats a perfect one next week.",
        "Small wins count" to "Even 20 minutes moves you forward. Open today's workout.",
        "Nobody does it for you" to "You show up, you do the work. Today's session is waiting.",
        "Your future self says thanks" to "Today's session is the one that counts. Let's go.",
    )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── Settings ──────────────────────────────────────────────────────────────────────────

    /** The master switch. Each kind also has its own. */
    fun enabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, true)

    fun enabled(context: Context, kind: ReminderKind) = prefs(context).getBoolean("kind_${kind.name}", true)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
        if (on) schedule(context) else cancel(context)
    }

    fun setEnabled(context: Context, kind: ReminderKind, on: Boolean) {
        prefs(context).edit().putBoolean("kind_${kind.name}", on).apply()
        schedule(context)
    }

    fun time(context: Context): LocalTime = prefs(context).let {
        LocalTime.of(it.getInt(KEY_HOUR, DEFAULT_HOUR), it.getInt(KEY_MINUTE, 0))
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

    // ── What the app notes as it happens: dates and counts only ───────────────────────────

    /** A training or sport session was saved today. */
    fun noteSession(context: Context) {
        val today = LocalDate.now().toEpochDay()
        val kept = sessionDays(context).filter { it > today - 21 }.toMutableSet().apply { add(today) }
        prefs(context).edit().putStringSet(KEY_SESSIONS, kept.map { it.toString() }.toSet()).apply()
    }

    /** The app was opened today; resets the comeback countdown. */
    fun noteOpen(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_OPEN, LocalDate.now().toEpochDay()).apply()
    }

    fun noteGoalDay(context: Context, epochDay: Long?) {
        prefs(context).edit().apply {
            if (epochDay == null) remove(KEY_GOAL_DAY) else putLong(KEY_GOAL_DAY, epochDay)
        }.apply()
    }

    /** Fuel has a meal plan, so meal reminders have something to point at. */
    fun noteFuelUsed(context: Context) {
        if (!prefs(context).getBoolean(KEY_FUEL, false)) {
            prefs(context).edit().putBoolean(KEY_FUEL, true).apply()
            schedule(context)
        }
    }

    private fun sessionDays(context: Context): Set<Long> =
        prefs(context).getStringSet(KEY_SESSIONS, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    // ── Scheduling ────────────────────────────────────────────────────────────────────────

    /** Books every reminder that is on and cancels the rest. Safe to call any number of times. */
    fun schedule(context: Context) {
        createChannels(context)
        if (!enabled(context)) {
            cancel(context)
            return
        }
        val now = LocalDateTime.now()
        fun next(time: LocalTime): LocalDateTime =
            now.toLocalDate().atTime(time).let { if (it.isAfter(now)) it else it.plusDays(1) }

        if (enabled(context, ReminderKind.TRAINING)) {
            book(context, ACTION_DAILY, DAILY_ID, next(time(context)))
            var sunday = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(10, 0)
            if (!sunday.isAfter(now)) sunday = sunday.plusWeeks(1)
            book(context, ACTION_WEEKLY, WEEKLY_ID, sunday)
        } else {
            unbook(context, ACTION_DAILY, DAILY_ID)
            unbook(context, ACTION_WEEKLY, WEEKLY_ID)
        }

        if (enabled(context, ReminderKind.PROGRESS) || enabled(context, ReminderKind.COMEBACK)) {
            book(context, ACTION_MORNING, MORNING_ID, next(MORNING))
        } else {
            unbook(context, ACTION_MORNING, MORNING_ID)
        }

        if (enabled(context, ReminderKind.MEALS) && prefs(context).getBoolean(KEY_FUEL, false)) {
            book(context, ACTION_LUNCH, LUNCH_ID, next(LUNCH))
            book(context, ACTION_DINNER, DINNER_ID, next(DINNER))
        } else {
            unbook(context, ACTION_LUNCH, LUNCH_ID)
            unbook(context, ACTION_DINNER, DINNER_ID)
        }
    }

    fun cancel(context: Context) {
        unbook(context, ACTION_DAILY, DAILY_ID)
        unbook(context, ACTION_WEEKLY, WEEKLY_ID)
        unbook(context, ACTION_MORNING, MORNING_ID)
        unbook(context, ACTION_LUNCH, LUNCH_ID)
        unbook(context, ACTION_DINNER, DINNER_ID)
    }

    private fun book(context: Context, action: String, id: Int, at: LocalDateTime) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Inexact but allowed while the phone dozes: a reminder a few minutes late is fine, and
        // this needs no exact-alarm permission.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending(context, action, id))
    }

    private fun unbook(context: Context, action: String, id: Int) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context, action, id))
    }

    private fun pending(context: Context, action: String, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, ReminderReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ReminderKind.entries.forEach { kind ->
            manager.createNotificationChannel(
                NotificationChannel(kind.channel, kind.channelName, NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = kind.detail },
            )
        }
    }

    // ── Deciding what to say ──────────────────────────────────────────────────────────────

    internal fun onAlarm(context: Context, action: String) {
        if (!enabled(context) || !canPost(context)) return
        val today = LocalDate.now()
        when (action) {
            ACTION_DAILY -> if (enabled(context, ReminderKind.TRAINING)) {
                val count = prefs(context).getInt(KEY_COUNT, 0)
                prefs(context).edit().putInt(KEY_COUNT, count + 1).apply()
                val (title, text) = daily[count % daily.size]
                post(context, ReminderKind.TRAINING, DAILY_ID, title, text)
            }

            ACTION_WEEKLY -> if (enabled(context, ReminderKind.TRAINING)) {
                post(context, ReminderKind.TRAINING, WEEKLY_ID, "Weekly check-in", "Take your scan today and see what changed this week.")
            }

            ACTION_LUNCH -> if (enabled(context, ReminderKind.MEALS)) {
                post(context, ReminderKind.MEALS, LUNCH_ID, "Lunch is planned", "Today's lunch from your Fuel plan is ready. Eat for your goal.")
            }

            ACTION_DINNER -> if (enabled(context, ReminderKind.MEALS)) {
                post(context, ReminderKind.MEALS, DINNER_ID, "Dinner time", "Finish the day on plan. Your dinner is in Fuel.")
            }

            // One morning message at most, in this order: the Monday recap, the goal countdown,
            // then the comeback nudge.
            ACTION_MORNING -> morningMessage(context, today)?.let { (kind, title, text) ->
                post(context, kind, MORNING_ID, title, text)
            }
        }
    }

    private fun morningMessage(context: Context, today: LocalDate): Triple<ReminderKind, String, String>? {
        val progress = enabled(context, ReminderKind.PROGRESS)

        if (progress && today.dayOfWeek == DayOfWeek.MONDAY) {
            val start = today.minusDays(7).toEpochDay()
            val end = today.minusDays(1).toEpochDay()
            val days = sessionDays(context).count { it in start..end }
            val text = when {
                days >= 4 -> "Last week: $days training days. Strong week. Keep it rolling."
                days >= 2 -> "Last week: $days training days. Solid. One more this week makes it great."
                days == 1 -> "Last week: 1 training day. Every week is a new start. Aim for 2 or more."
                else -> "New week, fresh start. Your first session is waiting."
            }
            return Triple(ReminderKind.PROGRESS, "Your week in review", text)
        }

        val goal = prefs(context).getLong(KEY_GOAL_DAY, Long.MIN_VALUE)
        if (progress && goal != Long.MIN_VALUE) {
            val left = goal - today.toEpochDay()
            val text = when (left) {
                28L -> "4 weeks to your target date. Stay on plan and it shows."
                14L -> "2 weeks to your target date. This is where it counts."
                7L -> "1 week to your target date. Finish strong."
                0L -> "Today is your target date. Take a scan and see how far you came."
                else -> null
            }
            if (text != null) return Triple(ReminderKind.PROGRESS, "Goal countdown", text)
        }

        val lastOpen = prefs(context).getLong(KEY_LAST_OPEN, Long.MIN_VALUE)
        if (enabled(context, ReminderKind.COMEBACK) && lastOpen != Long.MIN_VALUE) {
            when (today.toEpochDay() - lastOpen) {
                3L -> return Triple(ReminderKind.COMEBACK, "We saved your spot", "3 days off is fine. A short session today gets you back on track.")
                7L -> return Triple(ReminderKind.COMEBACK, "Ready when you are", "It has been a week. Start small: one session, 20 minutes.")
            }
        }
        return null
    }

    /** A personal record was set in the session just saved. */
    fun postRecords(context: Context, exercises: List<String>) {
        if (exercises.isEmpty() || !enabled(context) || !enabled(context, ReminderKind.PROGRESS) || !canPost(context)) return
        val text = when (exercises.size) {
            1 -> "New best on ${exercises[0]}. That is progress you can feel."
            else -> "New bests on ${exercises.take(3).joinToString(", ")}. Huge session."
        }
        post(context, ReminderKind.PROGRESS, RECORD_ID, "New personal record", text)
    }

    private fun post(context: Context, kind: ReminderKind, id: Int, title: String, text: String) {
        createChannels(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, kind.channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF1768FF.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}

/** Posts a due reminder and books the next, and rebooks everything after a reboot or update. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.action?.let { Reminders.onAlarm(context, it) }
        Reminders.schedule(context)
    }
}
