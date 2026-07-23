package com.whocalled.android.service

import com.whocalled.android.data.CallLogEntity
import com.whocalled.android.data.PatternDao
import com.whocalled.android.data.ScoredNumberDao
import com.whocalled.android.data.UserRuleDao
import com.whocalled.android.util.PatternMatcher

enum class Action { BLOCK, WARN, ALLOW }

data class Decision(val action: Action, val score: Int, val category: String?)

/**
 * Pure decision logic — unit-testable, no Android dependencies in the algorithm.
 *
 * Priority:
 *   1. User allow rule  → always ALLOW
 *   2. User block rule  → always BLOCK
 *   3. Synced exact number → BLOCK if score >= threshold, else WARN, else ALLOW
 *   4. ARCEP / wildcard pattern → BLOCK or WARN per the pattern's status
 */
object ScreeningDecision {

    fun decide(
        phone: String?,
        blockThreshold: Int,
        warnEnabled: Boolean,
        userRuleDao: UserRuleDao,
        scoredNumberDao: ScoredNumberDao,
        patternDao: PatternDao,
    ): Decision {
        if (phone == null) return Decision(Action.ALLOW, 0, null)

        userRuleDao.findByPhone(phone)?.let { rule ->
            return when (rule.kind) {
                "allow" -> Decision(Action.ALLOW, 0, null)
                "block" -> Decision(Action.BLOCK, 100, "user")
                else -> Decision(Action.ALLOW, 0, null)
            }
        }

        scoredNumberDao.findByPhone(phone)?.let { scored ->
            return when {
                scored.spamScore >= blockThreshold || scored.status == "block" ->
                    Decision(Action.BLOCK, scored.spamScore, scored.category)
                warnEnabled && scored.status == "warn" ->
                    Decision(Action.WARN, scored.spamScore, scored.category)
                else -> Decision(Action.ALLOW, scored.spamScore, scored.category)
            }
        }

        // Fall back to wildcard patterns (ARCEP / operator ranges).
        PatternMatcher.firstMatch(phone, patternDao.all())?.let { p ->
            return when {
                p.status == "block" -> Decision(Action.BLOCK, 100, p.category)
                warnEnabled && p.status == "warn" -> Decision(Action.WARN, 70, p.category)
                else -> Decision(Action.ALLOW, 0, p.category)
            }
        }

        return Decision(Action.ALLOW, 0, null)
    }

    fun logEntry(phone: String, action: Action, score: Int, category: String?): CallLogEntity? = when (action) {
        Action.BLOCK -> CallLogEntity(phone = phone, action = "blocked", spamScore = score, category = category ?: "unknown", timestamp = System.currentTimeMillis())
        Action.WARN -> CallLogEntity(phone = phone, action = "warned", spamScore = score, category = category ?: "unknown", timestamp = System.currentTimeMillis())
        Action.ALLOW -> null
    }
}
