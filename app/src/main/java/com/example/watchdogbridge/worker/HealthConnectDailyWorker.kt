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

        Log.d(TAG, "Starting daily (14-day) sync work")
        
        try {
            val deviceId = preferencesRepository.getDeviceId()

            if (!healthConnectRepository.hasPermissions()) {
                Log.e(TAG, "Permissions missing")
                return Result.failure()
            }

            val today = LocalDate.now()
            val zoneId = ZoneId.systemDefault()

            // Iterate last 14 days (Yesterday back to T-14)
            for (i in 1..14) {
                val date = today.minusDays(i.toLong())
                val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                
                try {
                    val startOfDay = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
                    val endOfDay = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1

                    // Check Local State first to get attempt count
                    val lastState = syncStateDao.getSyncState(dateStr)

                    // Fetch Data
                    val steps = healthConnectRepository.readDailySteps(startOfDay, endOfDay)
                    val sleepSessions = healthConnectRepository.readSleepSessions(startOfDay, endOfDay)
                    val heartRateSummary = healthConnectRepository.readHeartRateSummary(startOfDay, endOfDay)
                    val exerciseSessions = healthConnectRepository.readExerciseSessions(startOfDay, endOfDay)
                    val bodyMetrics = healthConnectRepository.readBodyMetrics(startOfDay, endOfDay)
                    val nutritionSummary = healthConnectRepository.readNutritionSummary(startOfDay, endOfDay)

                    // Validation: Skip syncing a date if ALL major metrics are zero/empty
                    // This likely means Health Connect hasn't finished syncing or has no data yet.
                    if (steps == 0 && sleepSessions.isEmpty() && heartRateSummary == null) {
                        Log.w(TAG, "Skipping $dateStr - no data available yet (HC not synced?)")
                        continue
                    }

                    val request = DailyIngestRequest(
                        date = dateStr,
                        stepsTotal = steps,
                        sleepSessions = sleepSessions,
                        heartRateSummary = heartRateSummary,
                        exerciseSessions = exerciseSessions,
                        bodyMetrics = bodyMetrics,
                        nutritionSummary = nutritionSummary,
                        source = Source(
                            deviceId = deviceId,
                            collectedAt = LocalDateTime.now().atZone(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        )
                    )

                    // Compute Hash
                    val currentHash = DataHasher.computeHash(request)

                    if (lastState != null && lastState.dataHash == currentHash) {
                        Log.d(TAG, "Hash match for $dateStr, skipping.")
                        continue
                    }

                    // Send to API
                    Log.d(TAG, "Syncing $dateStr (Hash: $currentHash)...")
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
                    // Try to log error to DB
                    try {
                        val lastStateInner = syncStateDao.getSyncState(dateStr)
                        val errorState = lastStateInner?.let {
                            it.copy(
                                lastAttemptedAt = System.currentTimeMillis(),
                                lastError = e.message ?: "Unknown Exception",
                                attemptCount = it.attemptCount + 1
                            )
                        } ?: DailySyncState(
                            date = dateStr,
                            dataHash = "",
                            lastAttemptedAt = System.currentTimeMillis(),
                            lastError = e.message ?: "Unknown Exception",
                            attemptCount = 1
                        )
                        syncStateDao.insertOrUpdate(errorState)
                    } catch (dbEx: Exception) {
                        Log.e(TAG, "Failed to update DB error state", dbEx)
                    }
                }
            }

            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Global error in daily worker", e)
            return Result.failure()
        }
    }
}