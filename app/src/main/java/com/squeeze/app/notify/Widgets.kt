package com.squeeze.app.notify

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.squeeze.app.MainActivity
import com.squeeze.app.R

/**
 * The few figures the home screen widgets show, saved by the app when it is left.
 *
 * Widgets run outside the app and must never open the encrypted database, and the home screen
 * is visible to anyone holding the phone, so they show only how consistent the user is and
 * what is next: the training week streak, the next workout's name and the days to the next
 * scan. Never a weight or a body fat.
 */
object WidgetData {
    private const val PREFS = "squeeze_widget"

    data class Snapshot(val streakWeeks: Int, val nextWorkout: String?, val nextScanDays: Int?)

    fun save(context: Context, snapshot: Snapshot) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("streak", snapshot.streakWeeks)
            .putString("workout", snapshot.nextWorkout)
            .putInt("scan", snapshot.nextScanDays ?: -1)
            .apply()
        refreshAll(context)
    }

    fun read(context: Context): Snapshot {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Snapshot(
            streakWeeks = p.getInt("streak", 0),
            nextWorkout = p.getString("workout", null),
            nextScanDays = p.getInt("scan", -1).takeIf { it >= 0 },
        )
    }

    /** Redraws every placed widget of both sizes. */
    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        listOf(WideWidget::class.java, ScanWidget::class.java).forEach { cls ->
            val ids = manager.getAppWidgetIds(ComponentName(context, cls))
            if (ids.isNotEmpty()) {
                context.sendBroadcast(
                    Intent(context, cls)
                        .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
                )
            }
        }
    }

    internal fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        2001,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun scanText(days: Int?): String = when (days) {
        null -> "Take your first scan"
        0 -> "Scan day today"
        1 -> "Next scan tomorrow"
        else -> "Next scan in $days days"
    }
}

/** Four by two: streak, next workout, next scan, and a button into the app. */
class WideWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val data = WidgetData.read(context)
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_wide).apply {
                setTextViewText(R.id.widget_streak, "🔥 ${data.streakWeeks}")
                setTextViewText(R.id.widget_streak_label, if (data.streakWeeks == 1) "week in a row" else "weeks in a row")
                setTextViewText(R.id.widget_workout, data.nextWorkout ?: "Rest or recover")
                setTextViewText(R.id.widget_scan, WidgetData.scanText(data.nextScanDays))
                setOnClickPendingIntent(R.id.widget_root, WidgetData.openApp(context))
            }
            manager.updateAppWidget(id, views)
        }
    }
}

/** Two by two: days to the next scan. */
class ScanWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val days = WidgetData.read(context).nextScanDays
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_scan).apply {
                setTextViewText(R.id.widget_scan_days, days?.toString() ?: "Go")
                setTextViewText(
                    R.id.widget_scan_unit,
                    when (days) {
                        null -> "first scan"
                        0 -> "scan today"
                        1 -> "day to scan"
                        else -> "days to scan"
                    },
                )
                setOnClickPendingIntent(R.id.widget_root, WidgetData.openApp(context))
            }
            manager.updateAppWidget(id, views)
        }
    }
}
