package com.whocalled.android.worker

import android.content.Context
import androidx.core.app.NotificationManagerCompat
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
 *  - skipped (and not even scheduled) without notification permission;
 *  - auto-mutes after [Preferences.REMINDER_IGNORED_LIMIT] consecutive
 *    reminders with no reaction — any tap or play re-arms it;
 *  - the opt-out toggle lives in Settings → Jeux (no action on the
 *    notification itself, matching the other game's reminder).
 */
class GameReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!Preferences.isGameReminderEnabled(ctx)) return Result.success()

        val muted = Preferences.reminderIgnoredCount(ctx) >= Preferences.REMINDER_IGNORED_LIMIT
        val playedToday = Preferences.playedAnyGameToday(ctx, GameDay.epochDay())
        val canNotify = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        if (!muted && !playedToday && canNotify) {
            NotificationHelper.showGameReminder(ctx)
            // Counts as ignored until the user taps it or plays — both reset it.
            Preferences.incrementReminderIgnored(ctx)
        }
        // Always chain tomorrow's slot: a quiet day (already played, muted…)
        // must not kill the schedule itself.
        schedule(ctx)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "who-called-game-reminder"

        // Evening downtime window (local time): 18:30–20:30.
        private const val WINDOW_START_MIN = 18 * 60 + 30
        private const val WINDOW_END_MIN = 20 * 60 + 30

        /** Schedule the next reminder at a random minute inside the window. */
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<GameReminderWorker>()
                .setInitialDelay(delayToNextWindowSlot(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                // KEEP: rescheduling from app start must not move an already
                // planned slot (REPLACE would push it forever for daily users).
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /**
         * Milliseconds until a random minute in the next 18:30–20:30 window —
         * today's if still ahead, otherwise tomorrow's.
         */
        private fun delayToNextWindowSlot(): Long {
            val now = Calendar.getInstance()
            val targetMin = Random.nextInt(WINDOW_START_MIN, WINDOW_END_MIN + 1)
            val next = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, targetMin / 60)
                set(Calendar.MINUTE, targetMin % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
            }
            return next.timeInMillis - now.timeInMillis
        }
    }
}
