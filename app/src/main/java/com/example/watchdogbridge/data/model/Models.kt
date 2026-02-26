package com.example.watchdogbridge.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DailyIngestRequest(
    @SerialName("schema_version") val schemaVersion: Int = 3, // Version 3 is the "Agnostic" version
    val date: String, // YYYY-MM-DD
    
    // The "Lazy" Raw Blob that contains everything
    @SerialName("raw_json") val rawJson: String,

    val source: Source
)

@Serializable
data class Source(
    @SerialName("source_app") val sourceApp: String = "health_connect",
    @SerialName("device_id") val deviceId: String,
    @SerialName("collected_at") val collectedAt: String // ISO-8601
)
