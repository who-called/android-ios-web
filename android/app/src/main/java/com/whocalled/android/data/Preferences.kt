package com.whocalled.android.data

import android.content.Context
import android.provider.Settings
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.util.UUID

// A corrupted prefs file (power loss mid-write, full disk) would otherwise throw
// on EVERY read — including inside the call-screening service, i.e. each
// incoming call — and make the app unlaunchable. Start over with defaults
// instead: nothing here is irreplaceable (the device id is re-derived from
// ANDROID_ID, so even the user's votes stay attached to the same identity).
private val Context.dataStore by preferencesDataStore(
    name = "who-called-prefs",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * App preferences (DataStore).
 *
 * Privacy: `deviceId` is a one-way hash of Android's app-scoped identifier. It
 * survives reinstall for the same signing key/user/device, but is neither a
 * hardware id nor shared with other developers.
 */
object Preferences {
    private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
    private val KEY_FILTERING_ENABLED = booleanPreferencesKey("filtering_enabled")
    private val KEY_WARN_ENABLED = booleanPreferencesKey("warn_enabled")
    private val KEY_BLOCK_THRESHOLD = intPreferencesKey("block_threshold")
    private val KEY_LAST_SYNC = longPreferencesKey("last_sync_at")
    private val KEY_COUNTRY_DIAL = stringPreferencesKey("country_dial")
    private val KEY_COUNTRY_CONFIRMED = booleanPreferencesKey("country_confirmed")

    // Daily games (score-based). Keys are namespaced PER GAME so each game keeps
    // its own streak / attempts / best / history and they never collide. "defense"
    // uses the original un-suffixed keys for backward compatibility with existing
    // installs; new games (e.g. "trace") get a "game_<id>_…" prefix.
    private fun gamePrefix(game: String) = if (game == "defense") "game_" else "game_${game}_"
    private fun kGameLastDay(g: String) = longPreferencesKey(gamePrefix(g) + "last_day")
    private fun kGameStreak(g: String) = intPreferencesKey(gamePrefix(g) + "streak")
    private fun kGameAttempts(g: String) = intPreferencesKey(gamePrefix(g) + "attempts_today")
    private fun kGameLastScore(g: String) = intPreferencesKey(gamePrefix(g) + "last_score")
    private fun kGameLastWave(g: String) = intPreferencesKey(gamePrefix(g) + "last_wave")
    private fun kGameBestToday(g: String) = intPreferencesKey(gamePrefix(g) + "best_today")
    private fun kGameBestAllTime(g: String) = intPreferencesKey(gamePrefix(g) + "best_alltime")
    // Local per-day best history ("day|score|waves"), last 30 days — so the
    // leaderboard is never empty even offline / before the server has data.
    private fun kGameHistory(g: String) = stringSetPreferencesKey(gamePrefix(g) + "history_v1")
    private fun kGameBestStreak(g: String) = intPreferencesKey(gamePrefix(g) + "best_streak")

    /** Ranked attempts allowed per day (extra plays are unranked practice). */
    const val MAX_ATTEMPTS = 3

    /** Max length of a user-written fun notification title (one short line). */
    const val MAX_FUN_TITLE_LEN = 60
    private val KEY_SMS_BLOCKING_ENABLED = booleanPreferencesKey("sms_blocking_enabled")
    private val KEY_BLOCKED_SMS_NOTIFICATION = booleanPreferencesKey("blocked_sms_notification")
    private val KEY_BLOCKED_CALL_NOTIFICATION = booleanPreferencesKey("blocked_call_notification")
    private val KEY_FUN_NOTIFICATIONS = booleanPreferencesKey("fun_notifications")
    private val KEY_FUN_CUSTOM_TITLES = stringSetPreferencesKey("fun_custom_titles")
    private val KEY_GAME_REMINDER = booleanPreferencesKey("game_reminder_enabled")
    private val KEY_TRACE_TUTORIAL_SEEN = booleanPreferencesKey("trace_tutorial_seen")
    private val KEY_NOTIF_PROMPT_SEEN = booleanPreferencesKey("notif_prompt_seen")
    private val KEY_SHIELD_SETUP_COMPLETED = booleanPreferencesKey("shield_setup_completed")
    private val KEY_REMINDER_IGNORED = intPreferencesKey("game_reminder_ignored_count")
    private val KEY_REMINDER_SNOOZED_UNTIL = longPreferencesKey("game_reminder_snoozed_until_day")
    private val KEY_REMINDER_LAST_SHOWN_DAY = longPreferencesKey("game_reminder_last_shown_day")
    private val KEY_RECENT_CALL_HANDLED_AT = longPreferencesKey("recent_call_handled_at")
    private val KEY_RECENT_CALL_SEEN_AT = longPreferencesKey("recent_call_seen_at")

    /** Consecutive unanswered reminders before the nudge pauses itself (anti-spam). */
    const val REMINDER_IGNORED_LIMIT = 3

    /** How long the self-pause lasts before gently trying again (epoch-days). */
    const val REMINDER_SNOOZE_DAYS = 14L

    /**
     * Stable anonymous id used to replace (not duplicate) a vote after reinstall.
     * ANDROID_ID is app-signing-key/user/device scoped on Android 8+ (minSdk 29).
     */
    suspend fun deviceId(context: Context): String {
        val prefs = context.dataStore.data.first()
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
        )
        val id = stableAnonymousDeviceId(context.packageName, androidId)
            ?: prefs[KEY_DEVICE_ID]
            ?: UUID.randomUUID().toString()
        if (prefs[KEY_DEVICE_ID] != id) {
            context.dataStore.edit { it[KEY_DEVICE_ID] = id }
        }
        return id
    }

    suspend fun isFilteringEnabled(context: Context): Boolean =
        context.dataStore.data.first()[KEY_FILTERING_ENABLED] ?: true

    suspend fun setFilteringEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[KEY_FILTERING_ENABLED] = enabled }
    }

    suspend fun isWarnEnabled(context: Context): Boolean =
        context.dataStore.data.first()[KEY_WARN_ENABLED] ?: true

    suspend fun setWarnEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[KEY_WARN_ENABLED] = enabled }
    }

    /** User-configurable: block when spamScore >= threshold. */
    suspend fun blockThreshold(context: Context): Int =
        context.dataStore.data.first()[KEY_BLOCK_THRESHOLD] ?: 85

    suspend fun setBlockThreshold(context: Context, value: Int) {
        context.dataStore.edit { it[KEY_BLOCK_THRESHOLD] = value }
    }

    suspend fun lastSyncAt(context: Context): Long =
        context.dataStore.data.first()[KEY_LAST_SYNC] ?: 0L

    suspend fun setLastSyncAt(context: Context, value: Long) {
        context.dataStore.edit { it[KEY_LAST_SYNC] = value }
    }

    /**
     * Country scope (E.164 dial code) for the list sync; "" = worldwide ("Tous").
     * Unset → detect from SIM/locale so a new install scopes to where the user is.
     */
    suspend fun countryDial(context: Context): String =
        context.dataStore.data.first()[KEY_COUNTRY_DIAL]
            ?: Countries.detectDefault(context).dial

    suspend fun setCountryDial(context: Context, dial: String) {
        context.dataStore.edit { it[KEY_COUNTRY_DIAL] = dial }
    }

    /** Whether the user has seen/confirmed the auto-detected country (1st-launch banner). */
    suspend fun isCountryConfirmed(context: Context): Boolean =
        context.dataStore.data.first()[KEY_COUNTRY_CONFIRMED] ?: false

    suspend fun setCountryConfirmed(context: Context, value: Boolean) {
        context.dataStore.edit { it[KEY_COUNTRY_CONFIRMED] = value }
    }

    // SMS shield. Android can't truly block SMS without being the default SMS
    // app, so (like Saracroche) we hide the notification of an unwanted SMS — the
    // message stays in the SMS app, marked unread. Opt-in, default off.
    suspend fun isSmsBlockingEnabled(context: Context): Boolean =
        context.dataStore.data.first()[KEY_SMS_BLOCKING_ENABLED] ?: false

    suspend fun setSmsBlockingEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[KEY_SMS_BLOCKING_ENABLED] = enabled }
    }

    /** Whether to post our own "SMS bloqué" notification when one is hidden. */
    suspend fun blockedSmsNotification(context: Context): Boolean =
        context.dataStore.data.first()[KEY_BLOCKED_SMS_NOTIFICATION] ?: true

    suspend fun setBlockedSmsNotification(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[KEY_BLOCKED_SMS_NOTIFICATION] = enabled }
    }

    /** Whether to post a discreet "Appel bloqué" notification (à la Saracroche). */
    suspend fun blockedCallNotification(context: Context): Boolean =
        context.dataStore.data.first()[KEY_BLOCKED_CALL_NOTIFICATION] ?: true

    /** Everything onScreenCall needs, read in ONE DataStore pass — the
     * screening service runs on the system's ~5 s call budget. */
    data class ScreeningPrefs(
        val filteringEnabled: Boolean,
        val blockThreshold: Int,
        val warnEnabled: Boolean,
        /** Country dial for national-format normalization; never empty. */
        val countryDial: String,
        val blockedCallNotification: Boolean,
        val funNotifications: Boolean,
        val funCustomTitles: Set<String>,
    )

    suspend fun screeningPrefs(context: Context): ScreeningPrefs {
        val p = context.dataStore.data.first()
        return ScreeningPrefs(
            filteringEnabled = p[KEY_FILTERING_ENABLED] ?: true,
            blockThreshold = p[KEY_BLOCK_THRESHOLD] ?: 85,
            warnEnabled = p[KEY_WARN_ENABLED] ?: true,
            // "" means worldwide scope — normalization still needs a concrete
            // prefix for national-format numbers, keep the FR historic default.
            countryDial = (p[KEY_COUNTRY_DIAL] ?: Countries.detectDefault(context).dial)
                .ifEmpty { "33" },
            blockedCallNotification = p[KEY_BLOCKED_CALL_NOTIFICATION] ?: true,
            funNotifications = p[KEY_FUN_NOTIFICATIONS] ?: false,
            funCustomTitles = p[KEY_FUN_CUSTOM_TITLES] ?: emptySet(),
        )
    }

    suspend fun setBlockedCallNotification(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[KEY_BLOCKED_CALL_NOTIFICATION] = enabled }
    }

    /** Fun/personalised notification wording (opt-in, default off). */
    suspend fun funNotifications(context: Context): Boolean =
        context.dataStore.data.first()[KEY_FUN_NOTIFICATIONS] ?: false

    suspend fun setFunNotifications(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[KEY_FUN_NOTIFICATIONS] = enabled }
    }

    /**
     * User-written playful blocked-call titles. They join the built-in FR/EN
     * defaults in the random rotation — never replace them (see
     * [clearFunCustomTitles] for "Réinitialiser").
     */
    suspend fun funCustomTitles(context: Context): Set<String> =
        context.dataStore.data.first()[KEY_FUN_CUSTOM_TITLES] ?: emptySet()

    suspend fun addFunCustomTitle(context: Context, title: String) {
        val clean = title.trim().take(MAX_FUN_TITLE_LEN)
        if (clean.isEmpty()) return
        context.dataStore.edit { p ->
            p[KEY_FUN_CUSTOM_TITLES] = (p[KEY_FUN_CUSTOM_TITLES] ?: emptySet()) + clean
        }
    }

    suspend fun removeFunCustomTitle(context: Context, title: String) {
        context.dataStore.edit { p ->
            p[KEY_FUN_CUSTOM_TITLES] = (p[KEY_FUN_CUSTOM_TITLES] ?: emptySet()) - title
        }
    }

    /** Back to the built-in defaults only ("Réinitialiser" in Settings). */
    suspend fun clearFunCustomTitles(context: Context) {
        context.dataStore.edit { it.remove(KEY_FUN_CUSTOM_TITLES) }
    }

    /**
     * Daily "come back and play" game reminder. Default ON — but harmless by
     * itself: nothing fires until POST_NOTIFICATIONS is granted, and the
     * ignored-reminders kill-switch (see [reminderIgnoredCount]) auto-mutes it
     * for users who never engage. Turning it off in Settings cancels the worker.
     */
    suspend fun isGameReminderEnabled(context: Context): Boolean =
        context.dataStore.data.first()[KEY_GAME_REMINDER] ?: true

    suspend fun setGameReminderEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[KEY_GAME_REMINDER] = enabled }
    }

    /** Whether the in-app notification pre-prompt was already shown once. */
    suspend fun isNotifPromptSeen(context: Context): Boolean =
        context.dataStore.data.first()[KEY_NOTIF_PROMPT_SEEN] ?: false

    suspend fun setNotifPromptSeen(context: Context) {
        context.dataStore.edit { it[KEY_NOTIF_PROMPT_SEEN] = true }
    }

    /**
     * First-launch shield setup wizard (screening + notifications + call log).
     * Once completed or skipped, the full-screen tunnel never returns — Home
     * keeps a lighter "protection partielle" nudge instead.
     */
    suspend fun isShieldSetupCompleted(context: Context): Boolean =
        context.dataStore.data.first()[KEY_SHIELD_SETUP_COMPLETED] ?: false

    suspend fun setShieldSetupCompleted(context: Context, completed: Boolean = true) {
        context.dataStore.edit { it[KEY_SHIELD_SETUP_COMPLETED] = completed }
    }

    /** Most recent call already opened or dismissed from the Home prompt. */
    suspend fun recentCallHandledAt(context: Context): Long =
        context.dataStore.data.first()[KEY_RECENT_CALL_HANDLED_AT] ?: 0L

    suspend fun setRecentCallHandledAt(context: Context, timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_RECENT_CALL_HANDLED_AT] = maxOf(
                prefs[KEY_RECENT_CALL_HANDLED_AT] ?: 0L,
                timestamp,
            )
        }
    }

    /**
     * Most recent call whose Home card was displayed during a now-finished
     * session. "Seen but not acted on" — the card stops headlining it unless
     * the call is still fresh (see findRecentCallPrompt).
     */
    suspend fun recentCallSeenAt(context: Context): Long =
        context.dataStore.data.first()[KEY_RECENT_CALL_SEEN_AT] ?: 0L

    suspend fun setRecentCallSeenAt(context: Context, timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_RECENT_CALL_SEEN_AT] = maxOf(
                prefs[KEY_RECENT_CALL_SEEN_AT] ?: 0L,
                timestamp,
            )
        }
    }

    /**
     * Anti-spam pause: number of consecutive reminders that got no reaction
     * (no tap, no game played). At [REMINDER_IGNORED_LIMIT] the worker goes
     * quiet for [REMINDER_SNOOZE_DAYS] days (a pause, never a permanent mute);
     * any tap, play or visit to the Games hub resets the counter (see
     * [resetReminderIgnored]).
     */
    suspend fun reminderIgnoredCount(context: Context): Int =
        context.dataStore.data.first()[KEY_REMINDER_IGNORED] ?: 0

    suspend fun incrementReminderIgnored(context: Context) {
        context.dataStore.edit {
            it[KEY_REMINDER_IGNORED] = (it[KEY_REMINDER_IGNORED] ?: 0) + 1
        }
    }

    suspend fun resetReminderIgnored(context: Context) {
        context.dataStore.edit {
            it[KEY_REMINDER_IGNORED] = 0
            it.remove(KEY_REMINDER_SNOOZED_UNTIL)
        }
    }

    /** Epoch-day the current reminder pause ends (0 = no pause running). */
    suspend fun reminderSnoozedUntilDay(context: Context): Long =
        context.dataStore.data.first()[KEY_REMINDER_SNOOZED_UNTIL] ?: 0L

    suspend fun snoozeReminderUntilDay(context: Context, day: Long) {
        context.dataStore.edit { it[KEY_REMINDER_SNOOZED_UNTIL] = day }
    }

    /** Epoch-day a reminder was last actually posted — guards double fires. */
    suspend fun reminderLastShownDay(context: Context): Long =
        context.dataStore.data.first()[KEY_REMINDER_LAST_SHOWN_DAY] ?: -1L

    suspend fun setReminderLastShownDay(context: Context, day: Long) {
        context.dataStore.edit { it[KEY_REMINDER_LAST_SHOWN_DAY] = day }
    }

    /** Whether the TRACE interactive tutorial was completed once (first launch only). */
    suspend fun isTraceTutorialSeen(context: Context): Boolean =
        context.dataStore.data.first()[KEY_TRACE_TUTORIAL_SEEN] ?: false

    suspend fun setTraceTutorialSeen(context: Context, seen: Boolean) {
        context.dataStore.edit { it[KEY_TRACE_TUTORIAL_SEEN] = seen }
    }

    /** True if any game was already played today (so the reminder can stay quiet). */
    suspend fun playedAnyGameToday(context: Context, today: Long): Boolean {
        val p = context.dataStore.data.first()
        return (p[kGameLastDay("defense")] ?: -1L) == today || (p[kGameLastDay("trace")] ?: -1L) == today
    }

    // --- Daily games ("defense" arcade | "trace" puzzle) ---

    data class GameState(
        val lastPlayedDay: Long, // epoch-day last played (-1 = never)
        val streak: Int, // consecutive days played
        val lastScore: Int,
        val lastWave: Int, // DEFENSE: waves · TRACE: grids solved
        val bestScoreToday: Int,
        val bestScoreAllTime: Int,
        val attemptsToday: Int, // ranked attempts used today
        val bestStreak: Int, // longest daily streak ever
    )

    suspend fun gameState(context: Context, game: String = "defense"): GameState {
        val p = context.dataStore.data.first()
        return GameState(
            lastPlayedDay = p[kGameLastDay(game)] ?: -1L,
            streak = p[kGameStreak(game)] ?: 0,
            lastScore = p[kGameLastScore(game)] ?: 0,
            lastWave = p[kGameLastWave(game)] ?: 0,
            bestScoreToday = p[kGameBestToday(game)] ?: 0,
            bestScoreAllTime = p[kGameBestAllTime(game)] ?: 0,
            attemptsToday = p[kGameAttempts(game)] ?: 0,
            bestStreak = p[kGameBestStreak(game)] ?: 0,
        )
    }

    /**
     * Record a finished run for `game` on `today`. Returns whether it counted as
     * RANKED (first [MAX_ATTEMPTS] runs of the day) vs. unranked practice. The
     * streak is day-based — it bumps once on the first play of a new day and only
     * breaks by skipping a day; replaying never loses it.
     */
    suspend fun recordGame(context: Context, game: String, today: Long, score: Int, waves: Int): Boolean {
        val p = context.dataStore.data.first()
        val prevDay = p[kGameLastDay(game)] ?: -1L
        val prevStreak = p[kGameStreak(game)] ?: 0
        val firstToday = prevDay != today
        val attempts = if (firstToday) 0 else (p[kGameAttempts(game)] ?: 0)
        val ranked = attempts < MAX_ATTEMPTS
        val newStreak = if (firstToday) (if (prevDay == today - 1) prevStreak + 1 else 1) else prevStreak
        val newBestStreak = maxOf(p[kGameBestStreak(game)] ?: 0, newStreak)
        val prevBestToday = if (firstToday) 0 else (p[kGameBestToday(game)] ?: 0)
        val bestToday = maxOf(prevBestToday, score)
        // Update the local per-day history (keep the best of the day).
        val existing = (p[kGameHistory(game)] ?: emptySet()).filterNot { it.startsWith("$today|") }
        val newHistory = (existing + "$today|$bestToday|$waves")
            .sortedByDescending { it.substringBefore("|").toLongOrNull() ?: 0L }
            .take(30).toSet()
        context.dataStore.edit {
            // Playing at all proves the user is engaged — re-arm the reminder.
            it[KEY_REMINDER_IGNORED] = 0
            it[kGameLastDay(game)] = today
            it[kGameStreak(game)] = newStreak
            it[kGameBestStreak(game)] = newBestStreak
            it[kGameAttempts(game)] = if (ranked) attempts + 1 else attempts
            it[kGameLastScore(game)] = score
            it[kGameLastWave(game)] = waves
            it[kGameBestToday(game)] = bestToday
            it[kGameBestAllTime(game)] = maxOf(p[kGameBestAllTime(game)] ?: 0, score)
            it[kGameHistory(game)] = newHistory
        }
        return ranked
    }

    data class LocalDay(val day: Long, val score: Int, val waves: Int)

    /** Local per-day best history (desc by day) — used as leaderboard fallback. */
    suspend fun localHistory(context: Context, game: String = "defense"): List<LocalDay> =
        (context.dataStore.data.first()[kGameHistory(game)] ?: emptySet()).mapNotNull { s ->
            val parts = s.split("|")
            val day = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            LocalDay(day, parts.getOrNull(1)?.toIntOrNull() ?: 0, parts.getOrNull(2)?.toIntOrNull() ?: 0)
        }.sortedByDescending { it.day }
}

internal fun stableAnonymousDeviceId(packageName: String, androidId: String?): String? {
    if (androidId.isNullOrBlank() || androidId == "9774d56d682e549c") return null
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$packageName:$androidId".toByteArray())
        .joinToString("") { "%02x".format(it) }
    return "android-${digest.take(32)}"
}
