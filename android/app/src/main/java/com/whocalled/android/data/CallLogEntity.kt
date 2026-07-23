package com.whocalled.android.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local journal of screened calls (blocked or warned), for the history screen.
 */
@Entity(tableName = "call_log")
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phone: String,
    val action: String, // blocked | warned
    val spamScore: Int,
    val category: String = "unknown",
    val timestamp: Long,
)
