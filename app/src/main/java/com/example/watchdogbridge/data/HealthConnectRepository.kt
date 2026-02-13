package com.example.watchdogbridge.data

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.watchdogbridge.data.model.BodyMetrics
import com.example.watchdogbridge.data.model.ExerciseSession
import com.example.watchdogbridge.data.model.HeartRateSummary
import com.example.watchdogbridge.data.model.NutritionSummary
import com.example.watchdogbridge.data.model.SleepSession
import java.time.Duration
import java.time.Instant

class HealthConnectRepository(private val context: Context) {

    private val TAG = "HealthConnectRepo"
    
    private val healthConnectClient by lazy { 
        try {
            HealthConnectClient.getOrCreate(context)
        } catch (e: Exception) {
            Log.e(TAG, "Health Connect not available", e)
            throw e
        }
    }

    suspend fun getPermissions(): Set<String> {
        return setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(BodyFatRecord::class),
            HealthPermission.getReadPermission(NutritionRecord::class)
        )
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

    suspend fun readDailySteps(startTime: Long, endTime: Long): Int {
        return try {
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(
                        Instant.ofEpochMilli(startTime),
                        Instant.ofEpochMilli(endTime)
                    )
                )
            )
            response[StepsRecord.COUNT_TOTAL]?.toInt() ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Error reading steps", e)
            0
        }
    }

    suspend fun readSleepSessions(startTime: Long, endTime: Long): List<SleepSession> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        Instant.ofEpochMilli(startTime),
                        Instant.ofEpochMilli(endTime)
                    )
                )
            )
            response.records.map { record ->
                val duration = Duration.between(record.startTime, record.endTime)
                SleepSession(
                    startTime = record.startTime.toString(),
                    endTime = record.endTime.toString(),
                    durationMinutes = duration.toMinutes().toInt()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading sleep sessions", e)
            emptyList()
        }
    }

    suspend fun readHeartRateSummary(startTime: Long, endTime: Long): HeartRateSummary? {
        return try {
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        HeartRateRecord.BPM_AVG,
                        HeartRateRecord.BPM_MIN,
                        HeartRateRecord.BPM_MAX
                    ),
                    timeRangeFilter = TimeRangeFilter.between(
                        Instant.ofEpochMilli(startTime),
                        Instant.ofEpochMilli(endTime)
                    )
                )
            )
            
            val avg = response[HeartRateRecord.BPM_AVG]
            val min = response[HeartRateRecord.BPM_MIN]
            val max = response[HeartRateRecord.BPM_MAX]
            
            // Try to get Resting Heart Rate specifically
            var resting: Long? = null
            try {
                val restingResponse = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        recordType = RestingHeartRateRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(
                            Instant.ofEpochMilli(startTime),
                            Instant.ofEpochMilli(endTime)
                        ),
                        pageSize = 1
                    )
                )
                if (restingResponse.records.isNotEmpty()) {
                    resting = restingResponse.records.last().beatsPerMinute
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not read RestingHeartRateRecord", e)
            }

            if (avg != null) {
                val avgInt = avg.toInt()
                val minInt = min?.toInt() ?: 0
                val maxInt = max?.toInt() ?: 0
                val finalResting = resting?.toInt() ?: avgInt

                // Validation
                if (avgInt < 30 || avgInt > 250) {
                    Log.w(TAG, "Invalid heart rate (avg): $avgInt bpm, rejecting summary")
                    return null
                }
                if (minInt != 0 && (minInt < 30 || minInt > 250)) {
                    Log.w(TAG, "Invalid heart rate (min): $minInt bpm, rejecting summary")
                    return null
                }
                if (maxInt != 0 && (maxInt < 30 || maxInt > 250)) {
                    Log.w(TAG, "Invalid heart rate (max): $maxInt bpm, rejecting summary")
                    return null
                }
                if (resting != null && (finalResting < 30 || finalResting > 250)) {
                    Log.w(TAG, "Invalid heart rate (resting): $finalResting bpm, rejecting summary")
                    return null
                }

                HeartRateSummary(
                    avgHr = avgInt,
                    minHr = minInt,
                    maxHr = maxInt,
                    restingHr = finalResting
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading heart rate", e)
            null
        }
    }

    suspend fun readExerciseSessions(startTime: Long, endTime: Long): List<ExerciseSession> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        Instant.ofEpochMilli(startTime),
                        Instant.ofEpochMilli(endTime)
                    )
                )
            )
            response.records.map { record ->
                val duration = Duration.between(record.startTime, record.endTime)
                ExerciseSession(
                    startTime = record.startTime.toString(),
                    endTime = record.endTime.toString(),
                    durationMinutes = duration.toMinutes().toInt(),
                    title = record.title,
                    notes = record.notes
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading exercise sessions", e)
            emptyList()
        }
    }

    suspend fun readBodyMetrics(startTime: Long, endTime: Long): BodyMetrics? {
        var weight: Double? = null
        var bodyFat: Double? = null
        
        try {
            // Fetch Weight (Latest record in the timeframe)
            val weightResponse = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        Instant.ofEpochMilli(startTime),
                        Instant.ofEpochMilli(endTime)
                    ),
                    ascendingOrder = false, // Get latest first
                    pageSize = 1
                )
            )
            if (weightResponse.records.isNotEmpty()) {
                weight = weightResponse.records.first().weight.inKilograms
            }

            // Fetch Body Fat (Latest record)
            val bodyFatResponse = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = BodyFatRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        Instant.ofEpochMilli(startTime),
                        Instant.ofEpochMilli(endTime)
                    ),
                    ascendingOrder = false,
                    pageSize = 1
                )
            )
            if (bodyFatResponse.records.isNotEmpty()) {
                bodyFat = bodyFatResponse.records.first().percentage.value
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading body metrics", e)
        }
        
        // Validation
        if (weight != null) {
            if (weight < 30 || weight > 300) {
                Log.w(TAG, "Invalid weight: $weight kg, rejecting body_metrics")
                return null
            }
        }
        if (bodyFat != null) {
            if (bodyFat < 3 || bodyFat > 70) {
                Log.w(TAG, "Invalid body fat: $bodyFat %, rejecting body_metrics")
                return null
            }
        }

        if (weight == null && bodyFat == null) {
            return null
        }
        return BodyMetrics(weightKg = weight, bodyFatPercentage = bodyFat)
    }

    suspend fun readNutritionSummary(startTime: Long, endTime: Long): NutritionSummary? {
        return try {
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(NutritionRecord.ENERGY_TOTAL, NutritionRecord.PROTEIN_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(
                        Instant.ofEpochMilli(startTime),
                        Instant.ofEpochMilli(endTime)
                    )
                )
            )
            val calories = response[NutritionRecord.ENERGY_TOTAL]?.inKilocalories?.toInt()
            val protein = response[NutritionRecord.PROTEIN_TOTAL]?.inGrams
            
            // Validation
            if (calories != null) {
                if (calories < 0 || calories > 10000) {
                    Log.w(TAG, "Invalid total calories: $calories, rejecting nutrition")
                    return null
                }
            }
            if (protein != null) {
                if (protein < 0) {
                    Log.w(TAG, "Invalid protein: $protein g, rejecting nutrition")
                    return null
                }
            }

            if (calories == null && protein == null) {
                null
            } else {
                NutritionSummary(caloriesTotal = calories, proteinGrams = protein)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading nutrition", e)
            null
        }
    }
}