package com.whocalled.android.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A report the user has made (kept locally so they can review / change / delete it).
 * Source of truth for "Mes signalements". Synced best-effort to the API.
 *
 * `syncState`: pending | synced | failed  — lets us retry and show status.
 */
@Entity(tableName = "my_reports", indices = [Index(value = ["phone"], unique = true)])
data class MyReportEntity(
    @PrimaryKey val phone: String, // E.164 without '+'
    val vote: String, // spam | legit
    val category: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val syncState: String, // pending | synced | failed
)
