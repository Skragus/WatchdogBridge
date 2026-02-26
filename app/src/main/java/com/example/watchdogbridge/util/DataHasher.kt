package com.example.watchdogbridge.util

import com.example.watchdogbridge.data.model.DailyIngestRequest
import java.security.MessageDigest

object DataHasher {

    /**
     * Computes a SHA-256 hash of the agnostic payload.
     * We hash the date, the source app/device, and the entire raw JSON blob.
     */
    fun computeHash(data: DailyIngestRequest): String {
        val sb = StringBuilder()
        
        // Version prefix to force re-sync if the hashing logic ever changes
        sb.append("v3|") 

        sb.append("date=${data.date}|")
        sb.append("app=${data.source.sourceApp}|")
        sb.append("device=${data.source.deviceId}|")
        
        // We hash the entire raw blob to detect ANY change in underlying Health Connect records
        sb.append("raw=${data.rawJson}")

        return sha256(sb.toString())
    }

    private fun sha256(input: String): String {
        val bytes = input.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}
