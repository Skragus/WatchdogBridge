# HealthConnectBridge

**Android Health Connect Sync Bridge (Field Agnostic)**

A native Android app that acts as a transparent bridge between the Android Health Connect API and your custom backend. Unlike traditional sync apps, HealthConnectBridge is **field-agnostic**: it captures the raw Health Connect record data, serializes it into a JSON blob, and sends it to your API, allowing your backend to handle schema parsing and data extraction.

---

## Key Features

- **Field-Agnostic Engine (V3)**: No manual mapping of health fields. Automatically captures and sends full raw Health Connect records.
- **Deep Backfill Support**: Trigger custom range syncs (e.g., from Q4 2025 to now) directly from the UI.
- **Intelligent Throttling**: Built-in rate-limiting logic to navigate Health Connect API quotas during bulk backfills.
- **Dual Sync Strategy**: 
  - Daily maintenance sync for periodic updates.
  - Intraday sync (60min interval) for fresh data throughout the day.
- **Resilient Background Sync**: Built with WorkManager; state is tracked locally using Room to prevent duplicate uploads.
- **Debug Tools**: Built-in button to send a full day of raw data to a `/debug` endpoint for schema inspection.

---

## Architecture

```
Health Connect (Android)
    ↓
HealthConnectRepository (Raw Record Fetcher)
    ↓
DataHasher (SHA-256 Change Detection)
    ↓
Room Database (Sync State / Hashes)
    ↓
WorkManager (Periodic + Range Tasks)
    ↓
IngestApi (V3 Agnostic Payload)
    ↓
Custom API Backend
```

### Components

| Component | Responsibility |
|-----------|---------------|
| `HealthConnectRepository` | Iterates through 26+ record types and captures raw data. |
| `DailyIngestRequest` | Agnostic V3 wrapper containing metadata and the raw JSON blob. |
| `PreferencesRepository` | Stores Device ID and sync timestamps. |
| `DailySyncState` | Tracks day-by-day hashes to avoid redundant network calls. |

---

## API Integration (V3 Schema)

The app POSTs to `/v1/ingest/daily` and `/v1/ingest/intraday`.

**Payload Structure:**
```json
{
  "schema_version": 3,
  "date": "2026-02-26",
  "raw_json": "{\"StepsRecord\":[...], \"HeartRateRecord\":[...] }",
  "source": {
    "source_app": "health_connect",
    "device_id": "unique-uuid-here",
    "collected_at": "2026-02-26T21:00:00Z"
  }
}
```

---

## Build Setup

### Configuration

Create `local.properties` in the project root:

```properties
apiKey="your-secret-key"
baseUrl="https://your-api-endpoint.com/"
```

- `apiKey` is injected into the `X-API-Key` header.
- `baseUrl` defines your backend destination.

### Build & Run

1. Clone the repo.
2. Setup `local.properties` as shown above.
3. Open in Android Studio.
4. Build the `prod` or `staging` flavor.

---

## Development & Debugging

Use the **"Send Yesterday to /debug"** button in the app UI to verify your backend is correctly receiving the raw JSON strings. This payload is sent to your `baseUrl` + `/v1/ingest/debug`.

---

## License

MIT
