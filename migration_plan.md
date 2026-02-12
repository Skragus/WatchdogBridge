# Migration Plan: Samsung Health SDK to Health Connect

This document outlines the steps to migrate the `WatchdogBridge` application from Samsung Health SDK to Android Health Connect.

## 1. Dependency Management

### Remove Samsung Health SDK
- **File:** `app/build.gradle.kts`
- **Action:** Remove the line `implementation(files("libs/samsung-health-data-api-1.0.0.aar"))`.
- **Action:** Delete the file `app/libs/samsung-health-data-api-1.0.0.aar` if it is no longer needed.

### Add Health Connect Dependency
- **File:** `app/build.gradle.kts`
- **Action:** Add the Health Connect dependency:
  ```kotlin
  implementation("androidx.health.connect:connect-client:1.1.0-alpha07") // Check for latest version
  ```

## 2. Android Manifest Updates

### Remove Samsung Health Configuration
- **File:** `app/src/main/AndroidManifest.xml`
- **Action:** Remove the `<queries>` block for `com.sec.android.app.shealth`.
- **Action:** Remove the `<meta-data>` tag for `com.samsung.android.health.permission.read`.

### Add Health Connect Permissions
- **File:** `app/src/main/AndroidManifest.xml`
- **Action:** Add the following permissions:
  ```xml
  <uses-permission android:name="android.permission.health.READ_STEPS"/>
  <uses-permission android:name="android.permission.health.READ_SLEEP"/>
  <uses-permission android:name="android.permission.health.READ_HEART_RATE"/>
  ```
- **Action:** Add an activity alias or intent filter to handle Health Connect permission rationale (optional but recommended for production apps to explain usage).

## 3. Code Migration Strategy

### Create HealthConnectRepository
- **File:** Create `app/src/main/java/com/example/watchdogbridge/data/HealthConnectRepository.kt`
- **Responsibility:** This class will replace `SamsungHealthRepository`.
- **Key Components:**
  - **Initialization:**
    ```kotlin
    val healthConnectClient = HealthConnectClient.getOrCreate(context)
    ```
  - **Availability Check:** Check if Health Connect is available on the device.
  - **Permissions:**
    - Use `HealthPermissionTool` or directly construct a set of permissions.
    - Use `PermissionController.createRequestPermissionResultContract()` in the Activity/Fragment.
  - **Data Access Methods:**
    - `readDailySteps(startTime: Instant, endTime: Instant): Long`
      - Use `healthConnectClient.readRecords` or `aggregate` with `StepsRecord`.
    - `readSleepSessions(startTime: Instant, endTime: Instant): List<SleepSession>`
      - Use `healthConnectClient.readRecords` with `SleepSessionRecord`.
    - `readHeartRateSummary(startTime: Instant, endTime: Instant): HeartRateSummary?`
      - Use `healthConnectClient.readRecords` with `HeartRateRecord` and calculate average/resting manually or use aggregation if available.

### Refactor Existing Logic
- **File:** `app/src/main/java/com/example/watchdogbridge/data/SamsungHealthRepository.kt`
- **Action:** Can be deleted or kept as a reference until migration is complete.
- **File:** `app/src/main/java/com/example/watchdogbridge/MainActivity.kt` (and any ViewModels)
- **Action:** Replace usage of `SamsungHealthRepository` with `HealthConnectRepository`.
- **Action:** Update the permission request flow to use the AndroidX Activity Result API for Health Connect permissions.

## 4. Detailed Implementation Steps

### Step 1: Dependencies & Manifest
1. Update `build.gradle.kts`.
2. Update `AndroidManifest.xml`.
3. Sync project.

### Step 2: Implement HealthConnectRepository
1. Create the class.
2. Implement `getPermissions()` returning the set of required permissions.
3. Implement `readSteps`.
4. Implement `readSleep`.
5. Implement `readHeartRate`.

### Step 3: Update UI & Permission Flow
1. In `MainActivity`, check for Health Connect availability.
2. If not installed, prompt user to install (via intent to Play Store).
3. If installed, check permissions.
4. If not granted, launch the permission request contract.
5. Once granted, proceed to fetch data.

## 5. Verification
- Test on a device with Health Connect installed (Android 14+ or Android 9+ with the app).
- Verify that steps, sleep, and heart rate data are retrieved correctly.
- Verify that the app handles cases where permissions are denied.
