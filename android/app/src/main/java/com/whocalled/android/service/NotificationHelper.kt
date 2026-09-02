package com.whocalled.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.whocalled.android.MainActivity
import com.whocalled.android.R
import com.whocalled.android.ui.theme.WCColor

/**
 * Notifications for the WARN level ("j'ai un doute") — the call rings normally
 * but the user gets an immediate alert with the spam score.
 */
object NotificationHelper {
    const val CHANNEL_WARN = "who_called_warn"
    const val CHANNEL_SMS_BLOCKED = "who_called_sms_blocked"
    const val CHANNEL_CALL_BLOCKED = "who_called_call_blocked"
    const val CHANNEL_REMINDER = "who_called_reminder"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val warn = NotificationChannel(
            CHANNEL_WARN,
            context.getString(R.string.warn_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.warn_channel_desc)
        }
        manager.createNotificationChannel(warn)

        val smsBlocked = NotificationChannel(
            CHANNEL_SMS_BLOCKED,
            context.getString(R.string.sms_blocked_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.sms_blocked_channel_desc)
        }
        manager.createNotificationChannel(smsBlocked)

        val callBlocked = NotificationChannel(
            CHANNEL_CALL_BLOCKED,
            context.getString(R.string.call_blocked_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.call_blocked_channel_desc)
        }
        manager.createNotificationChannel(callBlocked)

        val reminder = NotificationChannel(
            CHANNEL_REMINDER,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.reminder_channel_desc)
        }
        manager.createNotificationChannel(reminder)
    }

    /**
     * Daily "come back and play" reminder — kind, encouraging (LinkedIn-style),
     * never guilt-trippy. Opt-in and skipped if the user already played today.
     */
    private val reminderMessages = listOf(
        "🧩 Le défi TRACE du jour t'attend — 5 grilles, 3 minutes chrono." to "Grimpe au classement, à ton rythme.",
        "🔥 Garde ta série en vie" to "Une partie rapide suffit pour continuer sur ta lancée.",
        "🏆 Quelqu'un a peut-être battu ton score" to "À toi de reprendre la tête, en toute bienveillance.",
        "🛡️ Pause futée du jour" to "Un défi anti-spam pour se changer les idées ?",
        "✨ Nouveau défi disponible" to "Mêmes grilles pour tout le monde aujourd'hui — montre ce que tu sais faire !",
    )

    /** Stable id so the opt-out action can dismiss the reminder. */
    val GAME_REMINDER_NOTIFICATION_ID = "game_reminder".hashCode()

    /** Returns whether the reminder was actually posted (permission + channel). */
    fun showGameReminder(context: Context): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        // A blocked channel makes notify() a silent no-op — report it so the
        // caller never counts an "ignored" reminder nobody could see.
        val channel = manager.getNotificationChannelCompat(CHANNEL_REMINDER)
        if (channel != null && channel.importance == NotificationManagerCompat.IMPORTANCE_NONE) return false
        val (title, body) = reminderMessages.random()
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification_shield)
            .setColor(WCColor.Blue.toArgb())
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(gamesIntent(context)) // opens the Games hub (choose a game)
            // No opt-out action on the notification itself — the toggle lives in
            // Settings → Jeux (cleaner, matches the other game's notification).
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        return runCatching {
            manager.notify(GAME_REMINDER_NOTIFICATION_ID, notification)
        }.isSuccess
    }

    /** Content intent that opens the app straight on the Games hub. */
    private fun gamesIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_GAMES, true)
        }
        return PendingIntent.getActivity(
            context,
            "games".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Discreet "Appel bloqué" notice (à la Saracroche). The system call-log
     * notification is suppressed (setSkipNotification), so this is the only trace
     * the user sees — and it's opt-out via settings.
     */
    /**
     * Playful, opt-in wording for blocked-call notifications — built-in defaults
     * in French AND English, with emojis. The user can add their own phrases in
     * Settings (they join this rotation) or reset back to just these defaults.
     */
    val defaultFunBlockedTitles = listOf(
        // 🇫🇷
        "🥊 Spam K.O. !", "🛡️ Bien tenté, spammeur", "🚫 Démarcheur refoulé",
        "📵 Un importun de moins", "☀️ Encore un vendeur de panneaux solaires bloqué",
        // 🇬🇧
        "🥊 Spam knocked out!", "🛡️ Nice try, spammer", "🚫 Cold caller turned away",
        "📵 One less nuisance", "😎 Blocked — you're welcome",
    )

    fun showBlockedCall(
        context: Context,
        rawPhone: String?,
        callLogId: Long?,
        funMode: Boolean = false,
        customTitles: Set<String> = emptySet(),
    ) {
        if (NotificationManagerCompat.from(context).areNotificationsEnabled().not()) return
        val number = rawPhone?.takeIf { it.isNotBlank() }?.let { if (it.startsWith("+")) it else "+$it" }
        val title = if (funMode) {
            (defaultFunBlockedTitles + customTitles).random()
        } else {
            context.getString(R.string.call_blocked_title)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_CALL_BLOCKED)
            .setSmallIcon(R.drawable.ic_notification_shield)
            .setColor(WCColor.Coral.toArgb())
            .setContentTitle(title)
            .setContentText(number ?: context.getString(R.string.call_blocked_unknown))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(detailIntent(context, callLogId))
            .setGroup(GROUP_BLOCKED_CALLS)
            .setAutoCancel(true)
            .build()
        runCatching {
            // Unique id per blocked call so several can coexist.
            val id = "call_${number ?: "?"}_${System.currentTimeMillis()}".hashCode()
            NotificationManagerCompat.from(context).notify(id, notification)
            // Group summary so a spam wave collapses into one tray line
            // instead of flooding the shade with LOW-priority entries.
            val summary = NotificationCompat.Builder(context, CHANNEL_CALL_BLOCKED)
                .setSmallIcon(R.drawable.ic_notification_shield)
                .setColor(WCColor.Coral.toArgb())
                .setContentTitle(context.getString(R.string.call_blocked_title))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(detailIntent(context, null))
                .setGroup(GROUP_BLOCKED_CALLS)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(BLOCKED_CALLS_SUMMARY_ID, summary)
        }
    }

    private const val GROUP_BLOCKED_CALLS = "who_called_blocked_calls"
    private val BLOCKED_CALLS_SUMMARY_ID = "blocked_calls_summary".hashCode()

    /** Discreet "an unwanted SMS notification was hidden" notice. */
    fun showBlockedSms(context: Context, sender: String) {
        if (NotificationManagerCompat.from(context).areNotificationsEnabled().not()) return
        // Deep-link to the sender's number page when it IS a number (calls
        // already do) — short codes / alphanumeric senders fall back to Home.
        val senderPhone = com.whocalled.android.util.PhoneNormalizer.normalize(sender)
        val notification = NotificationCompat.Builder(context, CHANNEL_SMS_BLOCKED)
            .setSmallIcon(R.drawable.ic_notification_shield)
            .setColor(WCColor.Emerald.toArgb())
            .setContentTitle(context.getString(R.string.sms_blocked_title))
            .setContentText(context.getString(R.string.sms_blocked_body, sender))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(
                if (senderPhone != null) phoneIntent(context, senderPhone)
                else detailIntent(context, null),
            )
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify("sms_$sender".hashCode(), notification)
        }
    }

    /**
     * WARN alert during a ringing suspicious call. Android doesn't let us put a
     * label under the number on the call screen (the CallScreeningService API has
     * no caller-name field — Saracroche has the same limit), so we make the alert
     * stand out instead: a heads-up, sticky (ongoing) notice that stays up while
     * the phone rings and auto-clears after ~45s (covers the ring) so it never
     * lingers. It carries the number + score and opens the detail on tap.
     */
    fun showWarning(context: Context, phone: String, score: Int, category: String?, callLogId: Long?) {
        if (NotificationManagerCompat.from(context).areNotificationsEnabled().not()) return

        // "Démarchage · risque 72%" decides a ringing call better than a bare
        // percentage — surface the known category while the phone rings.
        val categoryLabel = com.whocalled.android.data.ReportCategory.fromApi(category)
            ?.takeIf { it != com.whocalled.android.data.ReportCategory.OTHER }?.label
        val body = context.getString(R.string.warn_body, phone, score)
        val notification = NotificationCompat.Builder(context, CHANNEL_WARN)
            .setSmallIcon(R.drawable.ic_notification_shield)
            .setColor(WCColor.Amber.toArgb())
            .setContentTitle(context.getString(R.string.warn_title))
            .setContentText(if (categoryLabel != null) "$categoryLabel · $body" else body)
            .setSubText(context.getString(R.string.warn_subtext)) // "Sonne mais non bloqué"
            .setPriority(NotificationCompat.PRIORITY_MAX) // heads-up over the call UI
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(detailIntent(context, callLogId))
            .setOngoing(true) // sticky while it rings — not swiped away by accident
            .setTimeoutAfter(45_000L) // auto-clear after the ring window
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(phone.hashCode(), notification)
        }
    }

    /** Key for the call-log id carried by a notification's content intent. */
    const val EXTRA_OPEN_CALL_ID = "open_call_id"

    /** Flag carried by the daily reminder → open the Games hub. */
    const val EXTRA_OPEN_GAMES = "open_games"

    /** Normalized number carried by a notification → open that number's page. */
    const val EXTRA_OPEN_PHONE = "open_phone"

    /** Tapping opens the app straight on [phone]'s number page. */
    private fun phoneIntent(context: Context, phone: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_PHONE, phone)
        }
        return PendingIntent.getActivity(
            context,
            "phone_$phone".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Tapping a notification opens the app; if [callLogId] is known, MainActivity
     * deep-links to that call's detail screen, otherwise it just opens Home.
     */
    private fun detailIntent(context: Context, callLogId: Long?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (callLogId != null) putExtra(EXTRA_OPEN_CALL_ID, callLogId)
        }
        val requestCode = (callLogId ?: System.currentTimeMillis()).toInt()
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
