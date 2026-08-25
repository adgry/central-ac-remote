package com.hvacpanel.control

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 定时关机, scheduled with the system so it still fires after the app is swiped
 * away. Deliberately an inexact alarm: nobody minds a bedroom unit stopping at
 * 23:04 instead of 23:00, and exact alarms would mean asking for a permission
 * the feature does not need.
 */
object OffTimer {

    const val EXTRA_UNIT_ID = "unit_id"
    private const val ACTION = "com.hvacpanel.OFF_TIMER"

    /** The choices the control screen offers. */
    val PRESETS_MINUTES = listOf(30, 60, 120, 180, 480)

    fun humanise(minutes: Int): String = when {
        minutes < 60 -> "$minutes 分钟"
        minutes % 60 == 0 -> "${minutes / 60} 小时"
        else -> "${minutes / 60} 小时 ${minutes % 60} 分"
    }

    private fun intentFor(context: Context, unitId: String): PendingIntent {
        val intent = Intent(context, TimerReceiver::class.java).apply {
            action = ACTION
            putExtra(EXTRA_UNIT_ID, unitId)
            // The action alone is not distinct per unit; the data URI is.
            data = android.net.Uri.parse("hvacpanel://off/$unitId")
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, unitId.hashCode(), intent, flags)
    }

    fun schedule(context: Context, unitId: String, minutes: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val at = System.currentTimeMillis() + minutes * 60_000L
        val pi = intentFor(context, unitId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.set(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(context: Context, unitId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(intentFor(context, unitId))
    }
}
