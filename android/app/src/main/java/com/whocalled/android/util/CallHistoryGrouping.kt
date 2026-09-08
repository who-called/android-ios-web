package com.whocalled.android.util

import com.whocalled.android.data.CallLogEntity
import com.whocalled.android.data.MyReportEntity
import java.util.Calendar

/**
 * A call event shown by Who Called. Every event comes from our own Room
 * journal: the screening service records each incoming call it sees (blocked,
 * warned or allowed). The app never reads the phone's system call log.
 */
data class CallEvent(
    val key: String,
    val callLogId: Long?,
    val phone: String,
    val action: CallEventAction,
    val spamScore: Int,
    val category: String,
    val timestamp: Long,
    val direction: CallDirection = CallDirection.INCOMING,
    val attempts: Int = 1,
)

enum class CallEventAction {
    BLOCKED,
    WARNED,
    REPORTED_SPAM,
    LEGITIMATE,
    UNKNOWN,
}

enum class CallDirection {
    INCOMING,
    BLOCKED,
}

/** A titled section of the calls screen ("Aujourd'hui", "Hier", …). */
data class CallHistorySection(
    val title: String,
    val items: List<CallEvent>,
)

/** Time buckets, coarsened the further back we go (Phone-app style). */
private enum class Bucket(val title: String) {
    TODAY("Aujourd’hui"),
    YESTERDAY("Hier"),
    THIS_WEEK("Cette semaine"),
    THIS_MONTH("Ce mois-ci"),
    OLDER("Plus ancien"),
}

private const val RECENT_CALL_MAX_AGE_MS = 24 * 60 * 60 * 1_000L

/** A call this fresh keeps headlining Home even if a past session showed it. */
private const val RECENT_CALL_FRESH_MS = 60 * 60 * 1_000L

/**
 * Turn the screening journal into display events, decorated with the user's
 * own votes. Blocked/warned calls keep their filtering verdict; allowed calls
 * become "reported spam" / "legitimate" when the user already voted, otherwise
 * "unknown" — the ones worth a look.
 */
fun buildCallEvents(
    journal: List<CallLogEntity>,
    myReports: List<MyReportEntity> = emptyList(),
): List<CallEvent> {
    val voteByPhone = myReports.associate { it.phone to it.vote }
    return journal
        .sortedByDescending { it.timestamp }
        .map { call ->
            val action = when (call.action) {
                "blocked" -> CallEventAction.BLOCKED
                "warned" -> CallEventAction.WARNED
                else -> when (voteByPhone[call.phone]) {
                    "spam" -> CallEventAction.REPORTED_SPAM
                    "legit" -> CallEventAction.LEGITIMATE
                    else -> CallEventAction.UNKNOWN
                }
            }
            CallEvent(
                key = "filtered:${call.id}",
                callLogId = call.id,
                phone = call.phone,
                action = action,
                spamScore = call.spamScore,
                category = call.category,
                timestamp = call.timestamp,
                direction = if (call.action == "blocked") CallDirection.BLOCKED else CallDirection.INCOMING,
            )
        }
}

/** The Home headline: the newest call worth triaging, plus how many more wait. */
data class RecentCallPrompt(
    val call: CallEvent,
    /** Other still-unhandled calls of the last 24 h (they live in the Calls tab). */
    val others: Int,
)

/**
 * Newest event eligible for the Home prompt, bounded to the last 24 hours.
 *
 * Two decays keep the card honest news rather than a sticky banner:
 *  - [handledAt]: the user acted (opened/dismissed) — gone for good;
 *  - [seenAt]: a previous session displayed it and the user moved on — it only
 *    re-headlines while still fresh (< 1 h old); afterwards the Calls tab is
 *    its home. A brand-new call always resets the stage.
 */
fun findRecentCallPrompt(
    events: List<CallEvent>,
    handledAt: Long,
    seenAt: Long = 0L,
    now: Long = System.currentTimeMillis(),
): CallEvent? {
    val cutoff = now - RECENT_CALL_MAX_AGE_MS
    val freshCutoff = now - RECENT_CALL_FRESH_MS
    return events
        .asSequence()
        .filter { it.action != CallEventAction.LEGITIMATE }
        .filter { it.timestamp > handledAt && it.timestamp >= cutoff }
        .filter { it.timestamp > seenAt || it.timestamp >= freshCutoff }
        .maxByOrNull { it.timestamp }
}

/** [findRecentCallPrompt] plus the count of other unhandled calls behind it. */
fun buildRecentCallPrompt(
    events: List<CallEvent>,
    handledAt: Long,
    seenAt: Long = 0L,
    now: Long = System.currentTimeMillis(),
): RecentCallPrompt? {
    val call = findRecentCallPrompt(events, handledAt, seenAt, now) ?: return null
    val cutoff = now - RECENT_CALL_MAX_AGE_MS
    val others = events.count {
        it.key != call.key &&
            it.action != CallEventAction.LEGITIMATE &&
            it.timestamp > handledAt && it.timestamp >= cutoff
    }
    return RecentCallPrompt(call, others)
}

private fun bucketOf(timestamp: Long, now: Long): Bucket {
    val cal = Calendar.getInstance()
    cal.timeInMillis = now
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val startOfToday = cal.timeInMillis
    val startOfYesterday = startOfToday - 86_400_000L
    val startOfWeek = startOfToday - 6 * 86_400_000L
    val startOfMonth = startOfToday - 29 * 86_400_000L
    return when {
        timestamp >= startOfToday -> Bucket.TODAY
        timestamp >= startOfYesterday -> Bucket.YESTERDAY
        timestamp >= startOfWeek -> Bucket.THIS_WEEK
        timestamp >= startOfMonth -> Bucket.THIS_MONTH
        else -> Bucket.OLDER
    }
}

/** Day key (yyyyDDD) used to collapse repeats of the same number within a day. */
private fun dayKey(timestamp: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = timestamp
    return cal.get(Calendar.YEAR) * 1000L + cal.get(Calendar.DAY_OF_YEAR)
}

/**
 * Group merged calls into time sections. Repeated calls from the same number on
 * the same day collapse into one row while preserving the latest event's status.
 */
fun groupCallHistory(
    events: List<CallEvent>,
    now: Long = System.currentTimeMillis(),
): List<CallHistorySection> {
    if (events.isEmpty()) return emptyList()

    val collapsed = LinkedHashMap<Pair<Long, String>, CallEvent>()
    for (event in events.sortedByDescending { it.timestamp }) {
        val key = dayKey(event.timestamp) to event.phone
        val existing = collapsed[key]
        if (existing == null) {
            collapsed[key] = event
        } else {
            collapsed[key] = existing.copy(attempts = existing.attempts + 1)
        }
    }

    val sections = LinkedHashMap<Bucket, MutableList<CallEvent>>()
    for (item in collapsed.values) {
        val bucket = bucketOf(item.timestamp, now)
        sections.getOrPut(bucket) { mutableListOf() }.add(item)
    }

    return Bucket.entries
        .mapNotNull { b -> sections[b]?.let { CallHistorySection(b.title, it) } }
}
