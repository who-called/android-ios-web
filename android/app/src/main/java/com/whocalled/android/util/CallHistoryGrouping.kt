package com.whocalled.android.util

import com.whocalled.android.data.CallLogEntity
import java.util.Calendar

/**
 * One displayable history row. When the same number is filtered several times
 * within the same day section, the entries collapse into a single row carrying
 * the attempt count and the most recent event (so the list stays readable even
 * with dozens of blocked calls). Distinct days stay distinct — a number blocked
 * 3 weeks ago and again 2 days ago shows up in two different sections.
 */
data class CallHistoryItem(
    val representativeId: Long, // id of the most recent entry (for the detail screen)
    val phone: String,
    val action: String, // blocked | warned (most recent)
    val spamScore: Int,
    val category: String,
    val timestamp: Long, // most recent occurrence
    val attempts: Int, // how many times within this day section
)

/** A titled section of the history ("Aujourd'hui", "Hier", …) with its rows. */
data class CallHistorySection(
    val title: String,
    val items: List<CallHistoryItem>,
)

/** Time buckets, coarsened the further back we go (Phone-app style). */
private enum class Bucket(val title: String) {
    TODAY("Aujourd’hui"),
    YESTERDAY("Hier"),
    THIS_WEEK("Cette semaine"),
    THIS_MONTH("Ce mois-ci"),
    OLDER("Plus ancien"),
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
 * Group a flat, timestamp-DESC call log into sections. Within each calendar day,
 * repeated calls from the same number collapse into one row with an attempt
 * count; the representative row keeps the most recent occurrence.
 */
fun groupCallHistory(
    entries: List<CallLogEntity>,
    now: Long = System.currentTimeMillis(),
): List<CallHistorySection> {
    if (entries.isEmpty()) return emptyList()

    // Collapse same-number repeats inside the same day, preserving order.
    val collapsed = LinkedHashMap<Pair<Long, String>, CallHistoryItem>()
    for (e in entries) {
        val key = dayKey(e.timestamp) to e.phone
        val existing = collapsed[key]
        if (existing == null) {
            collapsed[key] = CallHistoryItem(
                representativeId = e.id,
                phone = e.phone,
                action = e.action,
                spamScore = e.spamScore,
                category = e.category,
                timestamp = e.timestamp,
                attempts = 1,
            )
        } else {
            // entries are DESC, so the first seen is already the most recent.
            collapsed[key] = existing.copy(attempts = existing.attempts + 1)
        }
    }

    // Bucket the collapsed rows, keeping DESC order within each section.
    val sections = LinkedHashMap<Bucket, MutableList<CallHistoryItem>>()
    for (item in collapsed.values) {
        val bucket = bucketOf(item.timestamp, now)
        sections.getOrPut(bucket) { mutableListOf() }.add(item)
    }

    // Emit in chronological bucket order.
    return Bucket.entries
        .mapNotNull { b -> sections[b]?.let { CallHistorySection(b.title, it) } }
}
