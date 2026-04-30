---
title: "Android Silent Crash Diagnosis Methodology"
aliases: [silent-crash-debugging, invisible-crash-diagnosis]
tags: [android, debugging, crash-diagnosis, methodology]
sources:
  - "daily/2026-04-29.md"
created: 2026-04-29
updated: 2026-04-29
---

# Android Silent Crash Diagnosis Methodology

A silent crash is an Android application crash that leaves no trace in the app's own logging infrastructure. The process terminates (or the Activity is destroyed) before persistent logs are flushed, making the crash invisible to in-app diagnostics. Systematic diagnosis requires external tools and a structured elimination of crash vectors.

This methodology was developed during investigation of an Ozero VPN silent crash on Android 15 (Nubia NX729J) where the VPN toggle click never reached the `onConnectClick` handler and `boot.log` contained no relevant entries.

## Key Points

- Absence of evidence in app logs is not evidence of absence — multiple crash vectors bypass persistent logging
- External diagnostic tools are essential: `adb logcat -b crash`, `ActivityManager.getHistoricalProcessExitReasons()` (dumpExitReasons), `filesDir/crashes/`
- Diagnosis proceeds by phase: pre-setContent → Compose init → LaunchedEffect → user interaction → startService → service onCreate
- Each phase has distinct crash vectors: native init, Compose runtime, coroutine exceptions, Hilt DI graph, permission enforcement
- Vendor ROMs (Nubia, Xiaomi, Samsung) may enforce stricter policies than AOSP, adding device-specific crash vectors

## Details

### Diagnostic Data Collection

When a silent crash is suspected, the following data should be collected before any fix attempt:

1. **Full boot.log** after device reboot (captures persistent logger output across process restarts)
2. **filesDir/crashes/** directory contents (if the app implements crash file storage)
3. **`adb logcat -b crash`** — Android's crash log buffer, independent of app logging
4. **`ApplicationExitInfo`** via `dumpExitReasons` — API 30+ provides structured exit reason data including native crashes, ANRs, and system kills

### Phase-Based Elimination

The investigation follows the execution path from process start to the observed failure point:

1. **Application bootstrap** (`attachBaseContext`, `onCreate`) — native library loading, DI container init
2. **Activity creation** (`onCreate`, `setContent`) — Compose tree construction, theme/state initialization
3. **Compose runtime** (`LaunchedEffect`, `remember`, `collectAsState`) — coroutine exceptions, state observation failures
4. **User interaction** (click handlers, navigation) — event handler registration, callback wiring
5. **Service lifecycle** (`startService`, `onCreate`, `onStartCommand`) — Hilt injection, foreground service requirements

Each phase has characteristic failure modes and characteristic logging gaps. The key insight is that phases 2-3 often produce crashes that are logged only to in-memory buffers, not to persistent storage.

### Hypothesis Elimination

During the Ozero investigation, the following were systematically evaluated and ruled out:

- **POST_NOTIFICATIONS permission** — not the cause (startForeground not yet reached at crash point)
- **registerForActivityResult timing** — normal behavior (field initializer runs before super.onCreate)
- **SecurityWatchdog** — safe (graceful return with Toast, no crash path)
- **Android 15 specialUse FGS** — not a breaking change for VPN type services

## Related Concepts

- [[concepts/compose-launchedeffect-crash-invisibility]] - A primary vector for silent crashes where exceptions bypass persistent logging
- [[concepts/hilt-di-native-library-failure]] - Another vector where DI graph failure produces opaque crashes
- [[concepts/nubia-rom-permission-enforcement]] - Device-specific behavior that complicates diagnosis

## Sources

- [[daily/2026-04-29.md]] - Full multi-agent debug session investigating Nubia NX729J silent crash; methodology developed through systematic hypothesis elimination
