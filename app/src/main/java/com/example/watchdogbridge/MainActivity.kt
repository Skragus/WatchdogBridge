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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.work.Data
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
import java.time.Instant
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
    
    // Backfill stubs
    private var backfillStartDate by mutableStateOf("2025-10-01")
    private var backfillEndDate by mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))

    private var onPermissionsResult: ((Boolean) -> Unit)? = null
    
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

        WorkerUtil.scheduleWorkers(applicationContext)

        setContent {
            WatchdogBridgeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        statusText = statusText,
                        startDate = backfillStartDate,
                        endDate = backfillEndDate,
                        onStartDateChange = { backfillStartDate = it },
                        onEndDateChange = { backfillEndDate = it },
                        onConnectClick = {
                            statusText = "Checking permissions..."
                            connectToHealth { success ->
                                statusText = if (success) "Permissions Granted" else "Permissions Denied"
                            }
                        },
                        onTestSyncClick = {
                            statusText = "Daily Sync (7 days) queued..."
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
                        },
                        onTriggerBackfillClick = {
                            triggerCustomBackfill()
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

    private fun triggerCustomBackfill() {
        statusText = "Custom Backfill queued: $backfillStartDate to $backfillEndDate"
        val data = Data.Builder()
            .putString("start_date", backfillStartDate)
            .putString("end_date", backfillEndDate)
            .build()

        val request = OneTimeWorkRequestBuilder<HealthConnectDailyWorker>()
            .setInputData(data)
            .addTag("custom_backfill")
            .build()
        WorkManager.getInstance(applicationContext).enqueue(request)
        observeWork(request.id, "Backfill")
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
                        collectedAt = Instant.now().toString()
                    )
                )

                statusText = "Sending to /v1/ingest/debug..."
                val response = withContext(Dispatchers.IO) {
                    NetworkClient.api.postDebug(request)
                }

                if (response.isSuccessful) {
                    statusText = "Debug Sync Success: ${response.code()}"
                } else {
                    statusText = "Debug Sync Failed: ${response.code()}"
                }
            } catch (e: Exception) {
                statusText = "Debug Sync Error: ${e.message}"
            }
        }
    }
    
    private fun wipeDailyHashes() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                AppDatabase.getDatabase(applicationContext).dailySyncStateDao().clearAll()
                withContext(Dispatchers.Main) {
                    statusText = "Daily Hashes Wiped!"
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
                    statusText = "$taskName: ${workInfo.state}"
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
                        statusText = "Intraday Worker: ${workInfos[0].state}\nLast Run: $lastRunText"
                    } else {
                        statusText = "Intraday Worker not scheduled\nLast Run: $lastRunText"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText = "Error: ${e.message}"
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    statusText: String,
    startDate: String,
    endDate: String,
    onStartDateChange: (String) -> Unit,
    onEndDateChange: (String) -> Unit,
    onConnectClick: () -> Unit,
    onTestSyncClick: () -> Unit,
    onCheckStatusClick: () -> Unit,
    onTriggerIntradayClick: () -> Unit,
    onWipeHashesClick: () -> Unit,
    onDebugSyncClick: () -> Unit,
    onTriggerBackfillClick: () -> Unit
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
        
        Spacer(Modifier.height(24.dp))
        
        Button(onClick = onConnectClick, modifier = Modifier.fillMaxWidth()) {
            Text("Connect Health Connect")
        }
        
        Spacer(Modifier.height(16.dp))
        
        Button(onClick = onCheckStatusClick, modifier = Modifier.fillMaxWidth()) {
            Text("Check Worker Status")
        }

        Spacer(Modifier.height(16.dp))

        Button(onClick = onTriggerIntradayClick, modifier = Modifier.fillMaxWidth()) {
            Text("Sync Today Now")
        }
        
        Spacer(Modifier.height(16.dp))
        
        Button(onClick = onTestSyncClick, modifier = Modifier.fillMaxWidth()) {
            Text("Sync Last 7 Days")
        }

        Spacer(Modifier.height(32.dp))

        // Backfill Section
        Text("Custom Backfill", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = startDate,
                onValueChange = onStartDateChange,
                label = { Text("Start (YYYY-MM-DD)") },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = endDate,
                onValueChange = onEndDateChange,
                label = { Text("End (YYYY-MM-DD)") },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onTriggerBackfillClick, modifier = Modifier.fillMaxWidth()) {
            Text("Trigger Range Sync")
        }

        Spacer(Modifier.height(32.dp))

        // Danger Zone
        Text("Danger Zone", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onWipeHashesClick, modifier = Modifier.fillMaxWidth()) {
            Text("Wipe Local Hashes (Force Re-sync)")
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onDebugSyncClick, modifier = Modifier.fillMaxWidth()) {
            Text("Send Yesterday to /debug")
        }
        
        Spacer(Modifier.height(32.dp))
        
        Text(text = statusText, style = MaterialTheme.typography.bodyMedium)
    }
}
