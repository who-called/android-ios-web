package com.whocalled.android.util

import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Human-friendly French relative time ("à l'instant", "il y a 5 min",
 * "il y a 2 h", "hier", "il y a 3 j"), falling back to an absolute FR date for
 * anything older than a week. Centralised so every screen shows dates the same
 * way — and never in the device's English locale.
 */
object RelativeTime {
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
