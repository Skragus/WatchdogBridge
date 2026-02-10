package com.example.watchdogbridge.data

import android.app.Activity
import android.content.Context
import android.util.Log
import com.example.watchdogbridge.data.model.HeartRateSummary
import com.example.watchdogbridge.data.model.SleepSession
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.permission.AccessType

class SamsungHealthRepository(private val context: Context) {

    private val TAG = "SamsungHealthDebug"
    private var healthDataStore: HealthDataStore? = null

    fun connect(): Boolean {
        Log.d(TAG, "connect() called")
        return try {
            Log.d(TAG, "Calling HealthDataService.getStore()...")
            healthDataStore = HealthDataService.getStore(context)
            Log.d(TAG, "HealthDataService.getStore() returned: $healthDataStore")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Exception in connect()", e)
            false
        }
    }

    fun disconnect() {
        Log.d(TAG, "disconnect() called")
        healthDataStore = null
    }

    suspend fun requestPermissions(activity: Activity): Boolean {
        Log.d(TAG, "requestPermissions() called")
        val store = healthDataStore ?: run {
            Log.w(TAG, "HealthDataStore is null, cannot request permissions.")
            return false
        }
        
        val permissions = setOf(
            Permission.of(DataTypes.STEPS, AccessType.READ),
            Permission.of(DataTypes.SLEEP, AccessType.READ),
            Permission.of(DataTypes.HEART_RATE, AccessType.READ)
        )
        Log.d(TAG, "Requesting permissions: $permissions")

        try {
            Log.d(TAG, "Calling store.getGrantedPermissions()...")
            val granted = store.getGrantedPermissions(permissions)
            Log.d(TAG, "getGrantedPermissions() returned: $granted")

            if (granted.containsAll(permissions)) {
                Log.d(TAG, "All permissions already granted.")
                return true
            }

            Log.d(TAG, "Calling store.requestPermissions()...")
            store.requestPermissions(permissions, activity)
            Log.d(TAG, "requestPermissions() call completed.")
            return true
        } catch (e: Throwable) {
            Log.e(TAG, "Exception in requestPermissions()", e)
            return false
        }
    }

    suspend fun readDailySteps(startTime: Long, endTime: Long): Int {
        Log.d(TAG, "readDailySteps() called")
        val store = healthDataStore ?: run {
            Log.w(TAG, "HealthDataStore is null, cannot read steps.")
            return 0
        }
        
        val start = LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneId.systemDefault())
        val end = LocalDateTime.ofInstant(Instant.ofEpochMilli(endTime), ZoneId.systemDefault())

        return try {
            val filter = LocalTimeFilter.of(start, end)
            val request = DataType.StepsType.TOTAL.requestBuilder
                .setLocalTimeFilter(filter)
                .build()
            Log.d(TAG, "Built aggregate request for steps.")

            Log.d(TAG, "Calling store.aggregateData() for steps...")
            val result = store.aggregateData(request)
            Log.d(TAG, "aggregateData() for steps returned.")
            
            val totalSteps = result.dataList.sumOf { 
                (it.value as? Number)?.toLong() ?: 0L
            }
            Log.d(TAG, "Calculated total steps: $totalSteps")
            return totalSteps.toInt()
        } catch (e: Throwable) {
            Log.e(TAG, "Exception in readDailySteps()", e)
            return 0
        }
    }

    suspend fun readSleepSessions(startTime: Long, endTime: Long): List<SleepSession> {
        Log.d(TAG, "readSleepSessions() called")
        val store = healthDataStore ?: run {
            Log.w(TAG, "HealthDataStore is null, cannot read sleep.")
            return emptyList()
        }

        val start = LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneId.systemDefault())
        val end = LocalDateTime.ofInstant(Instant.ofEpochMilli(endTime), ZoneId.systemDefault())

        try {
            val filter = LocalTimeFilter.of(start, end)
            val request = DataTypes.SLEEP.readDataRequestBuilder
                .setLocalTimeFilter(filter)
                .build()
            Log.d(TAG, "Built read request for sleep.")

            Log.d(TAG, "Calling store.readData() for sleep...")
            val result = store.readData(request)
            Log.d(TAG, "readData() for sleep returned.")
            
            val sessions = result.dataList.map { dp ->
                val duration = dp.getValue(DataType.SleepType.DURATION)
                val durationMinutes = duration?.toMinutes()?.toInt() ?: 0
                
                SleepSession(
                    startTime = dp.startTime.toString(),
                    endTime = dp.endTime.toString(),
                    durationMinutes = durationMinutes
                )
            }
            Log.d(TAG, "Mapped ${sessions.size} sleep sessions.")
            return sessions
        } catch (e: Throwable) {
            Log.e(TAG, "Exception in readSleepSessions()", e)
            return emptyList()
        }
    }

    suspend fun readHeartRateSummary(startTime: Long, endTime: Long): HeartRateSummary? {
        Log.d(TAG, "readHeartRateSummary() called")
        val store = healthDataStore ?: run {
            Log.w(TAG, "HealthDataStore is null, cannot read heart rate.")
            return null
        }

        val start = LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneId.systemDefault())
        val end = LocalDateTime.ofInstant(Instant.ofEpochMilli(endTime), ZoneId.systemDefault())

        try {
            val filter = LocalTimeFilter.of(start, end)
            val request = DataTypes.HEART_RATE.readDataRequestBuilder
                .setLocalTimeFilter(filter)
                .build()
            Log.d(TAG, "Built read request for heart rate.")

            Log.d(TAG, "Calling store.readData() for heart rate...")
            val result = store.readData(request)
            Log.d(TAG, "readData() for heart rate returned.")

            val points = result.dataList
            if (points.isEmpty()) {
                Log.d(TAG, "No heart rate data points found.")
                return null
            }
            
            var sum = 0.0
            var count = 0
            
            for (point in points) {
                val hr: Float? = point.getValue(DataType.HeartRateType.HEART_RATE)
                if (hr != null && hr > 0) {
                    sum += hr
                    count++
                }
            }
            
            return if (count > 0) {
                val summary = HeartRateSummary(restingHr = (sum / count).toInt())
                Log.d(TAG, "Calculated heart rate summary: $summary")
                summary
            } else {
                Log.d(TAG, "No valid heart rate points to calculate summary.")
                null
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Exception in readHeartRateSummary()", e)
            return null
        }
    }
}