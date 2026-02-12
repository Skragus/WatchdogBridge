package com.example.watchdogbridge.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DailySyncStateDao {
    @Query("SELECT * FROM daily_sync_state WHERE date = :date")
    suspend fun getSyncState(date: String): DailySyncState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(state: DailySyncState)

    @Query("SELECT * FROM daily_sync_state")
    suspend fun getAll(): List<DailySyncState>
}