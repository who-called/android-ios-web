package com.whocalled.android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScoredNumberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(numbers: List<ScoredNumberEntity>)

    @Query("SELECT * FROM scored_numbers WHERE phone = :phone LIMIT 1")
    fun findByPhone(phone: String): ScoredNumberEntity?

    @Query("SELECT MAX(updatedAt) FROM scored_numbers")
    suspend fun lastUpdatedAt(): Long?

    @Query("SELECT COUNT(*) FROM scored_numbers")
    suspend fun count(): Int

    @Query("DELETE FROM scored_numbers WHERE phone = :phone")
    suspend fun deleteByPhone(phone: String)

    @Query("DELETE FROM scored_numbers WHERE phone IN (:phones)")
    suspend fun deleteByPhones(phones: List<String>)

    @Query("DELETE FROM scored_numbers")
    suspend fun clear()
}

@Dao
interface PatternDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(patterns: List<PatternEntity>)

    @Query("SELECT * FROM patterns")
    fun all(): List<PatternEntity>

    @Query("SELECT MAX(updatedAt) FROM patterns")
    suspend fun lastUpdatedAt(): Long?
}

@Dao
interface UserRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: UserRuleEntity)

    @Query("SELECT * FROM user_rules WHERE phone = :phone LIMIT 1")
    fun findByPhone(phone: String): UserRuleEntity?

    @Query("SELECT * FROM user_rules ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<UserRuleEntity>>

    @Query("DELETE FROM user_rules WHERE phone = :phone")
    suspend fun deleteByPhone(phone: String)
}

@Dao
interface CallLogDao {
    /** Returns the auto-generated row id so callers can deep-link to the detail. */
    @Insert
    suspend fun insert(entry: CallLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<CallLogEntity>)

    @Query("SELECT * FROM call_log ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<CallLogEntity>>

    @Query("SELECT COUNT(*) FROM call_log WHERE action = 'blocked'")
    fun observeBlockedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM call_log WHERE action = 'warned'")
    fun observeWarnedCount(): Flow<Int>

    @Query("SELECT * FROM call_log WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): CallLogEntity?

    @Query("DELETE FROM call_log WHERE timestamp < :before")
    suspend fun pruneOlderThan(before: Long)
}

@Dao
interface MyReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(report: MyReportEntity)

    @Query("SELECT * FROM my_reports ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MyReportEntity>>

    @Query("SELECT * FROM my_reports WHERE syncState != 'synced'")
    suspend fun pending(): List<MyReportEntity>

    @Query("SELECT * FROM my_reports WHERE phone = :phone LIMIT 1")
    suspend fun byPhone(phone: String): MyReportEntity?

    @Query("DELETE FROM my_reports WHERE phone = :phone")
    suspend fun deleteByPhone(phone: String)

    @Query("DELETE FROM my_reports")
    suspend fun clear()
}
