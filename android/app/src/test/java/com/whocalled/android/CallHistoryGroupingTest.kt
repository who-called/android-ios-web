package com.whocalled.android

import com.whocalled.android.data.CallLogEntity
import com.whocalled.android.data.MyReportEntity
import com.whocalled.android.util.CallEvent
import com.whocalled.android.util.CallEventAction
import com.whocalled.android.util.CallDirection
import com.whocalled.android.util.buildCallEvents
import com.whocalled.android.util.buildRecentCallPrompt
import com.whocalled.android.util.findRecentCallPrompt
import com.whocalled.android.util.groupCallHistory
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

    private fun journal(id: Long, phone: String, action: String, ageMs: Long = 0) =
        CallLogEntity(id = id, phone = phone, action = action, spamScore = if (action == "allowed") 0 else 80,
            category = "telemarketing", timestamp = now - ageMs)

    private fun vote(phone: String, vote: String) =
        MyReportEntity(phone = phone, vote = vote, category = null, createdAt = now, updatedAt = now, syncState = "synced")

    @Test
    fun journalVerdictsMapToActionsAndDirections() {
        val events = buildCallEvents(
            listOf(
                journal(1, "33611111111", "blocked"),
                journal(2, "33622222222", "warned", ageMs = 1_000),
                journal(3, "33633333333", "allowed", ageMs = 2_000),
            ),
        )

        assertEquals(3, events.size)
        assertEquals(CallEventAction.BLOCKED, events[0].action)
        assertEquals(CallDirection.BLOCKED, events[0].direction)
        assertEquals(CallEventAction.WARNED, events[1].action)
        assertEquals(CallEventAction.UNKNOWN, events[2].action)
        assertEquals(CallDirection.INCOMING, events[2].direction)
        assertEquals(3L, events[2].callLogId)
    }

    @Test
    fun allowedUnknownCallHeadlinesHome() {
        val events = buildCallEvents(listOf(journal(1, "33611111111", "allowed")))
        assertEquals(events.single(), findRecentCallPrompt(events, handledAt = 0, now = now))
    }

    @Test
    fun personalVotesDecorateAllowedCallsOnly() {
        val events = buildCallEvents(
            listOf(
                journal(1, "33611111111", "allowed"),
                journal(2, "33622222222", "allowed", ageMs = 1_000),
                journal(3, "33633333333", "blocked", ageMs = 2_000),
            ),
            listOf(vote("33611111111", "legit"), vote("33622222222", "spam"), vote("33633333333", "legit")),
        )

        assertEquals(CallEventAction.LEGITIMATE, events.first { it.phone == "33611111111" }.action)
        assertEquals(CallEventAction.REPORTED_SPAM, events.first { it.phone == "33622222222" }.action)
        // The filter's verdict is what happened to the call; a vote does not rewrite it.
        assertEquals(CallEventAction.BLOCKED, events.first { it.phone == "33633333333" }.action)
    }

    @Test
    fun legitimateCallNeverHeadlinesHome() {
        val events = buildCallEvents(listOf(journal(1, "33611111111", "allowed")), listOf(vote("33611111111", "legit")))
        assertEquals(null, findRecentCallPrompt(events, handledAt = 0, now = now))
    }

    @Test
    fun recentPromptRespectsHandledTimestampAnd24HourLimit() {
        val recent = event(3, "33611111111", ageDays = 0)
        val old = event(2, "33622222222", ageDays = 2)

        assertEquals(recent, findRecentCallPrompt(listOf(old, recent), handledAt = 0, now = now))
        assertEquals(null, findRecentCallPrompt(listOf(recent), handledAt = recent.timestamp, now = now))
        assertEquals(null, findRecentCallPrompt(listOf(old), handledAt = 0, now = now))
    }

    @Test
    fun seenCallOnlyReHeadlinesWhileFresh() {
        val hour = 3_600_000L
        val fresh = event(4, "33611111111", ageDays = 0).copy(timestamp = now - 30 * 60_000L)
        val staleSeen = event(5, "33622222222", ageDays = 0).copy(timestamp = now - 3 * hour)

        // Seen in a previous session but < 1 h old → still headlines.
        assertEquals(
            fresh,
            findRecentCallPrompt(listOf(fresh), handledAt = 0, seenAt = fresh.timestamp, now = now),
        )
        // Seen and no longer fresh → the Calls tab is its home now.
        assertEquals(
            null,
            findRecentCallPrompt(listOf(staleSeen), handledAt = 0, seenAt = staleSeen.timestamp, now = now),
        )
        // A newer call always resets the stage, whatever was seen before.
        assertEquals(
            fresh,
            findRecentCallPrompt(listOf(staleSeen, fresh), handledAt = 0, seenAt = staleSeen.timestamp, now = now),
        )
    }

    @Test
    fun promptCountsTheRestOfTheUnhandledBacklog() {
        val newest = event(6, "33611111111", ageDays = 0)
        val other = event(7, "33622222222", ageDays = 0).copy(timestamp = now - 2 * 3_600_000L)
        val tooOld = event(8, "33633333333", ageDays = 2)

        val prompt = buildRecentCallPrompt(listOf(newest, other, tooOld), handledAt = 0, now = now)

        assertEquals(newest, prompt?.call)
        assertEquals(1, prompt?.others)
    }
}
