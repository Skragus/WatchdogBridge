package com.example.watchdogbridge.data

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.google.gson.Gson
import java.time.Instant

class HealthConnectRepository(private val context: Context) {

    private val TAG = "HealthConnectRepo"
    private val gson = Gson()
    
    private val healthConnectClient by lazy { 
        try {
            HealthConnectClient.getOrCreate(context)
        } catch (e: Exception) {
            Log.e(TAG, "Health Connect not available", e)
            throw e
        }
    }

    private val recordTypes = listOf(
        StepsRecord::class,
        SleepSessionRecord::class,
        HeartRateRecord::class,
        RestingHeartRateRecord::class,
        ExerciseSessionRecord::class,
        WeightRecord::class,
        BodyFatRecord::class,
        NutritionRecord::class,
        ActiveCaloriesBurnedRecord::class,
        TotalCaloriesBurnedRecord::class,
        DistanceRecord::class,
        HydrationRecord::class,
        BloodPressureRecord::class,
        Vo2MaxRecord::class,
        BasalMetabolicRateRecord::class,
        BodyWaterMassRecord::class,
        BoneMassRecord::class,
        LeanBodyMassRecord::class,
        FloorsClimbedRecord::class,
        ElevationGainedRecord::class,
        OxygenSaturationRecord::class,
        RespiratoryRateRecord::class,
        HeartRateVariabilityRmssdRecord::class,
        BodyTemperatureRecord::class,
        HeightRecord::class,
        BloodGlucoseRecord::class
    )

    suspend fun getPermissions(): Set<String> {
        val permissions = recordTypes.map { HealthPermission.getReadPermission(it) }.toMutableSet()
        permissions.add(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)
        if (Build.VERSION.SDK_INT >= 34) {
            permissions.add("android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND")
        }
        return permissions
    }

    suspend fun hasPermissions(): Boolean {
        return try {
            val permissions = getPermissions()
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            granted.containsAll(permissions)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions", e)
            false
        }
    }

    /**
     * Captures ALL records for the given time range as a single JSON string.
     */
    suspend fun readRawData(startTime: Long, endTime: Long): String {
        val rawData = mutableMapOf<String, List<Record>>()
        val filter = TimeRangeFilter.between(Instant.ofEpochMilli(startTime), Instant.ofEpochMilli(endTime))

        for (type in recordTypes) {
            try {
                val response = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        recordType = type,
                        timeRangeFilter = filter
                    )
                )
                if (response.records.isNotEmpty()) {
                    rawData[type.simpleName ?: "Unknown"] = response.records
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read raw data for ${type.simpleName}", e)
            }
        }
        return try {
            gson.toJson(rawData)
        } catch (e: Exception) {
            Log.e(TAG, "Error serializing raw Health Connect data", e)
            "{ \"error\": \"Serialization failed: ${e.message}\" }"
        }
    }
}
