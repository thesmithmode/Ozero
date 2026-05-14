---
title: "Android Foreground Service for Long-Running Background Operations"
aliases: [foreground-service-strategy-scan, fgs-long-operation, strategy-scan-service]
tags: [android, architecture, foreground-service, pattern]
sources:
  - "daily/2026-05-14.md"
created: 2026-05-14
updated: 2026-05-14
---

# Android Foreground Service for Long-Running Background Operations

Operations exceeding a few seconds in Android (e.g., iterating over 75 strategies × ~5s = ~6 minutes) are killed by the OS when the app moves to background. `Service.startForeground()` with a persistent notification prevents this. `START_NOT_STICKY` is preferred for user-initiated scan operations — if killed, don't auto-restart. Cleanup belongs in a `finally` block inside the coroutine, not `onCleared()`, because `viewModelScope` cancellation automatically propagates through the coroutine and triggers `finally`.

## Key Points

- Operations > ~1 minute require Foreground Service to survive background; `START_NOT_STICKY` for user-initiated ops (no auto-restart on kill)
- FGS type `FOREGROUND_SERVICE_DATA_SYNC` correct for data processing operations (strategy scanning)
- `finally` block in coroutine for cleanup (stopService, cancel) is more reliable than `onCleared()` — viewModelScope cancellation propagates automatically into the coroutine
- Notification ID must not conflict with existing IDs (VPN uses id=1; strategy scan uses id=23)
- Test pattern: `mockk<Context>(relaxed=true)` for ViewModel constructor when Context is needed for FGS start/stop

## Details

### Why Foreground Service

Android aggressively kills background processes to save memory. A strategy scan iterating 75 ByeDPI strategies takes ~6 minutes (`75 × ~5s`). Without a Foreground Service, the OS kills the app process within ~1 minute of backgrounding, aborting the scan and losing all results.

`startForeground(NOTIFICATION_ID, notification)` shows a persistent notification and elevates the service to a foreground process that Android will not kill due to resource pressure. The notification must appear within 10 seconds of service start (Android requirement for FGS).

### START_NOT_STICKY

`START_NOT_STICKY` means if the system kills the service due to resource pressure, it will NOT automatically restart. This is correct for user-initiated scans: the user explicitly tapped "Start scan" and a notification appeared. If killed, the user should manually restart. `START_STICKY` would restart with a `null` Intent — requiring null-handling and restarting the scan without user consent.

### finally Block vs onCleared

```kotlin
// StrategyTestViewModel
viewModelScope.launch {
    try {
        context.startForegroundService(Intent(context, StrategyScanService::class.java))
        runScan()
    } finally {
        context.stopService(Intent(context, StrategyScanService::class.java))
    }
}
```

`onCleared()` runs when the ViewModel is destroyed — asynchronously, after the coroutine may already be cancelled. A `finally` block runs immediately when the coroutine scope is cancelled (via `viewModelScope.cancel()`). Kotlin guarantee: `finally` always runs before coroutine termination, including on cancellation — making it the reliable cleanup point for operations started inside a coroutine.

### Notification ID Collision Avoidance

Each Foreground Service notification must have a unique ID within the app. ID collisions cause one notification to overwrite another, potentially breaking the FGS contract. Register IDs in a central constants file:

| Service | ID |
|---------|-----|
| VPN (OzeroVpnService) | 1 |
| Strategy Scan (StrategyScanService) | 23 |

### Test Pattern

ViewModels that take `@ApplicationContext context` for FGS operations need a mocked context in tests:

```kotlin
val ctx = mockk<Context>(relaxed = true)  // relaxed = no strict stub needed
val vm = StrategyTestViewModel(ctx, fakeEngine, fakeStore)
```

`relaxed = true` stubs all Context methods to return default values — prevents `MockKException: no answer found` for unprepared calls on Context (e.g., `getSystemService`, `getPackageName`).

## Related Concepts

- [[concepts/genetic-strategy-evolution]] - The strategy evolution engine that StrategyScanService wraps; 6-minute evolution runs require FGS
- [[concepts/byedpi-auto-strategy-testing]] - Fixed-list 75-strategy testing; same timing concern applies
- [[concepts/runtest-uncompleted-coroutines-trap]] - Related: coroutine scope lifecycle traps in tests; relaxed Context mock avoids similar NPE patterns

## Sources

- [[daily/2026-05-14.md]] - Session 10:00: StrategyScanService implementation — FOREGROUND_SERVICE_DATA_SYNC, START_NOT_STICKY, finally block for cleanup more reliable than onCleared(), NOTIFICATION_ID=23 no-clash with VPN, mockk relaxed Context in StrategyTestViewModelTest
