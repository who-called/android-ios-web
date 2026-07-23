package com.whocalled.android.util

/**
 * Normalizes phone numbers to E.164 digits without '+' (e.g. "33612345678").
 * FR-first (default country prefix 33) but accepts already-international input.
 * Mirrors the backend `normalizePhone` logic so client and server agree.
 */
object PhoneNormalizer {

    fun normalize(raw: String?, defaultCountryPrefix: String = "33"): String? {
        if (raw.isNullOrBlank()) return null

        var s = raw.filter { it.isDigit() || it == '+' }

        s = when {
            s.startsWith("+") -> s.drop(1)
            s.startsWith("00") -> s.drop(2)
            s.startsWith("0") -> defaultCountryPrefix + s.drop(1)
            else -> s
        }

        return if (s.matches(Regex("^\\d{6,15}$"))) s else null
    }

    /**
     * Best-effort extraction of a phone number from arbitrary shared text
     * (e.g. an SMS body). Returns the raw matched candidate (not normalized) so
     * the report screen can display it; null if nothing phone-like is found.
     * Picks the longest digit-rich token to avoid grabbing short codes.
     */
    fun extractFromText(text: String?): String? {
        if (text.isNullOrBlank()) return null
        // Phone-like runs: optional +, then digits/spaces/dots/dashes/parens.
        val candidates = Regex("\\+?[0-9][0-9\\s().\\-]{5,18}[0-9]")
            .findAll(text)
            .map { it.value.trim() }
            .filter { normalize(it) != null }
            .toList()
        return candidates.maxByOrNull { it.count { c -> c.isDigit() } }
    }
}
