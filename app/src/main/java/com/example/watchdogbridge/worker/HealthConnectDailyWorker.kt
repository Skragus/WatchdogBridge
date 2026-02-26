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
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

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

        val startStr = inputData.getString("start_date")
        val endStr = inputData.getString("end_date")
        val customDays = inputData.getInt("days", 7)

        val today = LocalDate.now()
        val backfillStartDate = if (startStr != null) LocalDate.parse(startStr) else today.minusDays(customDays.toLong())
        val backfillEndDate = if (endStr != null) LocalDate.parse(endStr) else today

        Log.i(TAG, "Starting daily sync range: $backfillStartDate to $backfillEndDate")
        
        try {
            val deviceId = preferencesRepository.getDeviceId()
            if (!healthConnectRepository.hasPermissions()) {
                Log.e(TAG, "Permissions missing")
                return Result.failure()
            }

            val zoneId = ZoneId.systemDefault()
            val daysToSync = ChronoUnit.DAYS.between(backfillStartDate, backfillEndDate).toInt()

            for (i in 0..daysToSync) {
                val date = backfillEndDate.minusDays(i.toLong())
                val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                
                try {
                    val startOfDay = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
                    val endOfDay = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1

                    val lastState = syncStateDao.getSyncState(dateStr)
                    val rawJson = healthConnectRepository.readRawData(startOfDay, endOfDay)

                    if (rawJson == "{}" || rawJson.isEmpty() || rawJson.contains("Serialization failed")) {
                        continue
                    }

                    val request = DailyIngestRequest(
                        date = dateStr,
                        rawJson = rawJson,
                        source = Source(
                            deviceId = deviceId,
                            collectedAt = Instant.now().toString()
                        )
                    )

                    val currentHash = DataHasher.computeHash(request)
                    if (lastState != null && lastState.dataHash == currentHash) {
                        continue
                    }

                    Log.d(TAG, "Syncing $dateStr...")
                    val response = NetworkClient.api.postDaily(request)
                    
                    if (response.isSuccessful) {
                        Log.i(TAG, "Successfully synced $dateStr (Code: ${response.code()})")
                        val newState = DailySyncState(
                            date = dateStr,
                            dataHash = currentHash,
                            lastSyncedAt = System.currentTimeMillis(),
                            attemptCount = 0,
                            lastError = null
                        )
                        syncStateDao.insertOrUpdate(newState)
                    } else {
                        Log.e(TAG, "Failed for $dateStr: ${response.code()} ${response.message()}")
                    }

                    delay(300)

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
