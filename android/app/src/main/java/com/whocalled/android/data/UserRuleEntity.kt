package com.whocalled.android.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-defined rule that overrides the synced list.
 * `kind` = "allow" (never block, e.g. after unblocking) or "block" (always block).
 * Phone in E.164 without '+'.
 */
@Entity(tableName = "user_rules", indices = [Index(value = ["phone"], unique = true)])
data class UserRuleEntity(
    @PrimaryKey val phone: String,
    val kind: String, // allow | block
    val createdAt: Long,
)
