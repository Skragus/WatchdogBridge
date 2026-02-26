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
        Log.i(TAG, "Starting agnostic intraday sync at $startTimeMillis")

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

            // Fetch Raw Data Blob
            val rawJson = healthConnectRepository.readRawData(startOfDay, now)

            if (rawJson == "{}" || rawJson.isEmpty()) {
                Log.i(TAG, "No data for today yet, skipping.")
                updateLastRunTime()
                return Result.success()
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
            
            val lastState = syncStateDao.getSyncState(dateStr)
            if (lastState != null && lastState.dataHash == currentHash) {
                Log.i(TAG, "Data unchanged for $dateStr, skipping upload.")
                updateLastRunTime()
                return Result.success()
            }

            // Send to API
            val response = NetworkClient.api.postIntraday(request)
            
            if (response.isSuccessful) {
                val newState = DailySyncState(
                    date = dateStr,
                    dataHash = currentHash,
                    lastSyncedAt = System.currentTimeMillis(),
                    attemptCount = 0
                )
                syncStateDao.insertOrUpdate(newState)
                Log.i(TAG, "Agnostic intraday sync successful.")
                updateLastRunTime()
                return Result.success()
            } else {
                Log.e(TAG, "Server error: ${response.code()}")
                return Result.retry()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in intraday worker", e)
            return Result.retry()
        } finally {
            preferencesRepository.setLastIntradayRun(System.currentTimeMillis())
        }
    }

    private fun updateLastRunTime() {
        preferencesRepository.setLastIntradayRun(System.currentTimeMillis())
    }
}
