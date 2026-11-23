package com.squeeze.app.play

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.ktx.launchReview
import com.google.android.play.core.review.ktx.requestReview
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks for a Google Play rating at a good moment, never at a bad one.
 *
 * A good moment is a saved scan or a finished workout: the user just got something from the
 * app. The card appears only after [MIN_MOMENTS] of them, at least [MIN_DAYS] after install,
 * and at most once every [REASK_DAYS]. Play itself also rations the card and may show nothing,
 * which is why nothing here depends on whether it appeared.
 *
 * The review is written in Play's own sheet over the app. Nothing about the user is read or
 * sent by this class; Play handles the rating end to end.
 */
@Singleton
class AppReview @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _due = MutableStateFlow(false)

    /** True when the next calm moment should show the rating card. */
    val due: StateFlow<Boolean> = _due.asStateFlow()

    /** Called after a saved scan or a finished workout. */
    fun goodMoment() {
        val moments = prefs.getInt(KEY_MOMENTS, 0) + 1
        prefs.edit().putInt(KEY_MOMENTS, moments).apply()
        val lastAsked = prefs.getLong(KEY_ASKED_AT, 0L)
        if (moments >= MIN_MOMENTS &&
            daysSince(installedAt()) >= MIN_DAYS &&
            (lastAsked == 0L || daysSince(lastAsked) >= REASK_DAYS)
        ) {
            _due.value = true
        }
    }

    /** Shows Play's rating card if one is due. Never throws: a missing card costs nothing. */
    suspend fun ask(activity: Activity) {
        if (!_due.value) return
        _due.value = false
        prefs.edit().putLong(KEY_ASKED_AT, System.currentTimeMillis()).apply()
        runCatching {
            val manager = ReviewManagerFactory.create(activity)
            manager.launchReview(activity, manager.requestReview())
        }
    }

    private fun installedAt(): Long = runCatching {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        info.firstInstallTime
    }.getOrDefault(System.currentTimeMillis())

    private fun daysSince(millis: Long): Long = (System.currentTimeMillis() - millis) / DAY_MS

    private companion object {
        const val PREFS = "squeeze_review"
        const val KEY_MOMENTS = "moments"
        const val KEY_ASKED_AT = "asked_at"
        const val MIN_MOMENTS = 3
        const val MIN_DAYS = 3L
        const val REASK_DAYS = 120L
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
