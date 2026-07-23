package com.whocalled.android.data

import com.whocalled.android.BuildConfig

/**
 * Seeds the local Room cache with mock data in DEBUG builds, so the whole UI
 * (history, my reports, scored numbers) is visible without real calls or server.
 * No-op in release.
 */
object DebugSeeder {

    suspend fun seedIfNeeded(db: WhoCalledDatabase) {
        if (!BuildConfig.DEBUG) return
        if (db.scoredNumberDao().count() > 0) return

        val now = System.currentTimeMillis()
        val hour = 3_600_000L

        db.scoredNumberDao().upsertAll(
            listOf(
                ScoredNumberEntity("33899123456", "block", 100, "telemarketing", now),
                ScoredNumberEntity("33162987654", "block", 92, "robocall", now),
                ScoredNumberEntity("33970650000", "warn", 68, "unknown", now),
                ScoredNumberEntity("33812345678", "warn", 74, "telemarketing", now),
            ),
        )

        db.callLogDao().insertAll(
            listOf(
                // Blocked because it matches the official ARCEP pattern 33899###### (no community reports).
                CallLogEntity(phone = "33899654321", action = "blocked", spamScore = 100, category = "telemarketing", timestamp = now - 30 * 60_000),
                CallLogEntity(phone = "33899123456", action = "blocked", spamScore = 100, category = "telemarketing", timestamp = now - hour),
                CallLogEntity(phone = "33162987654", action = "blocked", spamScore = 92, category = "robocall", timestamp = now - 2 * hour),
                CallLogEntity(phone = "33970650000", action = "warned", spamScore = 68, category = "scam", timestamp = now - 5 * hour),
            ),
        )

        db.myReportDao().upsert(
            MyReportEntity("33899123456", "spam", "telemarketing", now - hour, now - hour, "synced"),
        )
        db.myReportDao().upsert(
            MyReportEntity("33600112233", "spam", null, now - 3 * hour, now - 3 * hour, "failed"),
        )

        // ARCEP wildcard patterns (so the CallScreening matcher can be tested offline).
        db.patternDao().upsertAll(
            listOf(
                PatternEntity("33899######", "block", "telemarketing", "arcep", "Démarchage ARCEP", now),
                PatternEntity("33162######", "warn", "telemarketing", "arcep", "Numéros opérateurs ARCEP", now),
            ),
        )
    }
}
