package com.whocalled.android.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.whocalled.android.service.SmsNotificationListener

/**
 * Helpers around the system "notification access" permission required by the SMS
 * shield (NotificationListenerService). We never read or store SMS content — we
 * only hide the notification of an unwanted sender.
 */
object NotificationAccess {

    /** True if the user has granted notification access to our SMS listener. */
    fun isGranted(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        val expected = ComponentName(context, SmsNotificationListener::class.java)
        return flat.split(":").any { entry ->
            ComponentName.unflattenFromString(entry) == expected
        }
    }

    /** Opens the system screen where the user grants notification access. */
    fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
