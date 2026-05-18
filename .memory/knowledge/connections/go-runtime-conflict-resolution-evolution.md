---
title: "Connection: Go Runtime Conflict Resolution Evolution"
connects:
  - "concepts/dual-go-runtime-eager-loading"
  - "concepts/engine-switch-chain-cascading-failures"
  - "concepts/go-runtime-process-isolation"
  - "concepts/urnetwork-runtime-release-lifecycle"
sources:
  - "daily/2026-05-07.md"
  - "daily/2026-05-09.md"
  - "daily/2026-05-11.md"
  - "daily/2026-05-18.md"
created: 2026-05-11
updated: 2026-05-18
---

# Connection: Go Runtime Conflict Resolution Evolution

## The Connection

Ozero's dual Go runtime conflict (`libam-go.so` + `libgojni.so`) was addressed through four increasingly correct approaches over v0.0.5–v0.1.2. Each approach fixed one failure class while introducing another, culminating in complementary solutions: process isolation for WARP and explicit runtime release for URnetwork.

## Key Insight

The non-obvious relationship is that each fix was locally correct but globally insufficient because the fundamental constraint — two independent Go runtimes sharing signal handlers, GC state, and heap — cannot be resolved within a single Linux process. The evolution:

1. **Eager loading** (v0.0.5, [[concepts/dual-go-runtime-eager-loading]]): Both libraries loaded in `OzeroApp.onCreate` before any engine starts. Fixed: concurrent init/teardown SIGSEGV. Created: the assumption that both runtimes can peacefully coexist in one process if initialized sequentially.

2. **GoRuntimeGuard** (v0.0.8, [[concepts/engine-switch-chain-cascading-failures]]): A mutex serializing Go JNI access. Intended to prevent concurrent Go runtime operations. Fixed: nothing (eager loading already solved the problem it targeted). Created: deadlock when teardown coroutine is cancelled during rapid engine switching — the guard's `release(owner)` never fires, and the next engine's `acquire(owner)` blocks forever.

3. **Process isolation** (v0.0.12, [[concepts/go-runtime-process-isolation]]): WARP engine runs in `android:process=":engine_warp"` with AIDL IPC. Each Go runtime gets its own process, signal table, and GC heap. Fixed: all Go runtime conflicts definitively — no shared state possible. Trade-off: ~1-2ms IPC latency per engine control call.

4. **Explicit runtime release** (v0.1.2, [[concepts/urnetwork-runtime-release-lifecycle]]): URnetwork SDK Go runtime requires `Sdk.freeMemory()` + `setActiveNetworkSpace(null)` after `bridge.stop()`. Without release, the singleton runtime persists and blocks other Go-based apps (including URnetwork's own app) from initializing their SDK. Fixed: cross-app conflict where Ozero process alive = URnetwork-app crashes. This is complementary to process isolation — WARP uses process separation, URnetwork uses explicit lifecycle management within the same process.

## Evidence

- v0.0.5: eager loading prevented the original SIGSEGV during engine init/teardown concurrency
- v0.0.8: GoRuntimeGuard added as "defense in depth" — but was defense against a threat that eager loading had already neutralized
- v0.0.9–v0.0.11: GoRuntimeGuard deadlock discovered during rapid engine switching (7 `startVpn` in 30s); guard removed
- v0.0.11: After guard removal, Go GC SIGABRT continued during `UrnetworkEngineSettingsViewModel` polling (bridge JNI calls during teardown) — Engine Ownership Boundary violation
- v0.0.12: Process isolation + Engine Ownership Boundary established as the definitive solution for WARP
- v0.1.2: URnetwork runtime release discovered as necessary — unreleased Go singleton crashed URnetwork-app on same device; `UrnetworkRuntime.release()` added to teardown

The pattern demonstrates two anti-patterns: (1) adding synchronization primitives (mutex/guard) when architectural separation is needed, and (2) assuming `stop()` equals full cleanup when native singletons persist beyond method scope.

## Related Concepts

- [[concepts/dual-go-runtime-eager-loading]] - Phase 1: single-process eager loading
- [[concepts/engine-switch-chain-cascading-failures]] - Phase 2: GoRuntimeGuard deadlock discovery
- [[concepts/go-runtime-process-isolation]] - Phase 3: definitive fix for WARP via OS-level process separation
- [[concepts/urnetwork-runtime-release-lifecycle]] - Phase 4: explicit release for URnetwork Go runtime after stop
- [[concepts/engine-ownership-boundary]] - Complementary architectural principle: UI must not call bridge JNI during teardown
- [[connections/symptom-fix-vs-system-removal]] - Same pattern: GoRuntimeGuard was a symptom-patch over a structurally solved problem
