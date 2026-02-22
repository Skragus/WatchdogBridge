# WatchdogBridge

**Android Health Connect Sync Bridge**

A native Android app that reads health metrics from Health Connect (Google/Samsung) and syncs them to a custom API backend. Built with modern Android architecture and resilient background sync.

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| **Language** | Kotlin |
| **UI** | Jetpack Compose |
| **Architecture** | MVVM + Repository Pattern |
| **Health Data** | Health Connect API |
| **Networking** | Retrofit + OkHttp + Kotlinx Serialization |
| **Local Storage** | Room Database |
| **Background Work** | WorkManager (periodic + expedited) |
| **Build** | Gradle with product flavors |

---

## Features

- **Health Connect Integration**: Reads steps, workouts, sleep, nutrition, body metrics, heart rate, and more
- **Dual Sync Strategy**: 
  - Daily sync (60min interval) for aggregated summaries
  - Intraday sync (15min interval) for real-time updates
- **Resilient Background Sync**: Workers restart automatically if stalled
- **Offline-First**: Sync state tracked locally, retries on failure
- **MacroDroid Watchdog**: External automation can trigger worker restarts via broadcast intents

---

## Architecture

```
Health Connect (Android)
    ↓
HealthConnectRepository
    ↓
Room Database (sync state)
    ↓
WorkManager (PeriodicWorkRequest)
    ↓
IngestApi (Retrofit)
    ↓
SH-APK-API Backend
```

### Key Components

| Component | Responsibility |
|-----------|---------------|
| `HealthConnectRepository` | Reads from Health Connect SDK |
| `SamsungHealthRepository` | Legacy Samsung Health SDK support |
| `PreferencesRepository` | User settings and sync preferences |
| `DailySyncState` / `IntradaySyncState` | Tracks last sync timestamps |
| `WorkerRestartReceiver` | Broadcast receiver for external watchdog triggers |

---

## Build Setup

### Prerequisites

- Android Studio Hedgehog or newer
- Android SDK 34
- Health Connect app installed on device

### Configuration

Create `local.properties` in project root:

```properties
apiKey=your-backend-api-key
```

The build system injects this into `BuildConfig.API_KEY`.

### Product Flavors

| Flavor | Purpose |
|--------|---------|
| `staging` | Debug builds with verbose logging |
| `prod` | Release builds, minimal logging |

---

## Permissions

The app requests extensive Health Connect permissions:
- Activity: steps, distance, floors, elevation
- Body: weight, body fat, height, BMI
- Vitals: heart rate, sleep, blood pressure, SpO2
- Exercise: workout sessions, calories burned
- Nutrition: food intake, hydration

See `AndroidManifest.xml` for full list.

---

## API Integration

Syncs to a FastAPI backend ([SH-APK-API](https://github.com/Skragus/SH-APK-API)):

```kotlin
interface IngestApi {
    @POST("/v1/ingest/shealth/daily")
    suspend fun postDaily(@Body body: DailyIngestRequest): Response<IngestResponse>

    @POST("/v1/ingest/shealth/intraday")
    suspend fun postIntraday(@Body body: DailyIngestRequest): Response<IngestResponse>
}
```

---

## Watchdog / Automation

External automation (MacroDroid, Tasker) can restart workers via broadcast:

```kotlin
// Restart daily worker
Intent("com.example.watchdogbridge.RESTART_DAILY_WORKER")

// Restart intraday worker  
Intent("com.example.watchdogbridge.RESTART_INTRADAY_WORKER")

// Restart all workers
Intent("com.example.watchdogbridge.RESTART_ALL_WORKERS")
```

See `MACRODROID_WATCHDOG_SETUP.md` for detailed automation setup.

---

## Development

```bash
git clone https://github.com/Skragus/WatchdogBridge.git
cd WatchdogBridge

# Open in Android Studio
# Create local.properties with your API key
# Build and run
```

---

## Related Projects

- [SH-APK-API](https://github.com/Skragus/SH-APK-API) — Backend API that receives the health data

---

## License

MIT
