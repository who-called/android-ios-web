package com.whocalled.android.util

import android.content.Context
import com.whocalled.android.R
import java.text.DateFormat
import java.util.Date

/**
 * Human-friendly relative time ("à l'instant", "il y a 5 min", "il y a 2 h",
 * "hier", "il y a 3 j" in French), falling back to an absolute date for anything
 * older than a week. Centralised so every screen shows dates the same way. Wording
 * comes from string resources and the absolute date follows the resources' locale,
 * so it always matches the language the rest of the UI is shown in.
 */
object RelativeTime {
    fun format(context: Context, timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - timestamp
        if (diff < 0) return context.getString(R.string.time_just_now)
        val minutes = diff / 60_000L
        val hours = diff / 3_600_000L
        val days = diff / 86_400_000L
        return when {
            minutes < 1 -> context.getString(R.string.time_just_now)
            minutes < 60 -> context.getString(R.string.time_minutes_ago, minutes)
            hours < 24 -> context.getString(R.string.time_hours_ago, hours)
            days == 1L -> context.getString(R.string.time_yesterday)
            days < 7 -> context.getString(R.string.time_days_ago, days)
            else -> DateFormat.getDateInstance(DateFormat.MEDIUM, UiLocale.of(context))
                .format(Date(timestamp))
        }
    }
}
