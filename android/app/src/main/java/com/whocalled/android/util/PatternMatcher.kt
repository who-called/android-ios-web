package com.whocalled.android.util

import com.whocalled.android.data.PatternEntity

/**
 * Matches a normalized phone (E.164 digits) against wildcard patterns where '#'
 * matches any single digit. Pattern and phone must have the same length.
 */
object PatternMatcher {

    fun matches(phone: String, pattern: String): Boolean {
        if (phone.length != pattern.length) return false
        for (i in pattern.indices) {
            val pc = pattern[i]
            if (pc == '#') {
                if (!phone[i].isDigit()) return false
            } else if (pc != phone[i]) {
                return false
            }
        }
        return true
    }

    /** First pattern matching the phone, or null. */
    fun firstMatch(phone: String, patterns: List<PatternEntity>): PatternEntity? =
        patterns.firstOrNull { matches(phone, it.pattern) }
}
