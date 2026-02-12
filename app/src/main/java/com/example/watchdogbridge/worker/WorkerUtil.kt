package com.example.watchdogbridge.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkerUtil {
    private const val TAG = "WorkerUtil"

    fun scheduleWorkers(context: Context) {
        Log.d(TAG, "Scheduling workers...")

        val dailyConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val intradayConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            // Battery constraint removed for intraday to ensure execution
            .build()

        // 1. Intraday Worker (Every 60 mins)
        val intradayRequest = PeriodicWorkRequestBuilder<HealthConnectIntradayWorker>(
            60, TimeUnit.MINUTES,
            15, TimeUnit.MINUTES // Flex interval
        )
            .setConstraints(intradayConstraints)
            .addTag("intraday_sync")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "HealthConnectIntradaySync",
            ExistingPeriodicWorkPolicy.UPDATE,
            intradayRequest
        )
        Log.d(TAG, "Intraday worker scheduled with UPDATE policy")

        // 2. Daily Sync Worker (Every 6 hours)
        val dailyRequest = PeriodicWorkRequestBuilder<HealthConnectDailyWorker>(
            6, TimeUnit.HOURS,
            30, TimeUnit.MINUTES
        )
            .setConstraints(dailyConstraints)
            .addTag("daily_sync")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "HealthConnectDailySync",
            ExistingPeriodicWorkPolicy.UPDATE, // Update to apply new constraints/intervals if changed
            dailyRequest
        )
        Log.d(TAG, "Daily worker scheduled")
    }
}
