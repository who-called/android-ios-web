package com.whocalled.android.util

import com.whocalled.android.data.CallLogEntity
import java.util.Calendar
import kotlin.math.abs

/**
 * A call event shown by Who Called. Filtered events come from our Room journal;
 * unknown allowed/missed calls come from Android's system call log.
 */
data class CallEvent(
    val key: String,
    val callLogId: Long?,
    val phone: String,
    val action: CallEventAction,
    val spamScore: Int,
    val category: String,
    val timestamp: Long,
    val attempts: Int = 1,
)

enum class CallEventAction {
    BLOCKED,
    WARNED,
    UNKNOWN,
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

private const val DUPLICATE_WINDOW_MS = 2 * 60_000L
private const val RECENT_CALL_MAX_AGE_MS = 24 * 60 * 60 * 1_000L

// CallLog.Calls values kept here so this merge stays a plain JVM-testable function.
private val incomingCallTypes = setOf(1, 3, 5, 6) // incoming, missed, rejected, blocked

/**
 * Merge the app's filtered-call journal with Android's system call log.
 *
 * WARN calls normally exist in both sources. Calls close in time with the same
 * normalized number collapse into the richer Room event. Contacts and outgoing
 * calls are intentionally excluded: this screen is about unknown/filtered calls,
 * not a replacement for the system Phone app.
 */
fun mergeCallEvents(
    filteredCalls: List<CallLogEntity>,
    systemCalls: List<PhoneCall>,
): List<CallEvent> {
    val filtered = filteredCalls.sortedByDescending { it.timestamp }
    val unmatchedFiltered = filtered.indices.toMutableSet()
    val result = filtered.map { call ->
        CallEvent(
            key = "filtered:${call.id}",
            callLogId = call.id,
            phone = call.phone,
            action = if (call.action == "blocked") CallEventAction.BLOCKED else CallEventAction.WARNED,
            spamScore = call.spamScore,
            category = call.category,
            timestamp = call.timestamp,
        )
    }.toMutableList()

    systemCalls
        .asSequence()
        .filter { it.type in incomingCallTypes }
        .filterNot { it.isContact }
        .forEach { call ->
            val phone = call.normalizedPhone ?: return@forEach
            val duplicate = unmatchedFiltered
                .asSequence()
                .filter { filtered[it].phone == phone }
                .filter { abs(filtered[it].timestamp - call.timestamp) <= DUPLICATE_WINDOW_MS }
                .minByOrNull { abs(filtered[it].timestamp - call.timestamp) }

            if (duplicate != null) {
                unmatchedFiltered.remove(duplicate)
            } else {
                result += CallEvent(
                    key = "system:${call.systemId}",
                    callLogId = null,
                    phone = phone,
                    action = CallEventAction.UNKNOWN,
                    spamScore = 0,
                    category = "unknown",
                    timestamp = call.timestamp,
                )
            }
        }

    return result.sortedByDescending { it.timestamp }
}

/** Newest event eligible for the Home prompt, bounded to the last 24 hours. */
fun findRecentCallPrompt(
    events: List<CallEvent>,
    handledAt: Long,
    now: Long = System.currentTimeMillis(),
): CallEvent? {
    val cutoff = now - RECENT_CALL_MAX_AGE_MS
    return events
        .asSequence()
        .filter { it.timestamp > handledAt && it.timestamp >= cutoff }
        .maxByOrNull { it.timestamp }
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
