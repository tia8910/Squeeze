package com.squeeze.app.notify

import android.content.Context
import com.squeeze.core.coach.ReminderKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which reminders the user wants, and when.
 *
 * Plain preferences rather than the encrypted database: the alarm receiver reads these on
 * every fire, including straight after a reboot, and nothing here is personal — a time of
 * day and three switches.
 *
 * Built directly from a [Context] rather than injected so the receiver, which runs without
 * the UI, can read it without the dependency graph.
 */
class NotificationSettings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(
        ReminderKind.entries.filter { prefs.getBoolean(key(it), true) }.toSet(),
    )

    /** On by default: each is at most one notification a day, and only when actionable. */
    val enabled: StateFlow<Set<ReminderKind>> = _enabled.asStateFlow()

    private val _morningMinutes = MutableStateFlow(prefs.getInt(KEY_MORNING, DEFAULT_MORNING))
    val morningMinutes: StateFlow<Int> = _morningMinutes.asStateFlow()

    private val _eveningMinutes = MutableStateFlow(prefs.getInt(KEY_EVENING, DEFAULT_EVENING))
    val eveningMinutes: StateFlow<Int> = _eveningMinutes.asStateFlow()

    /** Whether the system permission prompt has been shown once; it is never shown twice. */
    var permissionAsked: Boolean
        get() = prefs.getBoolean(KEY_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_ASKED, value).apply()

    fun setEnabled(kind: ReminderKind, on: Boolean) {
        prefs.edit().putBoolean(key(kind), on).apply()
        _enabled.value = if (on) _enabled.value + kind else _enabled.value - kind
    }

    fun setMorningMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_MORNING, minutes).apply()
        _morningMinutes.value = minutes
    }

    fun setEveningMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_EVENING, minutes).apply()
        _eveningMinutes.value = minutes
    }

    private fun key(kind: ReminderKind) = "reminder_${kind.name.lowercase()}"

    companion object {
        private const val PREFS = "squeeze_notifications"
        private const val KEY_MORNING = "morning_minutes"
        private const val KEY_EVENING = "evening_minutes"
        private const val KEY_ASKED = "permission_asked"

        /** 08:00 — before most people train, early enough to plan the day around it. */
        const val DEFAULT_MORNING = 8 * 60

        /** 19:30 — late enough that a session could be done, early enough to still do it. */
        const val DEFAULT_EVENING = 19 * 60 + 30

        val MORNING_CHOICES = listOf(6 * 60 + 30, 7 * 60, 8 * 60, 9 * 60)
        val EVENING_CHOICES = listOf(18 * 60, 19 * 60 + 30, 20 * 60 + 30, 21 * 60 + 30)

        fun format(minutes: Int): String = "%d:%02d".format(minutes / 60, minutes % 60)
    }
}
