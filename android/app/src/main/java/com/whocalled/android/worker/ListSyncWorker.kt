package com.whocalled.android.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.whocalled.android.data.WhoCalledRepository
import java.util.concurrent.TimeUnit

/**
 * Periodic delta-sync of the scored-number list (every N hours), network-required.
 */
class ListSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = WhoCalledRepository(applicationContext)
        return repo.syncList().fold(
            onSuccess = {
                // The network is up — piggy-back the queue of unsent reports.
                repo.retryPendingReports()
                Result.success()
            },
            onFailure = { Result.retry() },
        )
    }

    companion object {
        private const val WORK_NAME = "who-called-list-sync"
        private const val INTERVAL_HOURS = 6L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ListSyncWorker>(
                INTERVAL_HOURS, TimeUnit.HOURS,
            ).setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
