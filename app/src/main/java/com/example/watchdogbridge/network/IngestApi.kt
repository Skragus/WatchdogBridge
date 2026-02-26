package com.example.watchdogbridge.network

import com.example.watchdogbridge.data.model.DailyIngestRequest
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface IngestApi {
    @POST("/v1/ingest/daily")
    suspend fun postDaily(@Body body: DailyIngestRequest): Response<IngestResponse>

    @POST("/v1/ingest/intraday")
    suspend fun postIntraday(@Body body: DailyIngestRequest): Response<IngestResponse>

    @POST("/v1/ingest/debug")
    suspend fun postDebug(@Body body: DailyIngestRequest): Response<IngestResponse>
}

@Serializable
data class IngestResponse(
    val status: String,
    val inserted: Boolean? = null,
    val id: String? = null
)
