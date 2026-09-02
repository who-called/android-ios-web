package com.whocalled.android.data

import android.content.Context
import android.util.Log
import com.whocalled.android.network.ApiException
import com.whocalled.android.network.ListNumber
import com.whocalled.android.network.LookupResponse
import com.whocalled.android.network.ReportRequest
import com.whocalled.android.network.WhoCalledApi
import com.whocalled.android.util.PhoneNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Typed errors surfaced to the UI for friendly messages. */
sealed class RepoError(message: String) : Exception(message) {
    object InvalidPhone : RepoError("Numéro invalide.")
    object Network : RepoError("Pas de connexion. Réessayez.")
    data class Server(val code: Int) : RepoError("Erreur serveur ($code).")
    data class Unknown(val error: Throwable) : RepoError(error.message ?: "Erreur inconnue.")
}

/**
 * Single entry point for data: local Room cache + remote who-called API.
 * All network paths catch and map errors to [RepoError] so the UI can react.
 */
class WhoCalledRepository(
    private val context: Context,
    private val api: WhoCalledApi = WhoCalledApi(),
    private val db: WhoCalledDatabase = WhoCalledDatabase.get(context),
) {
    private companion object {
        const val TAG = "WhoCalledRepo"
    }

    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // MARK: - List sync

    /**
     * Download new/changed scored numbers and update the local cache.
     * `onProgress(count)` is called after each page so the UI can show live
     * progress (the loop has no upfront total — the UI derives % from /stats).
     */
    suspend fun syncList(onProgress: ((Int) -> Unit)? = null): Result<Int> = guarded {
        val lastSync = Preferences.lastSyncAt(context)
        val since = if (lastSync > 0L) iso.format(lastSync) else null
        val country = Preferences.countryDial(context) // "" = worldwide

        var total = 0
        var cursor = since
        // Short-lived access token (see /lists/access). Refreshed once on 401 —
        // a long paginated sync can outlive the token's TTL.
        var token = api.listsAccess().token
        do {
            val response = try {
                api.fetchList(cursor, country, token = token)
            } catch (e: ApiException) {
                if (e.code != 401) throw e
                token = api.listsAccess().token
                api.fetchList(cursor, country, token = token)
            }
            if (response.numbers.isNotEmpty()) {
                db.scoredNumberDao().upsertAll(response.numbers.map { it.toEntity() })
                total += response.numbers.size
            }
            if (response.patterns.isNotEmpty()) {
                db.patternDao().upsertAll(response.patterns.map { it.toEntity() })
                total += response.patterns.size
            }
            // Drop numbers that healed/were removed upstream so the single local
            // cache stays in sync with the consolidated server DB.
            if (response.deleted.isNotEmpty()) {
                response.deleted.chunked(900).forEach { db.scoredNumberDao().deleteByPhones(it) }
            }
            onProgress?.invoke(total)
            cursor = response.next
        } while (cursor != null)

        Preferences.setLastSyncAt(context, System.currentTimeMillis())
        Log.d(TAG, "Synced $total numbers")
        total
    }

    /**
     * Change the country scope: persist it, wipe the now-out-of-scope cache and
     * reset the sync cursor so the next sync re-downloads the new scope fully.
     */
    suspend fun setCountry(dial: String): Result<Unit> = guarded {
        Preferences.setCountryDial(context, dial)
        db.scoredNumberDao().clear()
        Preferences.setLastSyncAt(context, 0L)
    }

    // MARK: - Lookup (detail screen)

    /** Fetch enriched stats for one number from the server. */
    suspend fun lookup(phone: String): Result<LookupResponse> = guarded {
        api.lookup(phone, Preferences.deviceId(context))
    }

    /** Fetch the "numéros qui montent" trending list. */
    suspend fun trending(windowDays: Int = 7): Result<List<com.whocalled.android.network.TrendingNumber>> =
        guarded { api.trending(windowDays).numbers }

    // MARK: - Diagnostics & stats

    /** Connectivity probe for the Settings "mode test" → latency (ms) on success. */
    suspend fun pingApi(): Result<Long> = guarded { api.ping() }

    /** Public reassurance stats for the Home banner. */
    suspend fun fetchStats(): Result<com.whocalled.android.network.StatsResponse> =
        guarded { api.stats(Preferences.countryDial(context)) }

    suspend fun submitGameScore(game: String, score: Int, waves: Int): Result<com.whocalled.android.network.GameScoreResponse> =
        guarded {
            api.submitGameScore(
                Preferences.deviceId(context), score, waves,
                com.whocalled.android.game.GameDay.epochDay(), game,
            )
        }

    suspend fun gameLeaderboard(game: String, period: String, day: Long): Result<com.whocalled.android.network.LeaderboardResponse> =
        guarded { api.gameLeaderboard(period, day, Preferences.deviceId(context), game) }

    suspend fun localGameHistory(game: String): List<Preferences.LocalDay> = Preferences.localHistory(context, game)

    suspend fun community(): Result<com.whocalled.android.network.CommunityResponse> =
        guarded { api.community() }

    suspend fun gameHistory(game: String, days: Int = 14): Result<com.whocalled.android.network.HistoryResponse> =
        guarded { api.gameHistory(Preferences.deviceId(context), days, game) }

    /** TRACE: the day's 5-grid sprint (identical for all players). */
    suspend fun fetchTracePuzzle(day: Long): Result<com.whocalled.android.game.TracePuzzleSet> =
        guarded { api.tracePuzzle(day) }

    /** TRACE: a single unranked training grid of the requested size. */
    suspend fun fetchTraceTraining(size: Int, seed: Long): Result<com.whocalled.android.game.TracePuzzleSet> =
        guarded { api.traceTrainingPuzzle(size, seed) }

    // MARK: - My reports (local source of truth + best-effort sync)

    fun observeMyReports() = db.myReportDao().observeAll()

    /**
     * Create or update a report. Stored locally immediately (so the user always
     * sees it and can change their mind), then pushed to the API best-effort.
     */
    suspend fun submitReport(
        rawPhone: String,
        isSpam: Boolean,
        category: String? = null,
    ): Result<Unit> = guarded {
        val phone = PhoneNormalizer.normalize(rawPhone) ?: throw RepoError.InvalidPhone
        val vote = if (isSpam) "spam" else "legit"
        val now = System.currentTimeMillis()

        // The REPLACE upsert would wipe what we knew: keep the original
        // category and creation date across vote flips.
        val previous = db.myReportDao().byPhone(phone)
        val keptCategory = category ?: previous?.category
        val createdAt = previous?.createdAt ?: now

        // Persist locally first (pending).
        db.myReportDao().upsert(
            MyReportEntity(phone, vote, keptCategory, createdAt, now, syncState = "pending"),
        )

        pushReport(phone, vote, keptCategory, createdAt)
    }

    /** Re-send any reports that failed to sync. */
    suspend fun retryPendingReports(): Result<Int> = guarded {
        val pending = db.myReportDao().pending()
        var ok = 0
        for (r in pending) {
            runCatching { pushReport(r.phone, r.vote, r.category, r.createdAt) }
                .onSuccess { ok += 1 }
        }
        ok
    }

    /**
     * Delete one of the user's reports. Locally removed; if a SPAM vote had
     * actually reached the server, we also send a corrective "legit" vote so
     * the crowd score isn't stuck on a mistake. An unsynced or already-legit
     * report gets no corrective — it would create a vote out of thin air.
     */
    suspend fun deleteMyReport(phone: String): Result<Unit> = guarded {
        val existing = db.myReportDao().byPhone(phone)
        db.myReportDao().deleteByPhone(phone)
        if (existing?.vote == "spam" && existing.syncState == "synced") {
            // Best-effort corrective vote (ignored if offline).
            runCatching {
                api.report(ReportRequest(phone, Preferences.deviceId(context), "legit", null))
            }
        }
        Unit
    }

    private suspend fun pushReport(phone: String, vote: String, category: String?, createdAt: Long) {
        val now = System.currentTimeMillis()
        try {
            // A "legit" vote carries no category — the local row still keeps
            // it so a later flip back to spam doesn't lose the reason.
            api.report(
                ReportRequest(
                    phone,
                    Preferences.deviceId(context),
                    vote,
                    if (vote == "spam") category else null,
                ),
            )
            db.myReportDao().upsert(
                MyReportEntity(phone, vote, category, createdAt, now, syncState = "synced"),
            )
        } catch (e: Exception) {
            db.myReportDao().upsert(
                MyReportEntity(phone, vote, category, createdAt, now, syncState = "failed"),
            )
            throw e
        }
    }

    // MARK: - Privacy (RGPD)

    /** Erase all data this device sent to the server, and clear local reports. */
    suspend fun eraseMyData(): Result<Unit> = guarded {
        api.eraseDevice(Preferences.deviceId(context))
        // Clear ALL local copies — a synced row left behind would show as
        // "envoyé" for a report the server just deleted.
        db.myReportDao().clear()
        Unit
    }

    // MARK: - User rules

    suspend fun allow(rawPhone: String) {
        val phone = PhoneNormalizer.normalize(rawPhone) ?: return
        db.userRuleDao().upsert(UserRuleEntity(phone, "allow", System.currentTimeMillis()))
    }

    suspend fun block(rawPhone: String) {
        val phone = PhoneNormalizer.normalize(rawPhone) ?: return
        db.userRuleDao().upsert(UserRuleEntity(phone, "block", System.currentTimeMillis()))
    }

    // MARK: - Helpers

    /**
     * Runs a block on the IO dispatcher (network I/O must never touch the main
     * thread — that throws NetworkOnMainThreadException), mapping any thrown
     * exception to a typed [RepoError].
     */
    private suspend fun <T> guarded(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(block())
            } catch (e: RepoError) {
                Result.failure(e)
            } catch (e: ApiException) {
                Result.failure(RepoError.Server(e.code))
            } catch (e: IOException) {
                Log.w(TAG, "Network error", e)
                Result.failure(RepoError.Network)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error", e)
                Result.failure(RepoError.Unknown(e))
            }
        }

    private fun ListNumber.toEntity() = ScoredNumberEntity(
        phone = phone,
        status = status,
        spamScore = spamScore,
        category = category,
        updatedAt = parseIso(updatedAt),
    )

    private fun com.whocalled.android.network.ListPattern.toEntity() = PatternEntity(
        pattern = pattern,
        status = status,
        category = category,
        source = source,
        name = name,
        updatedAt = parseIso(updatedAt),
    )

    private fun parseIso(s: String): Long =
        runCatching { iso.parse(s)?.time ?: 0L }.getOrDefault(0L)
}
