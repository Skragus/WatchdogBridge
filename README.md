# Watchdog Bridge

Watchdog Bridge is an Android application that acts as a bridge between Samsung Health and the Watchdog API. It reads daily health data—steps, sleep, and heart rate—and securely sends it to a remote server for analysis and storage.

## Features

- **Samsung Health Integration:** Connects to the Samsung Health SDK to read daily steps, sleep sessions, and heart rate data.
- **Background Sync:** Uses Android's WorkManager to schedule a daily background job that automatically syncs the latest health data.
- **Manual Sync:** A "Test Sync Now" option in the UI allows for immediate, on-demand data synchronization.
- **Secure Configuration:** API keys are stored securely in the `local.properties` file and are not checked into version control.
- **UI Feedback:** The app provides clear status updates on the connection to Samsung Health and the state of the data sync.

## Setup

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/Skragus/WatchdogBridge.git
    ```

2.  **Open in Android Studio:**
    Open the cloned project in Android Studio.

3.  **Configure API Key:**
    Create a `local.properties` file in the root directory of the project and add your API key:
    ```properties
    apiKey="YOUR_API_KEY"
    ```

4.  **Build the project:**
    Sync the project with Gradle files and build the app.

## Usage

1.  **Connect to Samsung Health:**
    Launch the app and tap the "Connect Samsung Health" button. This will prompt you to grant the necessary permissions.

2.  **Sync Data:**
    - The app will automatically sync data once every 24 hours.
    - To trigger a sync manually, tap the "Test Sync Now" button. The on-screen status will update to show the progress and result of the sync.
