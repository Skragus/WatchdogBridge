package com.example.watchdogbridge.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.watchdogbridge.worker.HealthConnectDailyWorker
import com.example.watchdogbridge.worker.HealthConnectIntradayWorker
import java.util.concurrent.TimeUnit

/**
 * BroadcastReceiver that listens for MacroDroid intents to restart health sync workers.
 * 
 * MacroDroid can send these intents to wake up stalled workers:
 * - com.example.watchdogbridge.RESTART_DAILY_WORKER
 * - com.example.watchdogbridge.RESTART_INTRADAY_WORKER
 * - com.example.watchdogbridge.RESTART_ALL_WORKERS
 */
class WorkerRestartReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WorkerRestartReceiver"
        const val ACTION_RESTART_DAILY = "com.example.watchdogbridge.RESTART_DAILY_WORKER"
        const val ACTION_RESTART_INTRADAY = "com.example.watchdogbridge.RESTART_INTRADAY_WORKER"
        const val ACTION_RESTART_ALL = "com.example.watchdogbridge.RESTART_ALL_WORKERS"
        private const val CHANNEL_ID = "worker_watchdog"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received intent: ${intent.action}")

        // Create notification channel (Android 8.0+)
        createNotificationChannel(context)

        when (intent.action) {
            ACTION_RESTART_DAILY -> {
                showNotification(context, "🔄 Restarting Daily Worker", "Watchdog triggered")
                restartDailyWorker(context)
            }
            ACTION_RESTART_INTRADAY -> {
                showNotification(context, "🔄 Restarting Intraday Worker", "Watchdog triggered")
                restartIntradayWorker(context)
            }
            ACTION_RESTART_ALL -> {
                showNotification(context, "🔄 Restarting All Workers", "Watchdog triggered")
                restartDailyWorker(context)
                restartIntradayWorker(context)
            }
            else -> {
                Log.w(TAG, "Unknown action: ${intent.action}")
                showNotification(context, "❌ Unknown Watchdog Action", intent.action ?: "null")
            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "Worker Watchdog",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications from worker watchdog"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(context: Context, title: String, text: String) {
        // Check for permission on Android 13+
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Notification permission missing. Cannot show notification.")
                return
            }
        }

        val notification = android.app.Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setTimeoutAfter(5000) // Auto-dismiss after 5 seconds
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun restartDailyWorker(context: Context) {
        Log.d(TAG, "Restarting daily worker...")
        
        val workManager = WorkManager.getInstance(context)
        
        val dailyConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        
        // Cancel existing work
        workManager.cancelUniqueWork("HealthConnectDailySync")
        
        // Recreate periodic work (6 hours interval, 30 min flex)
        val dailyWorkRequest = PeriodicWorkRequestBuilder<HealthConnectDailyWorker>(
            repeatInterval = 6,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
            flexTimeInterval = 30,
            flexTimeIntervalUnit = TimeUnit.MINUTES
        )
            .setConstraints(dailyConstraints)
            .addTag("daily_sync")
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            "HealthConnectDailySync",
            ExistingPeriodicWorkPolicy.REPLACE, // Using REPLACE to force a restart
            dailyWorkRequest
        )
        
        // Write timestamp for external monitoring
        writeRestartTimestamp(context, "daily")
        
        Log.d(TAG, "Daily worker restarted successfully")
    }

    private fun restartIntradayWorker(context: Context) {
        Log.d(TAG, "Restarting intraday worker...")
        
        val workManager = WorkManager.getInstance(context)

        val intradayConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        // Cancel existing work
        workManager.cancelUniqueWork("HealthConnectIntradaySync")
        
        // Recreate periodic work (60 min interval, 15 min flex)
        val intradayWorkRequest = PeriodicWorkRequestBuilder<HealthConnectIntradayWorker>(
            repeatInterval = 60,
            repeatIntervalTimeUnit = TimeUnit.MINUTES,
            flexTimeInterval = 15,
            flexTimeIntervalUnit = TimeUnit.MINUTES
        )
            .setConstraints(intradayConstraints)
            .addTag("intraday_sync")
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            "HealthConnectIntradaySync",
            ExistingPeriodicWorkPolicy.REPLACE, // Using REPLACE to force a restart
            intradayWorkRequest
        )
        
        // Write timestamp for external monitoring
        writeRestartTimestamp(context, "intraday")
        
        Log.d(TAG, "Intraday worker restarted successfully")
    }

    private fun writeRestartTimestamp(context: Context, workerType: String) {
        try {
            val file = java.io.File(context.getExternalFilesDir(null), "watchdog_restart_$workerType.txt")
            file.writeText(System.currentTimeMillis().toString())
            Log.d(TAG, "Wrote restart timestamp for $workerType to ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write restart timestamp: ${e.message}")
        }
    }
}
