package com.whocalled.android.util

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/**
 * Where a cached caller name comes from. The dialer caches a name both for the
 * user's own contacts and for numbers resolved through a *remote* directory
 * (Google's caller-ID / "spam protection" lookup) — two very different claims,
 * so we never present them the same way.
 */
enum class CallerNameSource {
    /** The name belongs to an entry in the user's address book. */
    CONTACT,

    /** Resolved by the phone's caller-ID lookup — informative, but NOT a contact. */
    DIRECTORY,
}

/** An entry read from the device's system call log. */
data class PhoneCall(
    val systemId: Long,
    val rawNumber: String,
    val normalizedPhone: String?,
    val timestamp: Long,
    val type: Int, // CallLog.Calls.INCOMING_TYPE, MISSED_TYPE, ...
    val durationSeconds: Long = 0,
    val contactName: String? = null, // dialer-cached name (null = no name at all)
    val nameSource: CallerNameSource = CallerNameSource.CONTACT,
) {
    /** A cached name of any origin (address book or caller-ID lookup). */
    val hasCachedName: Boolean get() = !contactName.isNullOrBlank()

    /** True only when the number matches one of the user's contacts. */
    val isContact: Boolean
        get() = hasCachedName && nameSource == CallerNameSource.CONTACT
}

/**
 * Reads the device's system call log (requires READ_CALL_LOG).
 * Used so the user can report a number straight from their recent calls.
 */
object CallLogReader {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALL_LOG,
        ) == PackageManager.PERMISSION_GRANTED

    /** Returns recent calls, most recent first. Empty if permission is missing. */
    fun recentCalls(context: Context, limit: Int = 200): List<PhoneCall> {
        if (!hasPermission(context)) return emptyList()

        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION,
            CallLog.Calls.CACHED_NAME, // contact name cached by the dialer (no READ_CONTACTS)
            CallLog.Calls.CACHED_LOOKUP_URI, // tells contact apart from caller-ID directory
        )

        val calls = mutableListOf<PhoneCall>()
        runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC",
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(CallLog.Calls._ID)
                val numberIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val dateIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val typeIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val durationIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val lookupIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_LOOKUP_URI)

                while (cursor.moveToNext() && calls.size < limit) {
                    val raw = cursor.getString(numberIdx) ?: continue
                    calls.add(
                        PhoneCall(
                            systemId = cursor.getLong(idIdx),
                            rawNumber = raw,
                            normalizedPhone = PhoneNormalizer.normalize(raw),
                            timestamp = cursor.getLong(dateIdx),
                            type = cursor.getInt(typeIdx),
                            durationSeconds = cursor.getLong(durationIdx),
                            contactName = if (nameIdx >= 0) cursor.getString(nameIdx) else null,
                            nameSource = nameSourceOf(
                                if (lookupIdx >= 0) cursor.getString(lookupIdx) else null,
                            ),
                        ),
                    )
                }
            }
        }
        return calls
    }

    /**
     * Classify a cached name from the lookup URI the dialer stored with it.
     *
     * A local (or work-profile) contact yields a plain contacts lookup URI; a
     * caller-ID hit carries a `directory` id that [ContactsContract.Directory
     * .isRemoteDirectoryId] recognizes as remote. Deliberately conservative:
     * anything we cannot attribute to a remote directory stays [CONTACT], so we
     * never wrongly tell the user a real contact is only "identified".
     */
    fun nameSourceOf(lookupUri: String?): CallerNameSource {
        if (lookupUri.isNullOrBlank()) return CallerNameSource.CONTACT
        val directoryId = runCatching {
            Uri.parse(lookupUri)
                .getQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY)
                ?.toLong()
        }.getOrNull() ?: return CallerNameSource.CONTACT
        return if (ContactsContract.Directory.isRemoteDirectoryId(directoryId)) {
            CallerNameSource.DIRECTORY
        } else {
            CallerNameSource.CONTACT
        }
    }
}
