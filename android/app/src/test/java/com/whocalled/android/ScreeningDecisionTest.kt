package com.whocalled.android

import com.whocalled.android.data.PatternDao
import com.whocalled.android.data.PatternEntity
import com.whocalled.android.data.ScoredNumberDao
import com.whocalled.android.data.ScoredNumberEntity
import com.whocalled.android.data.UserRuleDao
import com.whocalled.android.data.UserRuleEntity
import com.whocalled.android.service.Action
import com.whocalled.android.service.ScreeningDecision
import com.whocalled.android.util.PhoneNormalizer
import kotlinx.coroutines.flow.Flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class FakeScoredDao(private val map: Map<String, ScoredNumberEntity>) : ScoredNumberDao {
    override suspend fun upsertAll(numbers: List<ScoredNumberEntity>) {}
    override fun findByPhone(phone: String): ScoredNumberEntity? = map[phone]
    override suspend fun lastUpdatedAt(): Long? = null
    override suspend fun count(): Int = map.size
    override suspend fun deleteByPhone(phone: String) {}
}

private class FakeRuleDao(private val map: Map<String, UserRuleEntity>) : UserRuleDao {
    override suspend fun upsert(rule: UserRuleEntity) {}
    override fun findByPhone(phone: String): UserRuleEntity? = map[phone]
    override fun observeAll(): Flow<List<UserRuleEntity>> = throw NotImplementedError()
    override suspend fun deleteByPhone(phone: String) {}
}

private class FakePatternDao(private val list: List<PatternEntity>) : PatternDao {
    override suspend fun upsertAll(patterns: List<PatternEntity>) {}
    override fun all(): List<PatternEntity> = list
    override suspend fun lastUpdatedAt(): Long? = null
}

class ScreeningDecisionTest {

    private fun decide(
        phone: String?,
        scored: Map<String, ScoredNumberEntity> = emptyMap(),
        rules: Map<String, UserRuleEntity> = emptyMap(),
        patterns: List<PatternEntity> = emptyList(),
        blockThreshold: Int = 85,
        warnEnabled: Boolean = true,
    ) = ScreeningDecision.decide(
        phone, blockThreshold, warnEnabled,
        FakeRuleDao(rules), FakeScoredDao(scored), FakePatternDao(patterns),
    )

    @Test fun `high score blocks`() {
        val d = decide("33899123456", mapOf("33899123456" to num("33899123456", "block", 95)))
        assertEquals(Action.BLOCK, d.action)
    }

    @Test fun `medium score warns`() {
        val d = decide("33162987654", mapOf("33162987654" to num("33162987654", "warn", 70)))
        assertEquals(Action.WARN, d.action)
    }

    @Test fun `warn disabled falls back to allow`() {
        val d = decide(
            "33162987654",
            mapOf("33162987654" to num("33162987654", "warn", 70)),
            warnEnabled = false,
        )
        assertEquals(Action.ALLOW, d.action)
    }

    @Test fun `unknown number is allowed`() {
        assertEquals(Action.ALLOW, decide("33600000000").action)
    }

    @Test fun `user allow rule overrides block status`() {
        val d = decide(
            "33899123456",
            scored = mapOf("33899123456" to num("33899123456", "block", 99)),
            rules = mapOf("33899123456" to UserRuleEntity("33899123456", "allow", 0)),
        )
        assertEquals(Action.ALLOW, d.action)
    }

    @Test fun `user block rule forces block`() {
        val d = decide(
            "33600000000",
            rules = mapOf("33600000000" to UserRuleEntity("33600000000", "block", 0)),
        )
        assertEquals(Action.BLOCK, d.action)
    }

    @Test fun `null phone allowed`() {
        assertNull(ScreeningDecision.logEntry("x", decide(null).action, 0, null))
    }

    @Test fun `arcep block pattern matches and blocks`() {
        val d = decide(
            "33899000000",
            patterns = listOf(PatternEntity("33899######", "block", "telemarketing", "arcep", "ARCEP", 0)),
        )
        assertEquals(Action.BLOCK, d.action)
    }

    @Test fun `arcep warn pattern alerts when warn enabled`() {
        val d = decide(
            "33162000000",
            patterns = listOf(PatternEntity("33162######", "warn", "telemarketing", "arcep", "ARCEP", 0)),
        )
        assertEquals(Action.WARN, d.action)
    }

    @Test fun `pattern of different length does not match`() {
        val d = decide(
            "3389900",
            patterns = listOf(PatternEntity("33899######", "block", "telemarketing", "arcep", null, 0)),
        )
        assertEquals(Action.ALLOW, d.action)
    }

    @Test fun `exact number takes priority over pattern`() {
        val d = decide(
            "33899123456",
            scored = mapOf("33899123456" to num("33899123456", "warn", 65)),
            patterns = listOf(PatternEntity("33899######", "block", "telemarketing", "arcep", null, 0)),
        )
        assertEquals(Action.WARN, d.action) // scored number wins, not the block pattern
    }

    @Test fun `normalizer converts FR national to E164`() {
        assertEquals("33612345678", PhoneNormalizer.normalize("0612345678"))
        assertEquals("33612345678", PhoneNormalizer.normalize("+33 6 12 34 56 78"))
        assertEquals("33612345678", PhoneNormalizer.normalize("0033612345678"))
        assertNull(PhoneNormalizer.normalize("abc"))
    }

    private fun num(phone: String, status: String, score: Int) =
        ScoredNumberEntity(phone, status, score, "telemarketing", 0L)
}
