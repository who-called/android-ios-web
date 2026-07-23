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
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val raw = callDetails.handle?.schemeSpecificPart
        val phone = PhoneNormalizer.normalize(raw)
        val db = WhoCalledDatabase.get(this)

        val filteringEnabled = runBlocking { Preferences.isFilteringEnabled(this@WhoCalledScreeningService) }
        if (!filteringEnabled) {
            respondToCall(callDetails, allowResponse())
            return
        }

        val blockThreshold = runBlocking { Preferences.blockThreshold(this@WhoCalledScreeningService) }
        val warnEnabled = runBlocking { Preferences.isWarnEnabled(this@WhoCalledScreeningService) }

        val decision = ScreeningDecision.decide(
            phone = phone,
            blockThreshold = blockThreshold,
            warnEnabled = warnEnabled,
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
                ?.let { entry -> runCatching { runBlocking { db.callLogDao().insert(entry) } }.getOrNull() }

            when (decision.action) {
                Action.WARN -> NotificationHelper.showWarning(this, raw ?: p, decision.score, callLogId)
                Action.BLOCK -> {
                    // The system "missed call" notice is suppressed; post our own
                    // discreet "Appel bloqué" notice (opt-out via settings).
                    val notify = runBlocking { Preferences.blockedCallNotification(this@WhoCalledScreeningService) }
                    val fun_ = runBlocking { Preferences.funNotifications(this@WhoCalledScreeningService) }
                    // User-written fun titles join the built-in FR/EN rotation.
                    val custom = if (fun_) runBlocking { Preferences.funCustomTitles(this@WhoCalledScreeningService) } else emptySet()
                    if (notify) NotificationHelper.showBlockedCall(this, raw ?: p, callLogId, fun_, custom)
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
