package com.example.watchdogbridge.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DailyIngestRequest(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    val date: String, // YYYY-MM-DD
    @SerialName("steps_total") val stepsTotal: Int,
    @SerialName("sleep_sessions") val sleepSessions: List<SleepSession> = emptyList(),
    @SerialName("heart_rate_summary") val heartRateSummary: HeartRateSummary? = null,
    @SerialName("body_metrics") val bodyMetrics: BodyMetrics? = null,
    @SerialName("nutrition_summary") val nutritionSummary: NutritionSummary? = null,
    @SerialName("exercise_sessions") val exerciseSessions: List<ExerciseSession> = emptyList(),
    val source: Source
)

@Serializable
data class SleepSession(
    @SerialName("start_time") val startTime: String, // ISO-8601 with offset
    @SerialName("end_time") val endTime: String,
    @SerialName("duration_minutes") val durationMinutes: Int
)

@Serializable
data class ExerciseSession(
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    @SerialName("duration_minutes") val durationMinutes: Int,
    @SerialName("title") val title: String? = null,
    @SerialName("notes") val notes: String? = null
)

@Serializable
data class HeartRateSummary(
    @SerialName("avg_hr") val avgHr: Int,
    @SerialName("min_hr") val minHr: Int,
    @SerialName("max_hr") val maxHr: Int,
    // Maintaining resting_hr for backward compatibility, mapped to avg if real resting not available
    @SerialName("resting_hr") val restingHr: Int
)

@Serializable
data class BodyMetrics(
    @SerialName("weight_kg") val weightKg: Double? = null,
    @SerialName("body_fat_percentage") val bodyFatPercentage: Double? = null
)

@Serializable
data class NutritionSummary(
    @SerialName("calories_total") val caloriesTotal: Int? = null,
    @SerialName("protein_grams") val proteinGrams: Double? = null
)

@Serializable
data class Source(
    @SerialName("source_app") val sourceApp: String = "health_connect",
    @SerialName("device_id") val deviceId: String,
    @SerialName("collected_at") val collectedAt: String // ISO-8601 with offset
)
