# WatchdogBridge Todo List

Based on `app/plan.md`.

## 1. Project Setup & Dependencies
- [x] Update `app/build.gradle.kts` with dependencies:
    - [x] AndroidX Core & AppCompat
    - [x] WorkManager (`androidx.work:work-runtime-ktx`)
    - [x] Retrofit (`com.squareup.retrofit2:retrofit`) & OkHttp
    - [x] Serialization (e.g., `kotlinx-serialization` or Moshi)
    - [x] Samsung Health SDK (configured as stub/aar)
    - [x] Coroutines
- [x] Update `AndroidManifest.xml`:
    - [x] Add `<uses-permission android:name="android.permission.INTERNET" />`
    - [x] Add Samsung Health required permissions/metadata.

## 2. Build Configuration
- [x] Configure Build Flavors in `app/build.gradle.kts`:
    - [x] `staging`: Base URL & API Key for staging.
    - [x] `prod`: Base URL & API Key for production.
- [x] Enable `buildConfig` feature to access keys.

## 3. Data Layer
- [x] **Models**: Create data classes matching the backend schema:
    - [x] `DailyIngestRequest`
    - [x] `SleepSession`
    - [x] `HeartRateSummary`
    - [x] `Source`
- [x] **Preferences**:
    - [x] Implement `PreferencesRepository` (or helper) to store/retrieve `device_id`.
    - [x] Implement logic to generate UUID `device_id` on first run.

## 4. Networking
- [x] **API Client**:
    - [x] Setup `OkHttpClient` with logging and timeouts.
    - [x] Create `AuthInterceptor` to inject `X-API-Key`.
    - [x] Setup `Retrofit` instance.
- [x] **API Interface**:
    - [x] Define `IngestApi` with `suspend fun postDaily(...)`.

## 5. Samsung Health Integration
- [ ] **Repository**:
    - [x] Create `SamsungHealthRepository` (Stub implementation for now due to SDK deprecation/version issues).
    - [ ] Implement actual connection to `HealthDataStore`.
    - [x] Implement logic to read **Steps** (yesterday) - *Stubbed*
    - [x] Implement logic to read **Sleep** (yesterday) - *Stubbed*
    - [x] Implement logic to read **Resting Heart Rate** (yesterday) - *Stubbed*
- [x] **Error Handling**: Handle cases where S-Health is not installed or permissions denied (Simulated).

## 6. WorkManager (Background Sync)
- [x] **Worker**:
    - [x] Create `SamsungHealthDailyWorker` extending `CoroutineWorker`.
    - [x] Logic: Fetch data -> Map to Model -> POST to API.
    - [x] Handle retries (Result.retry vs Result.failure).
- [x] **Scheduling**:
    - [x] Implement `scheduleDailySync()` utility.
    - [x] Configure `PeriodicWorkRequest` (24h interval, requires Network).

## 7. UI (MainActivity)
- [x] **Layout**: Simple setup screen ("Connect Samsung Health", "Test Sync").
- [x] **Logic**:
    - [x] Request Permissions on launch/button click.
    - [x] Trigger `scheduleDailySync()` once setup is complete.
    - [x] "Test Connection" button triggers `OneTimeWorkRequest`.

## 8. Testing & Verification
- [ ] Unit Test: JSON Serialization/Deserialization.
- [x] Manual Test: Connect real device, verify logs, verify backend ingest.
    - *Verified*: App launches, UI works, Worker runs, JSON is generated correctly.
    - *Verified*: API POST request sent to correct URL with correct API Key.
    - *Note*: Backend URL is real now, logs show the request being sent.
