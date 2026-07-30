package com.whocalled.android.util

import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat

/** An entry read from the device's system call log. */
data class PhoneCall(
    val systemId: Long,
    val rawNumber: String,
    val normalizedPhone: String?,
    val timestamp: Long,
    val type: Int, // CallLog.Calls.INCOMING_TYPE, MISSED_TYPE, ...
    val contactName: String? = null, // dialer-cached name (null = not in contacts)
) {
    /** True when the number matches one of the user's contacts (has a cached name). */
    val isContact: Boolean get() = !contactName.isNullOrBlank()
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
    fun recentCalls(context: Context, limit: Int = 50): List<PhoneCall> {
        if (!hasPermission(context)) return emptyList()

        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.TYPE,
            CallLog.Calls.CACHED_NAME, // contact name cached by the dialer (no READ_CONTACTS)
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
                val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)

                while (cursor.moveToNext() && calls.size < limit) {
                    val raw = cursor.getString(numberIdx) ?: continue
                    calls.add(
                        PhoneCall(
                            systemId = cursor.getLong(idIdx),
                            rawNumber = raw,
                            normalizedPhone = PhoneNormalizer.normalize(raw),
                            timestamp = cursor.getLong(dateIdx),
                            type = cursor.getInt(typeIdx),
                            contactName = if (nameIdx >= 0) cursor.getString(nameIdx) else null,
                        ),
                    )
                }
            }
        }
        return calls
    }
}
