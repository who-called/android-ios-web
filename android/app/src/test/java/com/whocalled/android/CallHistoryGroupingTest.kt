package com.whocalled.android

import com.whocalled.android.data.CallLogEntity
import com.whocalled.android.data.MyReportEntity
import com.whocalled.android.util.CallEvent
import com.whocalled.android.util.CallEventAction
import com.whocalled.android.util.CallDirection
import com.whocalled.android.util.PhoneCall
import com.whocalled.android.util.findRecentCallPrompt
import com.whocalled.android.util.groupCallHistory
import com.whocalled.android.util.mergeCallEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallHistoryGroupingTest {

    private val now = 1_700_000_000_000L // fixed "now" for deterministic buckets
    private val day = 86_400_000L

    private fun event(id: Long, phone: String, ageDays: Long, action: CallEventAction = CallEventAction.BLOCKED) =
        CallEvent(
            key = "filtered:$id",
            callLogId = id,
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
            event(2, "33612345678", ageDays = 2),
            event(1, "33612345678", ageDays = 21),
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
            event(3, "33700000000", ageDays = 0),
            event(2, "33700000000", ageDays = 0),
            event(1, "33700000000", ageDays = 0),
        )
        val sections = groupCallHistory(entries, now)
        val rows = sections.flatMap { it.items }.filter { it.phone == "33700000000" }
        assertEquals(1, rows.size)
        assertEquals(3, rows.first().attempts)
        assertEquals(3L, rows.first().callLogId)
    }

    @Test
    fun todayAndYesterdayAreDistinctSections() {
        val entries = listOf(
            event(2, "33611111111", ageDays = 0),
            event(1, "33622222222", ageDays = 1),
        )
        val sections = groupCallHistory(entries, now)
        assertEquals("Aujourd’hui", sections.first().title)
        assertEquals("Hier", sections[1].title)
    }

    @Test
    fun warnedCallPresentInBothSourcesIsDeduplicated() {
        val filtered = listOf(
            CallLogEntity(
                id = 7,
                phone = "33612345678",
                action = "warned",
                spamScore = 72,
                category = "telemarketing",
                timestamp = now,
            ),
        )
        val system = listOf(
            PhoneCall(
                systemId = 42,
                rawNumber = "+33 6 12 34 56 78",
                normalizedPhone = "33612345678",
                timestamp = now + 5_000,
                type = 1,
            ),
        )

        val merged = mergeCallEvents(filtered, system)

        assertEquals(1, merged.size)
        assertEquals(CallEventAction.WARNED, merged.single().action)
        assertEquals(7L, merged.single().callLogId)
    }

    @Test
    fun fullHistoryKeepsUnknownContactsAndOutgoingCalls() {
        val system = listOf(
            PhoneCall(1, "+33611111111", "33611111111", now, 3),
            PhoneCall(2, "+33622222222", "33622222222", now - 1_000, 1, contactName = "Alice"),
            PhoneCall(3, "+33633333333", "33633333333", now - 2_000, 2),
            PhoneCall(4, "+33644444444", "33644444444", now - 3_000, 6),
        )

        val merged = mergeCallEvents(emptyList(), system)

        assertEquals(4, merged.size)
        assertEquals(CallEventAction.UNKNOWN, merged.first { it.phone == "33611111111" }.action)
        assertEquals(CallEventAction.CONTACT, merged.first { it.phone == "33622222222" }.action)
        assertEquals(CallDirection.OUTGOING, merged.first { it.phone == "33633333333" }.direction)
        assertEquals(CallEventAction.BLOCKED, merged.first { it.phone == "33644444444" }.action)
    }

    @Test
    fun personalLegitimateVoteDecoratesRecentCall() {
        val system = listOf(PhoneCall(1, "+33611111111", "33611111111", now, 3))
        val reports = listOf(
            MyReportEntity(
                phone = "33611111111",
                vote = "legit",
                category = null,
                createdAt = now,
                updatedAt = now,
                syncState = "synced",
            ),
        )

        val merged = mergeCallEvents(emptyList(), system, reports)

        assertEquals(CallEventAction.LEGITIMATE, merged.single().action)
        assertEquals(null, findRecentCallPrompt(merged, handledAt = 0, now = now))
    }

    @Test
    fun recentPromptRespectsHandledTimestampAnd24HourLimit() {
        val recent = event(3, "33611111111", ageDays = 0)
        val old = event(2, "33622222222", ageDays = 2)

        assertEquals(recent, findRecentCallPrompt(listOf(old, recent), handledAt = 0, now = now))
        assertEquals(null, findRecentCallPrompt(listOf(recent), handledAt = recent.timestamp, now = now))
        assertEquals(null, findRecentCallPrompt(listOf(old), handledAt = 0, now = now))
    }
}
