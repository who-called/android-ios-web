package com.whocalled.android.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.whocalled.android.data.Preferences
import com.whocalled.android.game.GameDay
import com.whocalled.android.service.NotificationHelper
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Once-a-day "come back and play" reminder, fired at a random time inside the
 * evening window (18:30–20:30) so it lands in downtime and never feels
 * mechanical. Self-rescheduling one-shot chain — more reliable than periodic
 * work against Doze, and each day gets a fresh random slot.
 *
 * It never nags:
 *  - skipped if the user already played today;
 *  - skipped (and not even counted) when notifications can't actually show
 *    (permission missing or the reminder channel blocked);
 *  - after [Preferences.REMINDER_IGNORED_LIMIT] consecutive reminders with no
 *    reaction it PAUSES for [Preferences.REMINDER_SNOOZE_DAYS] days, then
 *    gently tries again — any tap, play or Games-hub visit re-arms it;
 *  - the opt-out toggle lives in Settings → Jeux, which also surfaces the
 *    pause state (so "enabled but quiet" is never invisible).
 */
class GameReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val enabled = runCatching { Preferences.isGameReminderEnabled(ctx) }.getOrDefault(true)
        if (!enabled) return Result.success()
        try {
            runCatching { remindIfDue(ctx) }
        } finally {
            // Always chain tomorrow's slot: a quiet day (already played,
            // paused…) or a transient failure must not kill the schedule.
            schedule(ctx, fromWorker = true)
        }
        return Result.success()
    }

    private suspend fun remindIfDue(ctx: Context) {
        val today = GameDay.epochDay()
        // Whatever re-enqueued us twice (KEEP races, process restarts), one
        // evening never posts — or counts — more than one reminder.
        if (Preferences.reminderLastShownDay(ctx) == today) return

        // Ignored-reminders anti-spam: a time-boxed pause, never a mute.
        if (Preferences.reminderIgnoredCount(ctx) >= Preferences.REMINDER_IGNORED_LIMIT) {
            val until = Preferences.reminderSnoozedUntilDay(ctx)
            when {
                until == 0L -> {
                    Preferences.snoozeReminderUntilDay(ctx, today + Preferences.REMINDER_SNOOZE_DAYS)
                    return
                }
                today < until -> return
                // Pause over — re-arm and fall through to tonight's reminder.
                else -> Preferences.resetReminderIgnored(ctx)
            }
        }

        if (Preferences.playedAnyGameToday(ctx, today)) return

        // Only count "ignored" when the notification was actually posted — a
        // blocked channel or missing permission must not eat the counter.
        if (NotificationHelper.showGameReminder(ctx)) {
            Preferences.setReminderLastShownDay(ctx, today)
            Preferences.incrementReminderIgnored(ctx)
        }
    }

    companion object {
        private const val WORK_NAME = "who-called-game-reminder"

        // Evening downtime window (local time): 18:30–20:30.
        private const val WINDOW_START_MIN = 18 * 60 + 30
        private const val WINDOW_END_MIN = 20 * 60 + 30

        /**
         * Schedule the next reminder at a random minute inside the window.
         *
         * [fromWorker] is set when chaining from inside doWork: the target is
         * then always TOMORROW's window (one reminder per evening, never a
         * same-evening re-fire), and the policy is APPEND_OR_REPLACE because
         * KEEP would silently drop the enqueue while this very work is still
         * RUNNING — the historic reason the "daily" chain only survived until
         * its first fire and then went quiet until the next app launch.
         */
        fun schedule(context: Context, fromWorker: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<GameReminderWorker>()
                .setInitialDelay(delayToNextWindowSlot(skipToday = fromWorker), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                // KEEP from app start must not move an already planned slot
                // (REPLACE would push it forever for daily users).
                if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /**
         * Milliseconds until a random minute in the next 18:30–20:30 window —
         * today's if still ahead (unless [skipToday]), otherwise tomorrow's.
         */
        private fun delayToNextWindowSlot(skipToday: Boolean = false): Long {
            val now = Calendar.getInstance()
            val targetMin = Random.nextInt(WINDOW_START_MIN, WINDOW_END_MIN + 1)
            val next = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, targetMin / 60)
                set(Calendar.MINUTE, targetMin % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (skipToday || before(now)) add(Calendar.DAY_OF_MONTH, 1)
            }
            return next.timeInMillis - now.timeInMillis
        }
    }
}
