package com.example.watchdogbridge.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.watchdogbridge.data.HealthConnectRepository
import com.example.watchdogbridge.data.PreferencesRepository
import com.example.watchdogbridge.data.local.AppDatabase
import com.example.watchdogbridge.data.local.DailySyncState
import com.example.watchdogbridge.data.model.DailyIngestRequest
import com.example.watchdogbridge.data.model.Source
import com.example.watchdogbridge.network.NetworkClient
import com.example.watchdogbridge.util.DataHasher
import com.example.watchdogbridge.BuildConfig
import com.example.watchdogbridge.util.NotificationUtil
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HealthConnectDailyWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val preferencesRepository = PreferencesRepository(appContext)
    private val healthConnectRepository = HealthConnectRepository(appContext)
    private val syncStateDao = AppDatabase.getDatabase(appContext).dailySyncStateDao()
    private val TAG = "HCDailyWorker"

    override suspend fun doWork(): Result {
        if (BuildConfig.WORKER_PROOF_OF_LIFE_ENABLED) {
            Log.d(TAG, "Proof of Life: Daily Worker Ran.")
            NotificationUtil.postProofOfLifeNotification(applicationContext, "Daily Worker")
            return Result.success()
        }

        Log.d(TAG, "Starting daily (30-day) sync work")
        
        try {
            val deviceId = preferencesRepository.getDeviceId()

            if (!healthConnectRepository.hasPermissions()) {
                Log.e(TAG, "Permissions missing")
                return Result.failure()
            }

            val today = LocalDate.now()
            val zoneId = ZoneId.systemDefault()

            for (i in 1..30) {
                val date = today.minusDays(i.toLong())
                val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                
                try {
                    Log.d(TAG, "Checking data for $dateStr (i=$i)...")
                    val startOfDay = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
                    val endOfDay = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1

                    val lastState = syncStateDao.getSyncState(dateStr)

                    // Fetch Raw Data Blob (Agnostic Approach)
                    val rawJson = healthConnectRepository.readRawData(startOfDay, endOfDay)

                    // Skip if the blob is empty (no data found for this day)
                    if (rawJson == "{}" || rawJson.isEmpty()) {
                        Log.d(TAG, "No data found for $dateStr, skipping.")
                        continue
                    }

                    val request = DailyIngestRequest(
                        date = dateStr,
                        rawJson = rawJson,
                        source = Source(
                            deviceId = deviceId,
                            collectedAt = LocalDateTime.now().atZone(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        )
                    )

                    val currentHash = DataHasher.computeHash(request)
                    Log.d(TAG, "Computed Hash for $dateStr: $currentHash")

                    // Send to API
                    Log.d(TAG, "Syncing $dateStr...")
                    val response = NetworkClient.api.postDaily(request)
                    
                    if (response.isSuccessful) {
                        val newState = DailySyncState(
                            date = dateStr,
                            dataHash = currentHash,
                            lastSyncedAt = System.currentTimeMillis(),
                            attemptCount = 0,
                            lastError = null
                        )
                        syncStateDao.insertOrUpdate(newState)
                        Log.d(TAG, "Sync successful for $dateStr")
                    } else {
                        Log.e(TAG, "Failed for $dateStr: ${response.code()}")
                        val errorState = lastState?.let {
                            it.copy(
                                lastAttemptedAt = System.currentTimeMillis(),
                                lastError = "HTTP ${response.code()}",
                                attemptCount = it.attemptCount + 1
                            )
                        } ?: DailySyncState(
                            date = dateStr,
                            dataHash = currentHash,
                            lastAttemptedAt = System.currentTimeMillis(),
                            lastError = "HTTP ${response.code()}",
                            attemptCount = 1
                        )
                        syncStateDao.insertOrUpdate(errorState)
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error processing $dateStr", e)
                }
            }

            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Global error in daily worker", e)
            return Result.failure()
        }
    }
}
