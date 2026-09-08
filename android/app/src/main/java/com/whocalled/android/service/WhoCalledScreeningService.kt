package com.whocalled.android.service

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.whocalled.android.data.Preferences
import com.whocalled.android.data.WhoCalledDatabase
import com.whocalled.android.util.PhoneNormalizer
import kotlinx.coroutines.runBlocking

/**
 * Real-time call screening — the heart of who-called.
 *
 *   BLOCK → silently reject (setDisallowCall + setRejectCall, skip log/notification)
 *   WARN  → let the phone ring AND fire a custom notification ("⚠️ spam probable, score X%")
 *           — the signature feature: "fais sonner mais affiche une alerte".
 *   ALLOW → ring normally.
 */
class WhoCalledScreeningService : CallScreeningService() {

    private companion object {
        const val TAG = "WhoCalledScreening"
        const val JOURNAL_RETENTION_MS = 90L * 24 * 60 * 60 * 1_000
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val raw = callDetails.handle?.schemeSpecificPart
        val db = WhoCalledDatabase.get(this)

        // ONE DataStore read for every pref — this runs on the system's ~5 s
        // call budget, each extra blocking read eats into it.
        val prefs = runBlocking { Preferences.screeningPrefs(this@WhoCalledScreeningService) }
        if (!prefs.filteringEnabled) {
            respondToCall(callDetails, allowResponse())
            return
        }

        // National-format numbers resolve against the user's country scope —
        // a +32 user's "0470…" must match the +32 list, not become a 33….
        val phone = PhoneNormalizer.normalize(raw, prefs.countryDial)

        val decision = ScreeningDecision.decide(
            phone = phone,
            blockThreshold = prefs.blockThreshold,
            warnEnabled = prefs.warnEnabled,
            userRuleDao = db.userRuleDao(),
            scoredNumberDao = db.scoredNumberDao(),
            patternDao = db.patternDao(),
        )

        Log.d(TAG, "Call $raw → ${decision.action} (score=${decision.score})")

        when (decision.action) {
            Action.BLOCK -> respondToCall(callDetails, blockResponse())
            Action.WARN, Action.ALLOW -> respondToCall(callDetails, allowResponse())
        }

        // Side effects: journal + notifications. We insert the journal entry first
        // so the notification can deep-link straight to that call's detail.
        phone?.let { p ->
            val callLogId = ScreeningDecision.logEntry(p, decision.action, decision.score, decision.category)
                .let { entry -> runCatching { runBlocking { db.callLogDao().insert(entry) } }.getOrNull() }
            // Allowed calls are journaled too, so the table grows with every
            // call: keep it bounded to what the Calls tab can meaningfully show.
            runCatching {
                runBlocking { db.callLogDao().pruneOlderThan(System.currentTimeMillis() - JOURNAL_RETENTION_MS) }
            }

            when (decision.action) {
                Action.WARN -> NotificationHelper.showWarning(
                    this, raw ?: p, decision.score, decision.category, callLogId,
                )
                Action.BLOCK -> {
                    // The system "missed call" notice is suppressed; post our own
                    // discreet "Appel bloqué" notice (opt-out via settings).
                    // User-written fun titles join the built-in FR/EN rotation.
                    if (prefs.blockedCallNotification) {
                        NotificationHelper.showBlockedCall(
                            this,
                            // Normalized digits, never the raw national form —
                            // the notification prefixes "+" to what it gets.
                            p,
                            callLogId,
                            prefs.funNotifications,
                            if (prefs.funNotifications) prefs.funCustomTitles else emptySet(),
                        )
                    }
                }
                Action.ALLOW -> {}
            }
        }
    }

    private fun blockResponse() = CallResponse.Builder()
        .setDisallowCall(true)
        .setRejectCall(true)
        .setSkipCallLog(true)
        .setSkipNotification(true)
        .build()

    private fun allowResponse() = CallResponse.Builder()
        .setDisallowCall(false)
        .setRejectCall(false)
        .build()
}
