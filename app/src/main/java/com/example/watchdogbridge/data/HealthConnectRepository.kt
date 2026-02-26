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
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import kotlinx.coroutines.delay
import java.lang.reflect.Type
import java.time.Instant

class HealthConnectRepository(private val context: Context) {

    private val TAG = "HealthConnectRepo"
    
    private val gson = GsonBuilder()
        .registerTypeAdapter(Instant::class.java, object : JsonSerializer<Instant> {
            override fun serialize(src: Instant?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
                return JsonPrimitive(src?.toString())
            }
        })
        .create()
    
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

    suspend fun readRawData(startTime: Long, endTime: Long): String {
        Log.d(TAG, "Reading raw data: ${Instant.ofEpochMilli(startTime)} -> ${Instant.ofEpochMilli(endTime)}")
        val rawData = mutableMapOf<String, List<Record>>()
        val filter = TimeRangeFilter.between(Instant.ofEpochMilli(startTime), Instant.ofEpochMilli(endTime))
        var errorCount = 0

        for (type in recordTypes) {
            try {
                // Increased delay to 800ms between types
                delay(800) 
                
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
                errorCount++
                if (e.message?.contains("quota exceeded", ignoreCase = true) == true) {
                    Log.w(TAG, "Quota hit for ${type.simpleName}, 40s pause...")
                    delay(40000) // Increased back-off
                } else {
                    Log.w(TAG, "Read failed for ${type.simpleName}", e)
                }
            }
        }

        if (rawData.isEmpty() && errorCount > 0) {
            throw IllegalStateException("Sync failed: all API calls hit quota limits")
        }

        return gson.toJson(rawData)
    }
}
