package com.whocalled.android.util

import android.content.res.Resources
import com.whocalled.android.R
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Human-friendly relative time ("à l'instant", "il y a 5 min", "il y a 2 h",
 * "hier", "il y a 3 j"), falling back to an absolute date for anything older
 * than a week. Centralised so every screen shows dates the same way.
 *
 * The [Resources] overload is localized (fr default, en, …); the legacy
 * French-only overload remains for screens not yet migrated to resources.
 */
object RelativeTime {
    fun format(res: Resources, timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - timestamp
        if (diff < 0) return res.getString(R.string.reltime_now)
        val minutes = diff / 60_000L
        val hours = diff / 3_600_000L
        val days = diff / 86_400_000L
        val locale = res.configuration.locales[0] ?: Locale.getDefault()
        return when {
            minutes < 1 -> res.getString(R.string.reltime_now)
            minutes < 60 -> res.getString(R.string.reltime_minutes, minutes)
            hours < 24 -> res.getString(R.string.reltime_hours, hours)
            days == 1L -> res.getString(R.string.reltime_yesterday)
            days < 7 -> res.getString(R.string.reltime_days, days)
            else -> DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(timestamp))
        }
    }

    fun format(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - timestamp
        if (diff < 0) return "à l’instant"
        val minutes = diff / 60_000L
        val hours = diff / 3_600_000L
        val days = diff / 86_400_000L
        return when {
            minutes < 1 -> "à l’instant"
            minutes < 60 -> "il y a $minutes min"
            hours < 24 -> "il y a $hours h"
            days == 1L -> "hier"
            days < 7 -> "il y a $days j"
            else -> DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.FRANCE)
                .format(Date(timestamp))
        }
    }
}
