package com.example.watchdogbridge.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.example.watchdogbridge.data.PreferencesRepository
import com.example.watchdogbridge.data.SamsungHealthRepository
import com.example.watchdogbridge.data.model.DailyIngestRequest
import com.example.watchdogbridge.data.model.Source
import com.example.watchdogbridge.network.NetworkClient
import java.net.SocketTimeoutException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class SamsungHealthDailyWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val preferencesRepository = PreferencesRepository(appContext)
    private val samsungHealthRepository = SamsungHealthRepository(appContext)

    override suspend fun doWork(): Result {
        try {
            // 1. Ensure device_id exists
            val deviceId = preferencesRepository.getDeviceId()

            // 2. Calculate local "yesterday"
            val today = LocalDate.now()
            val yesterday = today.minusDays(1)
            val yesterdayDateStr = yesterday.format(DateTimeFormatter.ISO_LOCAL_DATE)

            val zoneId = ZoneId.systemDefault()
            val startOfDay = yesterday.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val endOfDay = today.atStartOfDay(zoneId).toInstant().toEpochMilli() - 1

            // 3. Connect to Samsung Health
            val connected = samsungHealthRepository.connect()
            if (!connected) {
                return Result.retry()
            }

            // 4. Query Data
            val steps = samsungHealthRepository.readDailySteps(startOfDay, endOfDay)
            val sleepSessions = samsungHealthRepository.readSleepSessions(startOfDay, endOfDay)
            val heartRateSummary = samsungHealthRepository.readHeartRateSummary(startOfDay, endOfDay)

            // 5. Build Payload
            val request = DailyIngestRequest(
                date = yesterdayDateStr,
                stepsTotal = steps,
                sleepSessions = sleepSessions,
                heartRateSummary = heartRateSummary,
                source = Source(
                    deviceId = deviceId,
                    collectedAt = LocalDateTime.now().atZone(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                )
            )

            // 6. Send to API
            val response = try {
                NetworkClient.api.postDaily(request)
            } catch (e: SocketTimeoutException) {
                val errorData = Data.Builder().putString("error", "timeout").build()
                return Result.failure(errorData)
            }
            
            samsungHealthRepository.disconnect()

            return if (response.isSuccessful) {
                Result.success()
            } else {
                val errorData = Data.Builder().putString("error", "server_error").build()
                Result.failure(errorData)
            }
        } catch (e: Exception) {
            val errorData = Data.Builder().putString("error", "unknown").build()
            return Result.failure(errorData)
        }
    }
}