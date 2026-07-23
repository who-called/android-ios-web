package com.whocalled.android.util

/**
 * Extracts the sender phone number from an SMS notification's extras. The
 * default SMS app exposes the sender in several places depending on the app and
 * Android version: the `android.people.list` / `android.messagingPerson` URIs
 * (most reliable, `tel:`), the notification title, or somewhere in the text.
 *
 * Pure/Android-free so it can be unit-tested.
 */
object SmsNumberExtractor {

    /** A notification "person" reduced to its URI (e.g. "tel:+33612345678"). */
    data class PersonInfo(val uri: String?)

    fun extractSenderNumber(
        peopleList: List<PersonInfo>?,
        messagingPerson: PersonInfo?,
        title: String?,
        text: String?,
        bigText: String?,
    ): String? {
        // 1) tel: URIs from the people list (most reliable).
        peopleList?.forEach { person ->
            fromTelUri(person.uri)?.let { return it }
        }
        // 2) messaging person tel: URI.
        fromTelUri(messagingPerson?.uri)?.let { return it }

        // 3) The title, only when it looks like a phone number (not a contact name).
        title?.let { t ->
            if (t.matches(Regex("^[+0-9\\s().\\-]{4,}$"))) {
                val cleaned = t.replace(Regex("[^0-9+]"), "")
                if (cleaned.length >= 4) return cleaned
            }
        }

        // 4) Last resort: scan the combined text for a phone-like run.
        val haystack = listOfNotNull(title, text, bigText).joinToString(" ")
        Regex("\\+?[0-9]{1,4}(?:[\\s().\\-]?[0-9]{1,4})+").find(haystack)?.value?.let { match ->
            val cleaned = match.replace(Regex("[^0-9+]"), "")
            if (cleaned.length >= 7) return cleaned
        }
        return null
    }

    private fun fromTelUri(uri: String?): String? {
        if (uri == null || !uri.startsWith("tel:")) return null
        val number = uri.substring(4).replace(Regex("[^0-9+]"), "")
        return if (number.length >= 4) number else null
    }
}
