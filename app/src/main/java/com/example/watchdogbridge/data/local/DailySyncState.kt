package com.example.watchdogbridge.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_sync_state")
data class DailySyncState(
    @PrimaryKey val date: String, // YYYY-MM-DD
    val dataHash: String,
    val lastSyncedAt: Long = 0,
    val lastAttemptedAt: Long = 0,
    val lastError: String? = null,
    val attemptCount: Int = 0
)