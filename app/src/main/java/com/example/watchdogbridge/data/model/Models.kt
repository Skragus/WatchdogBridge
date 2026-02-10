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
    val source: Source
)

@Serializable
data class SleepSession(
    @SerialName("start_time") val startTime: String, // ISO-8601 with offset
    @SerialName("end_time") val endTime: String,
    @SerialName("duration_minutes") val durationMinutes: Int
)

@Serializable
data class HeartRateSummary(
    @SerialName("resting_hr") val restingHr: Int
)

@Serializable
data class Source(
    @SerialName("source_app") val sourceApp: String = "samsung_health",
    @SerialName("device_id") val deviceId: String,
    @SerialName("collected_at") val collectedAt: String // ISO-8601 with offset
)
