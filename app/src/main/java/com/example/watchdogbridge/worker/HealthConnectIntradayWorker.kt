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
import java.time.Instant
import java.time.LocalDate
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
        if (BuildConfig.WORKER_PROOF_OF_LIFE_ENABLED) {
            Log.d(TAG, "Proof of Life: Intraday Worker Ran.")
            NotificationUtil.postProofOfLifeNotification(applicationContext, "Intraday Worker")
            return Result.success()
        }

        val startTimeMillis = System.currentTimeMillis()
        Log.i(TAG, "Starting intraday sync at $startTimeMillis")

        try {
            val deviceId = preferencesRepository.getDeviceId()
            val today = LocalDate.now()
            val dateStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val zoneId = ZoneId.systemDefault()
            
            val startOfDay = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val now = System.currentTimeMillis()

            if (!healthConnectRepository.hasPermissions()) {
                Log.w(TAG, "Permissions missing")
                return Result.retry()
            }

            Log.d(TAG, "Querying Health Connect for $dateStr (From: ${Instant.ofEpochMilli(startOfDay)} To: ${Instant.ofEpochMilli(now)})")

            // Fetch Raw Data Blob
            val rawJson = healthConnectRepository.readRawData(startOfDay, now)

            // We no longer skip if rawJson is "{}" because "no data is still data" for today.
            // The DataHasher will handle skipping if the "no data" state hasn't changed.

            val request = DailyIngestRequest(
                date = dateStr,
                rawJson = rawJson,
                source = Source(
                    deviceId = deviceId,
                    collectedAt = Instant.now().toString()
                )
            )

            val currentHash = DataHasher.computeHash(request)
            
            val lastState = syncStateDao.getSyncState(dateStr)
            if (lastState != null && lastState.dataHash == currentHash) {
                Log.i(TAG, "Data unchanged for $dateStr, skipping upload.")
                updateLastRunTime()
                return Result.success()
            }

            // Send to API
            Log.d(TAG, "Sending intraday payload to API...")
            val response = NetworkClient.api.postIntraday(request)
            
            if (response.isSuccessful) {
                val newState = DailySyncState(
                    date = dateStr,
                    dataHash = currentHash,
                    lastSyncedAt = System.currentTimeMillis(),
                    attemptCount = 0
                )
                syncStateDao.insertOrUpdate(newState)
                Log.i(TAG, "Intraday sync successful for $dateStr (Code: ${response.code()})")
                updateLastRunTime()
                return Result.success()
            } else {
                Log.e(TAG, "Server error: ${response.code()} ${response.message()}")
                return Result.retry()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in intraday worker: ${e.message}", e)
            return Result.retry()
        } finally {
            updateLastRunTime()
        }
    }

    private fun updateLastRunTime() {
        preferencesRepository.setLastIntradayRun(System.currentTimeMillis())
    }
}
