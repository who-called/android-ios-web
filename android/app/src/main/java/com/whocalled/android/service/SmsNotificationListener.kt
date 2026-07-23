package com.whocalled.android.service

import android.app.Person
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.os.BundleCompat
import com.whocalled.android.data.Preferences
import com.whocalled.android.data.WhoCalledDatabase
import com.whocalled.android.util.PhoneNormalizer
import com.whocalled.android.util.SmsNumberExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * SMS shield. Android can't truly block an SMS unless the app is the default SMS
 * app, so — like Saracroche — we listen to notifications and HIDE the one posted
 * by the default SMS app when the sender is a number we'd block. The message
 * itself stays in the SMS app, marked unread. Reuses [ScreeningDecision] so SMS
 * and call filtering share the exact same verdict logic.
 *
 * Requires the user to grant notification-access in system settings.
 */
class SmsNotificationListener : NotificationListenerService() {

    private companion object {
        const val TAG = "WhoCalledSmsListener"
    }

    private var scope: CoroutineScope? = null

    override fun onListenerConnected() {
        if (scope == null) scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onListenerDisconnected() {
        scope?.cancel()
        scope = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val scope = scope ?: return

        // Only act on notifications from the user's default SMS app.
        val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this) ?: return
        if (sbn.packageName != defaultSmsPackage) return

        val extras = sbn.notification.extras ?: return
        val people = BundleCompat
            .getParcelableArrayList(extras, "android.people.list", Person::class.java)
            ?.map { SmsNumberExtractor.PersonInfo(it.uri) }
        val messagingPerson = BundleCompat
            .getParcelable(extras, "android.messagingPerson", Person::class.java)
            ?.let { SmsNumberExtractor.PersonInfo(it.uri) }

        val sender = SmsNumberExtractor.extractSenderNumber(
            peopleList = people,
            messagingPerson = messagingPerson,
            title = extras.getString("android.title"),
            text = extras.getString("android.text"),
            bigText = extras.getString("android.bigText"),
        ) ?: return

        val notificationKey = sbn.key

        scope.launch {
            try {
                if (!Preferences.isSmsBlockingEnabled(this@SmsNotificationListener)) return@launch

                val phone = PhoneNormalizer.normalize(sender) ?: return@launch
                val db = WhoCalledDatabase.get(this@SmsNotificationListener)
                val threshold = Preferences.blockThreshold(this@SmsNotificationListener)

                // SMS is hide-or-keep (no "warn" middle ground), so we only act on
                // a BLOCK verdict. warnEnabled=false keeps WARN numbers untouched.
                val decision = ScreeningDecision.decide(
                    phone = phone,
                    blockThreshold = threshold,
                    warnEnabled = false,
                    userRuleDao = db.userRuleDao(),
                    scoredNumberDao = db.scoredNumberDao(),
                    patternDao = db.patternDao(),
                )

                if (decision.action == Action.BLOCK) {
                    Log.d(TAG, "Hiding SMS notification from $sender (message kept in SMS app)")
                    cancelNotification(notificationKey)
                    if (Preferences.blockedSmsNotification(this@SmsNotificationListener)) {
                        NotificationHelper.showBlockedSms(this@SmsNotificationListener, sender)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing SMS notification", e)
            }
        }
    }
}
