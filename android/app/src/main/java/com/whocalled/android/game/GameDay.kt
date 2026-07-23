package com.whocalled.android.game

import java.time.LocalDate

/** Shared daily-challenge day helper (epoch-day, local midnight). */
object GameDay {
    fun epochDay(date: LocalDate = LocalDate.now()): Long = date.toEpochDay()
}
