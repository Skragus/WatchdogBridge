package com.example.watchdogbridge.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.watchdogbridge.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NotificationUtil {
    private const val CHANNEL_ID = "worker_proof_of_life"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Worker Status"
            val descriptionText = "Notifications for worker proof of life"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun postProofOfLifeNotification(context: Context, workerName: String) {
        // Ensure channel is created
        createNotificationChannel(context)

        val timeStamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val contentText = "$workerName proof of life triggered at $timeStamp"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with a real icon
            .setContentTitle("Worker Ran")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // If permission is not granted, we can't post.
                // The main app flow handles requesting this permission.
                return
            }
            // notificationId is a unique int for each notification that you must define
            val notificationId = (System.currentTimeMillis() % 10000).toInt()
            notify(notificationId, builder.build())
        }
    }
}
