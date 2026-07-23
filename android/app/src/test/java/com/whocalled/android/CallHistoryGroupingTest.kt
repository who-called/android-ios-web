package com.whocalled.android

import com.whocalled.android.data.CallLogEntity
import com.whocalled.android.util.groupCallHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallHistoryGroupingTest {

    private val now = 1_700_000_000_000L // fixed "now" for deterministic buckets
    private val day = 86_400_000L

    private fun entry(id: Long, phone: String, ageDays: Long, action: String = "blocked") =
        CallLogEntity(
            id = id,
            phone = phone,
            action = action,
            spamScore = 90,
            category = "telemarketing",
            timestamp = now - ageDays * day,
        )

    @Test
    fun emptyLogProducesNoSections() {
        assertTrue(groupCallHistory(emptyList(), now).isEmpty())
    }

    @Test
    fun sameNumberOnDifferentDaysStaysInSeparateSections() {
        // User scenario: blocked 3 weeks ago, then again 2 days ago.
        val entries = listOf(
            entry(2, "33612345678", ageDays = 2),
            entry(1, "33612345678", ageDays = 21),
        )
        val sections = groupCallHistory(entries, now)

        // Two distinct time sections, each with one row for the number.
        val rowsForNumber = sections.flatMap { it.items }.filter { it.phone == "33612345678" }
        assertEquals(2, rowsForNumber.size)
        // Neither is collapsed: each is a single attempt on its own day.
        assertTrue(rowsForNumber.all { it.attempts == 1 })
    }

    @Test
    fun sameNumberSameDayCollapsesWithAttemptCount() {
        val entries = listOf(
            entry(3, "33700000000", ageDays = 0),
            entry(2, "33700000000", ageDays = 0),
            entry(1, "33700000000", ageDays = 0),
        )
        val sections = groupCallHistory(entries, now)
        val rows = sections.flatMap { it.items }.filter { it.phone == "33700000000" }
        assertEquals(1, rows.size)
        assertEquals(3, rows.first().attempts)
        // Representative keeps the most recent entry id.
        assertEquals(3L, rows.first().representativeId)
    }

    @Test
    fun todayAndYesterdayAreDistinctSections() {
        val entries = listOf(
            entry(2, "33611111111", ageDays = 0),
            entry(1, "33622222222", ageDays = 1),
        )
        val sections = groupCallHistory(entries, now)
        assertEquals("Aujourd’hui", sections.first().title)
        assertEquals("Hier", sections[1].title)
    }
}
