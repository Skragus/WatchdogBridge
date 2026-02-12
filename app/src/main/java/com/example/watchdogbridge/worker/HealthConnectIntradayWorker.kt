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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HealthConnectIntradayWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val preferencesRepository = PreferencesRepository(appContext)
    private val healthConnectRepository = HealthConnectRepository(appContext)
    private val syncStateDao = AppDatabase.getDatabase(appContext).dailySyncStateDao()
    private val TAG = "HCIntradayWorker"

    override suspend fun doWork(): Result {
        try {
            Log.d(TAG, "Starting intraday sync")
            val deviceId = preferencesRepository.getDeviceId()

            // Target: Today
            val today = LocalDate.now()
            val dateStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val zoneId = ZoneId.systemDefault()
            
            // Start of day until NOW
            val startOfDay = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val now = System.currentTimeMillis()

            if (!healthConnectRepository.hasPermissions()) {
                Log.d(TAG, "Permissions missing, skipping")
                return Result.failure()
            }

            // Fetch Data
            val steps = healthConnectRepository.readDailySteps(startOfDay, now)
            val sleepSessions = healthConnectRepository.readSleepSessions(startOfDay, now)
            val heartRateSummary = healthConnectRepository.readHeartRateSummary(startOfDay, now)
            val exerciseSessions = healthConnectRepository.readExerciseSessions(startOfDay, now)
            val bodyMetrics = healthConnectRepository.readBodyMetrics(startOfDay, now)
            val nutritionSummary = healthConnectRepository.readNutritionSummary(startOfDay, now)

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
            
            // Check Local State
            val lastState = syncStateDao.getSyncState(dateStr)
            if (lastState != null && lastState.dataHash == currentHash) {
                Log.d(TAG, "Data unchanged for $dateStr, skipping upload.")
                return Result.success()
            }

            // Send to API
            Log.d(TAG, "Sending intraday data to API... Hash: $currentHash")
            val response = NetworkClient.api.postIntraday(request)
            
            if (response.isSuccessful) {
                // Update State
                val newState = DailySyncState(
                    date = dateStr,
                    dataHash = currentHash,
                    lastSyncedAt = System.currentTimeMillis(),
                    attemptCount = 0
                )
                syncStateDao.insertOrUpdate(newState)
                Log.d(TAG, "Intraday sync successful")
                return Result.success()
            } else {
                Log.e(TAG, "Server error: ${response.code()} ${response.message()}")
                // Ideally update 'lastError' in DB here too
                return Result.retry()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in intraday worker", e)
            return Result.failure()
        }
    }
}