package com.whocalled.android.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A scored number synced from the who-called backend (cache).
 * `status` drives screening behaviour: "block" or "warn".
 * Numbers are stored in E.164 without '+' (e.g. "33899123456").
 */
@Entity(tableName = "scored_numbers", indices = [Index(value = ["phone"], unique = true)])
data class ScoredNumberEntity(
    @PrimaryKey val phone: String,
    val status: String, // block | warn
    val spamScore: Int,
    val category: String,
    val updatedAt: Long,
)
