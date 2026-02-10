package com.example.watchdogbridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.watchdogbridge.data.SamsungHealthRepository
import com.example.watchdogbridge.ui.theme.WatchdogBridgeTheme
import com.example.watchdogbridge.worker.SamsungHealthDailyWorker
import com.example.watchdogbridge.worker.WorkerUtil
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val healthRepo by lazy { SamsungHealthRepository(applicationContext) }
    private var statusText by mutableStateOf("Ready")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // WorkerUtil.scheduleDailySync(applicationContext)

        setContent {
            WatchdogBridgeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        statusText = statusText,
                        onConnectClick = {
                            statusText = "Connecting..."
                            connectToHealth { success ->
                                statusText = if (success) "Connection Established" else "Connection Failed"
                            }
                        },
                        onTestSyncClick = {
                            statusText = "Sync queued..."
                            testSync()
                        }
                    )
                }
            }
        }
    }

    private fun connectToHealth(onResult: (Boolean) -> Unit) {
        lifecycleScope.launch {
            val connected = healthRepo.connect()
            if (connected) {
                val permissionsGranted = healthRepo.requestPermissions(this@MainActivity)
                onResult(permissionsGranted)
            } else {
                onResult(false)
            }
        }
    }

    private fun testSync() {
        val request = OneTimeWorkRequestBuilder<SamsungHealthDailyWorker>()
            .addTag("test_sync")
            .build()
        WorkManager.getInstance(applicationContext).enqueue(request)

        WorkManager.getInstance(applicationContext).getWorkInfoByIdLiveData(request.id)
            .observe(this) { workInfo ->
                statusText = when (workInfo?.state) {
                    WorkInfo.State.SUCCEEDED -> "Sync Successful"
                    WorkInfo.State.FAILED -> {
                        val error = workInfo.outputData.getString("error")
                        if (error == "timeout") "Sync Timed Out" else "Sync Failed"
                    }
                    WorkInfo.State.RUNNING -> "Sync in progress..."
                    WorkInfo.State.ENQUEUED -> "Sync queued..."
                    else -> "Ready"
                }
            }
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    statusText: String,
    onConnectClick: () -> Unit,
    onTestSyncClick: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Samsung Health Bridge", style = MaterialTheme.typography.headlineMedium)
        
        Spacer(Modifier.height(32.dp))
        
        Button(onClick = onConnectClick) {
            Text("Connect Samsung Health")
        }
        
        Spacer(Modifier.height(16.dp))
        
        Button(onClick = onTestSyncClick) {
            Text("Test Sync Now")
        }
        
        Spacer(Modifier.height(32.dp))
        
        Text(text = statusText)
    }
}