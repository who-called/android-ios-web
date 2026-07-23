package com.whocalled.android.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A wildcard pattern (ARCEP / operator ranges), e.g. "33899######" where '#'
 * matches any single digit. Synced from the backend, cached locally.
 */
@Entity(tableName = "patterns")
data class PatternEntity(
    @PrimaryKey val pattern: String,
    val status: String, // block | warn
    val category: String,
    val source: String, // arcep | community
    val name: String?,
    val updatedAt: Long,
)
