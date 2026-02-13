# MacroDroid Watchdog Setup Guide

This guide shows how to set up MacroDroid to automatically monitor and restart stalled health sync workers.

## Prerequisites

- WatchdogBridge app installed
- MacroDroid app installed
- Health sync workers configured (60min + 15min flex)

---

## Macro Setup

### Macro 1: Daily Worker Watchdog

**Name:** "Health Sync - Daily Worker Watchdog"

**Trigger:**
- Time: Every 2 hours
- Device Boot

**Conditions:**
- None (always run the check)

**Actions:**
1. **Send Intent**
   - Action: `com.example.watchdogbridge.RESTART_DAILY_WORKER`
   - Package: `com.example.watchdogbridge`
   - Target: Broadcast

2. **Notification** (optional, for visibility)
   - Title: "🔄 Daily Worker Restarted"
   - Text: "Health sync daily worker was restarted by watchdog"
   - Auto-dismiss: 3 seconds

---

### Macro 2: Intraday Worker Watchdog

**Name:** "Health Sync - Intraday Worker Watchdog"

**Trigger:**
- Time: Every 2 hours
- Device Boot

**Conditions:**
- None (always run the check)

**Actions:**
1. **Send Intent**
   - Action: `com.example.watchdogbridge.RESTART_INTRADAY_WORKER`
   - Package: `com.example.watchdogbridge`
   - Target: Broadcast

2. **Notification** (optional, for visibility)
   - Title: "🔄 Intraday Worker Restarted"
   - Text: "Health sync intraday worker was restarted by watchdog"
   - Auto-dismiss: 3 seconds

---

### Macro 3: Restart All Workers (Manual/Emergency)

**Name:** "Health Sync - Restart All Workers"

**Trigger:**
- Quick Settings Tile (for manual trigger)
- OR Notification Action (tap to restart)

**Actions:**
1. **Send Intent**
   - Action: `com.example.watchdogbridge.RESTART_ALL_WORKERS`
   - Package: `com.example.watchdogbridge`
   - Target: Broadcast

2. **Notification**
   - Title: "✅ All Workers Restarted"
   - Text: "Daily and intraday workers have been restarted"
   - Auto-dismiss: 5 seconds

---

## Advanced: Conditional Restart (Only If Stale)

To avoid unnecessary restarts, you can check if the last sync is actually stale.

### Option A: Check File Timestamp

WatchdogBridge app should write last sync time to a file:
- `/sdcard/Android/data/com.example.watchdogbridge/files/last_daily_sync.txt`
- `/sdcard/Android/data/com.example.watchdogbridge/files/last_intraday_sync.txt`

**MacroDroid Condition:**
- File: `/sdcard/Android/data/com.example.watchdogbridge/files/last_daily_sync.txt`
- Modified: More than 90 minutes ago

**Only restart if file is stale.**

### Option B: Use SharedPreferences (requires root or ADB)

If you can access app's SharedPreferences, check `last_sync_timestamp`:

**MacroDroid Condition:**
- Read SharedPreferences
- Check if `System.currentTimeMillis() - lastSyncTime > 5400000` (90 min)

---

## Testing

### Test Manual Restart:

1. Open Terminal/ADB:
   ```bash
   adb shell am broadcast -a com.example.watchdogbridge.RESTART_ALL_WORKERS -n com.example.watchdogbridge/.receiver.WorkerRestartReceiver
   ```

2. Check logcat:
   ```bash
   adb logcat | grep WorkerRestartReceiver
   ```

Expected output:
```
D/WorkerRestartReceiver: Received intent: com.example.watchdogbridge.RESTART_ALL_WORKERS
D/WorkerRestartReceiver: Restarting daily worker...
D/WorkerRestartReceiver: Daily worker restarted successfully
D/WorkerRestartReceiver: Restarting intraday worker...
D/WorkerRestartReceiver: Intraday worker restarted successfully
```

### Test MacroDroid Macro:

1. Create the macro in MacroDroid
2. Run it manually (three-dot menu → "Test Actions")
3. Check notification appears
4. Check logcat for worker restart logs

---

## Monitoring

### Check if workers are actually running:

```bash
adb shell dumpsys jobscheduler | grep -A 10 "WatchdogBridge"
```

Look for:
- `health_connect_daily_sync` - should show next run time
- `health_connect_intraday_sync` - should show next run time

### Check WorkManager status:

```bash
adb shell pm dump com.example.watchdogbridge | grep -A 20 "WorkSpec"
```

---

## Troubleshooting

### Intent not received:

1. Check receiver is registered in AndroidManifest
2. Check package name matches (`com.example.watchdogbridge`)
3. Check receiver is `android:exported="true"`

### Workers not restarting:

1. Check WorkManager permissions
2. Check battery optimization is disabled for WatchdogBridge
3. Check app has Health Connect permissions

### MacroDroid macro not running:

1. Check MacroDroid has all required permissions
2. Check battery optimization is disabled for MacroDroid
3. Check "Run in background" is enabled for MacroDroid

---

## Future Improvements

1. **Add heartbeat reporting:** Workers ping server after each sync, MacroDroid checks server status
2. **Persistent notification:** Show "Worker health: ✅" in notification drawer, update every 2h
3. **Smart scheduling:** Only restart during waking hours, skip overnight
4. **Escalation:** If restart fails 3 times, send SMS/Telegram alert

---

## Summary

**MacroDroid sends intent → BroadcastReceiver catches it → Workers restart → Zero human interaction needed**

This creates a fully autonomous watchdog that ensures health data syncing never stops, even if Android kills the background workers.
