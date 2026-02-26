package com.example.watchdogbridge

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.watchdogbridge.data.HealthConnectRepository
import com.example.watchdogbridge.data.PreferencesRepository
import com.example.watchdogbridge.data.local.AppDatabase
import com.example.watchdogbridge.data.model.DailyIngestRequest
import com.example.watchdogbridge.data.model.Source
import com.example.watchdogbridge.network.NetworkClient
import com.example.watchdogbridge.ui.theme.WatchdogBridgeTheme
import com.example.watchdogbridge.worker.HealthConnectDailyWorker
import com.example.watchdogbridge.worker.HealthConnectIntradayWorker
import com.example.watchdogbridge.worker.WorkerUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val healthRepo by lazy { HealthConnectRepository(applicationContext) }
    private val prefsRepo by lazy { PreferencesRepository(applicationContext) }
    private var statusText by mutableStateOf("Ready")
    
    private var onPermissionsResult: ((Boolean) -> Unit)? = null
    
    // Health Connect Permission Launcher
    private val requestHealthPermissions = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        lifecycleScope.launch {
            if (granted.containsAll(healthRepo.getPermissions())) {
                onPermissionsResult?.invoke(true)
            } else {
                onPermissionsResult?.invoke(false)
            }
            onPermissionsResult = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Schedule periodic workers
        WorkerUtil.scheduleWorkers(applicationContext)

        setContent {
            WatchdogBridgeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        statusText = statusText,
                        onConnectClick = {
                            statusText = "Checking permissions..."
                            connectToHealth { success ->
                                statusText = if (success) "Permissions Granted" else "Permissions Denied"
                            }
                        },
                        onTestSyncClick = {
                            statusText = "Daily Sync queued..."
                            testDailySync()
                        },
                        onCheckStatusClick = {
                            statusText = "Checking status..."
                            checkWorkerStatus()
                        },
                        onTriggerIntradayClick = {
                            statusText = "Intraday Sync queued..."
                            triggerIntradaySync()
                        },
                        onWipeHashesClick = {
                            wipeDailyHashes()
                        },
                        onDebugSyncClick = {
                            sendDebugSync()
                        }
                    )
                }
            }
        }
    }

    private fun connectToHealth(onResult: (Boolean) -> Unit) {
        lifecycleScope.launch {
            if (healthRepo.hasPermissions()) {
                onResult(true)
            } else {
                onPermissionsResult = onResult
                requestHealthPermissions.launch(healthRepo.getPermissions())
            }
        }
    }

    private fun testDailySync() {
        val request = OneTimeWorkRequestBuilder<HealthConnectDailyWorker>()
            .addTag("test_daily_sync")
            .build()
        WorkManager.getInstance(applicationContext).enqueue(request)

        observeWork(request.id, "Daily Sync")
    }

    private fun triggerIntradaySync() {
        val request = OneTimeWorkRequestBuilder<HealthConnectIntradayWorker>()
            .addTag("manual_intraday_sync")
            .build()
        WorkManager.getInstance(applicationContext).enqueue(request)

        observeWork(request.id, "Intraday Sync")
    }

    private fun sendDebugSync() {
        lifecycleScope.launch {
            statusText = "Fetching yesterday's data for debug..."
            try {
                val yesterday = LocalDate.now().minusDays(1)
                val dateStr = yesterday.format(DateTimeFormatter.ISO_LOCAL_DATE)
                val zoneId = ZoneId.systemDefault()
                val startOfDay = yesterday.atStartOfDay(zoneId).toInstant().toEpochMilli()
                val endOfDay = yesterday.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1

                val rawJson = healthRepo.readRawData(startOfDay, endOfDay)
                val deviceId = prefsRepo.getDeviceId()

                val request = DailyIngestRequest(
                    date = dateStr,
                    rawJson = rawJson,
                    source = Source(
                        deviceId = deviceId,
                        collectedAt = LocalDateTime.now().atZone(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    )
                )

                statusText = "Sending to /v1/ingest/debug..."
                val response = withContext(Dispatchers.IO) {
                    NetworkClient.api.postDebug(request)
                }

                if (response.isSuccessful) {
                    statusText = "Debug Sync Success: ${response.code()}\nCheck API Console!"
                } else {
                    statusText = "Debug Sync Failed: ${response.code()}\n${response.errorBody()?.string()}"
                }
            } catch (e: Exception) {
                statusText = "Debug Sync Error: ${e.message}"
                Log.e("MainActivity", "Debug sync error", e)
            }
        }
    }
    
    private fun wipeDailyHashes() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                AppDatabase.getDatabase(applicationContext).dailySyncStateDao().clearAll()
                withContext(Dispatchers.Main) {
                    statusText = "Daily Hashes Wiped! Run Sync Now."
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText = "Failed to wipe hashes: ${e.message}"
                }
            }
        }
    }

    private fun observeWork(id: UUID, taskName: String) {
        WorkManager.getInstance(applicationContext).getWorkInfoByIdLiveData(id)
            .observe(this) { workInfo ->
                if (workInfo != null) {
                    val state = workInfo.state
                    statusText = "$taskName: $state"
                    if (state == WorkInfo.State.FAILED) {
                        val error = workInfo.outputData.getString("error")
                        if (error != null) {
                            statusText += "\nError: $error"
                        }
                    }
                }
            }
    }

    private fun checkWorkerStatus() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val workManager = WorkManager.getInstance(applicationContext)
                val workInfos = workManager.getWorkInfosForUniqueWork("HealthConnectIntradaySync").get()
                val lastRun = prefsRepo.getLastIntradayRun()
                val lastRunText = if (lastRun > 0) {
                     SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(lastRun))
                } else {
                    "Never"
                }

                withContext(Dispatchers.Main) {
                    if (!workInfos.isNullOrEmpty()) {
                        val workInfo = workInfos[0]
                        val nextRun = if (workInfo.state == WorkInfo.State.ENQUEUED) {
                            " (Scheduled)"
                        } else {
                            ""
                        }
                        statusText = "Intraday Worker: ${workInfo.state}$nextRun\nID: ${workInfo.id}\nLast Run: $lastRunText"
                    } else {
                        statusText = "Intraday Worker not found (Not Scheduled)\nLast Run: $lastRunText"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText = "Error checking status: ${e.message}"
                }
                Log.e("MainActivity", "Error checking worker status", e)
            }
        }
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    statusText: String,
    onConnectClick: () -> Unit,
    onTestSyncClick: () -> Unit,
    onCheckStatusClick: () -> Unit,
    onTriggerIntradayClick: () -> Unit,
    onWipeHashesClick: () -> Unit,
    onDebugSyncClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Health Connect Bridge", style = MaterialTheme.typography.headlineMedium)
        
        Spacer(Modifier.height(32.dp))
        
        Button(onClick = onConnectClick) {
            Text("Connect Health Connect")
        }
        
        Spacer(Modifier.height(16.dp))
        
        Button(onClick = onCheckStatusClick) {
            Text("Check Periodic Status")
        }

        Spacer(Modifier.height(16.dp))

        Button(onClick = onTriggerIntradayClick) {
            Text("Run Intraday Now")
        }
        
        Spacer(Modifier.height(16.dp))
        
        Button(onClick = onTestSyncClick) {
            Text("Run Daily Sync Now")
        }
        
        Spacer(Modifier.height(16.dp))
        
        Button(onClick = onWipeHashesClick) {
            Text("Wipe Daily Hashes")
        }

        Spacer(Modifier.height(32.dp))

        // Debug Section
        Text("Debug Tools", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onDebugSyncClick) {
            Text("Send Yesterday to /debug")
        }
        
        Spacer(Modifier.height(32.dp))
        
        Text(text = statusText)
    }
}
