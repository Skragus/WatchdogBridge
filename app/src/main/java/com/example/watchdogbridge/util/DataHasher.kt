package com.example.watchdogbridge.util

import com.example.watchdogbridge.data.model.DailyIngestRequest
import java.security.MessageDigest
import java.util.Locale

object DataHasher {

    fun computeHash(data: DailyIngestRequest): String {
        // Build a canonical string representation
        val sb = StringBuilder()
        
        // Version prefix to handle future schema changes
        sb.append("v1|")

        // 1. Core Data
        sb.append("date=${data.date}|")
        sb.append("steps=${data.stepsTotal}|")

        // 2. Sleep Sessions (Sorted)
        val sortedSleep = data.sleepSessions.sortedBy { it.startTime }
        sb.append("sleep=[")
        sortedSleep.forEach { 
            sb.append("{s=${it.startTime},e=${it.endTime},d=${it.durationMinutes}},")
        }
        sb.append("]|")

        // 3. Heart Rate Summary
        val hr = data.heartRateSummary
        if (hr != null) {
            sb.append("hr={avg=${hr.avgHr},min=${hr.minHr},max=${hr.maxHr},rest=${hr.restingHr}}|")
        } else {
            sb.append("hr=null|")
        }

        // 4. Body Metrics
        val body = data.bodyMetrics
        if (body != null) {
            // Round to 2 decimal places
            val w = body.weightKg?.let { "%.2f".format(Locale.US, it) } ?: "null"
            val f = body.bodyFatPercentage?.let { "%.2f".format(Locale.US, it) } ?: "null"
            sb.append("body={w=$w,f=$f}|")
        } else {
            sb.append("body=null|")
        }

        // 5. Nutrition
        val nut = data.nutritionSummary
        if (nut != null) {
            val p = nut.proteinGrams?.let { "%.2f".format(Locale.US, it) } ?: "null"
            sb.append("nut={cal=${nut.caloriesTotal},prot=$p}|")
        } else {
            sb.append("nut=null|")
        }

        // 6. Exercise Sessions (Sorted)
        val sortedExercise = data.exerciseSessions.sortedBy { it.startTime }
        sb.append("ex=[")
        sortedExercise.forEach {
            sb.append("{s=${it.startTime},e=${it.endTime},d=${it.durationMinutes},t=${it.title}},")
        }
        sb.append("]")

        return sha256(sb.toString())
    }

    private fun sha256(input: String): String {
        val bytes = input.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}