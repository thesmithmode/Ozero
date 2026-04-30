---
title: "Connection: Invisible Crash Vectors in Android Startup"
connects:
  - "concepts/compose-launchedeffect-crash-invisibility"
  - "concepts/hilt-di-native-library-failure"
  - "concepts/android-silent-crash-diagnosis"
sources:
  - "daily/2026-04-29.md"
created: 2026-04-29
updated: 2026-04-29
---

# Connection: Invisible Crash Vectors in Android Startup

## The Connection

Both Compose `LaunchedEffect` exceptions and Hilt DI graph failures during native library loading share a critical property: they produce crashes that bypass the app's persistent logging infrastructure. Together, they represent the two primary vectors for "silent crashes" — application terminations that leave no trace in `boot.log` or other app-managed logs.

## Key Insight

The non-obvious relationship is that these two failure modes occupy different phases of the Android lifecycle but create identical diagnostic blind spots. A `LaunchedEffect` crash occurs during Compose tree initialization (phase 2-3 of startup), while a Hilt DI crash occurs during Service `onCreate` injection (phase 5). Despite being causally unrelated, they both exploit the same architectural gap: Ozero's persistent logger (`BootFileLogger`) only receives events explicitly routed to it via `PersistentLoggers`, while both crash types generate exceptions handled by framework code (Compose runtime, Hilt codegen) that logs to in-memory channels or not at all.

This means that a systematic diagnosis of any silent crash must check both vectors, even when initial evidence points to only one. The absence of a log entry cannot distinguish between "crash didn't happen" and "crash happened in a logging blind spot."

## Evidence

During the Ozero VPN silent crash investigation on Nubia NX729J (2026-04-29):
- The `LaunchedEffect` hypothesis explained the absence of boot.log entries for a crash during `setContent`
- The Hilt DI hypothesis explained a potential crash path through `System.loadLibrary` in a service provider
- Both hypotheses were evaluated because the symptom (no log, no click handler reached) was consistent with either vector
- The Hilt hypothesis was ultimately deprioritized for the specific crash (click never reached `onConnectClick` means startService was never called), but both remain documented vulnerability paths

## Related Concepts

- [[concepts/compose-launchedeffect-crash-invisibility]] - Phase 2-3 crash vector (Compose initialization)
- [[concepts/hilt-di-native-library-failure]] - Phase 5 crash vector (Service DI injection)
- [[concepts/android-silent-crash-diagnosis]] - The methodology that must account for both vectors
- [[concepts/nubia-rom-permission-enforcement]] - Device-specific factor that may trigger either vector
