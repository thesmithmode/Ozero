---
title: "Compose LaunchedEffect Crash Invisibility"
aliases: [launchedeffect-silent-crash, compose-exception-loss]
tags: [android, compose, logging, crash-diagnosis]
sources:
  - "daily/2026-04-29.md"
created: 2026-04-29
updated: 2026-04-29
---

# Compose LaunchedEffect Crash Invisibility

Exceptions thrown inside a Jetpack Compose `LaunchedEffect` block during `setContent` are caught by the Compose runtime and routed to the in-memory `LogBuffer` (via `AppLogger`), but never reach persistent storage such as `BootFileLogger`. This means that if the process dies shortly after (or the exception is fatal enough to crash the Activity), the crash evidence is lost entirely — it never appears in `boot.log` on disk.

This was discovered during investigation of a silent crash on Android 15 (Nubia NX729J) where the VPN connect button click never reached `onConnectClick` and no trace appeared in `boot.log`.

## Key Points

- `LaunchedEffect` exceptions go to in-memory `AppLogger`/`LogBuffer`, not to `BootFileLogger` on disk
- If the process dies before the in-memory buffer is read (e.g., via the UI Logs tab), the crash trace is permanently lost
- `boot.log` absence does not prove "no crash happened" — it only proves "no crash was logged to persistent storage"
- This is a structural gap in Ozero's logging architecture: the persistent logger (`BootFileLogger`) and the UI logger (`LogcatReader` → `LogBuffer`) are separate channels
- Diagnosing such crashes requires external tools: `adb logcat -b crash`, `dumpExitReasons`, or `filesDir/crashes/`

## Details

In the Ozero VPN app, logging is split into two independent channels. `BootFileLogger` writes to `filesDir/debug/boot.log` as a persistent, append-only file initialized from `attachBaseContext`. `LogcatReader` feeds an in-memory ring buffer used by the UI Logs tab. These channels do not share a write path for all event types.

When a `LaunchedEffect` inside `MainActivity.setContent` throws an exception, the Compose runtime catches it internally. The exception propagates through the coroutine exception handler, which logs to `AppLogger` — the in-memory path. `PersistentLoggers` (the disk path) is only invoked explicitly for events marked as critical (`error`/`warn` calls). A `LaunchedEffect` crash does not go through `PersistentLoggers` unless the developer explicitly wraps it.

The practical consequence: a crash that occurs between `setContent` and the first user interaction (like clicking the VPN toggle) produces zero evidence in `boot.log`. The only reliable way to capture such crashes is via ADB or Android's built-in crash reporting (`logcat -b crash`, `ApplicationExitInfo` via `dumpExitReasons`).

## Related Concepts

- [[concepts/android-silent-crash-diagnosis]] - This crash invisibility is a primary cause of silent crashes requiring external diagnostic tools
- [[concepts/hilt-di-native-library-failure]] - Another vector for early-lifecycle crashes that may be invisible for the same reason

## Sources

- [[daily/2026-04-29.md]] - Discovered during Nubia NX729J silent crash investigation; click never reached onConnectClick, no boot.log entry found
