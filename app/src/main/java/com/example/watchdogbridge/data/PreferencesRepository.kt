package com.example.watchdogbridge.data

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

class PreferencesRepository(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "watchdog_bridge_prefs"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_SETUP_COMPLETED = "setup_completed"
    }

    fun getDeviceId(): String {
        val existingId = sharedPreferences.getString(KEY_DEVICE_ID, null)
        if (existingId != null) {
            return existingId
        }
        val newId = UUID.randomUUID().toString()
        sharedPreferences.edit().putString(KEY_DEVICE_ID, newId).apply()
        return newId
    }

    fun isSetupCompleted(): Boolean {
        return sharedPreferences.getBoolean(KEY_SETUP_COMPLETED, false)
    }

    fun setSetupCompleted(completed: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SETUP_COMPLETED, completed).apply()
    }
}
