package com.whocalled.android

import android.app.Application
import com.whocalled.android.data.DebugSeeder
import com.whocalled.android.data.Preferences
import com.whocalled.android.data.WarmupLoader
import com.whocalled.android.data.WhoCalledDatabase
import com.whocalled.android.service.NotificationHelper
import com.whocalled.android.worker.GameReminderWorker
import com.whocalled.android.worker.ListSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WhoCalledApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
        ListSyncWorker.schedule(this)

        // Preload the embedded warm-up (real FR spam DB) so the app works
        // offline on first launch; then fall back to mock data in debug builds
        // only if no warm-up was bundled.
        CoroutineScope(Dispatchers.IO).launch {
            val db = WhoCalledDatabase.get(this@WhoCalledApplication)
            WarmupLoader.loadIfNeeded(this@WhoCalledApplication, db)
            DebugSeeder.seedIfNeeded(db)
            // Keep the opt-in daily game reminder scheduled (no-op / cancelled when off).
            if (Preferences.isGameReminderEnabled(this@WhoCalledApplication)) {
                GameReminderWorker.schedule(this@WhoCalledApplication)
            }
        }
    }
}
