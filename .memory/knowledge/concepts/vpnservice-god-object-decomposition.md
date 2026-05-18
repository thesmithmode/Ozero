---
title: "OzeroVpnService God-Object Decomposition"
aliases: [vpnservice-decomposition, god-object-extraction, vpn-service-refactor]
tags: [architecture, android, refactoring, vpn, pattern]
sources:
  - "daily/2026-05-16.md"
created: 2026-05-16
updated: 2026-05-16
---

# OzeroVpnService God-Object Decomposition

`OzeroVpnService.kt` grew to 1010 lines with 6 distinct responsibilities: VPN lifecycle, engine orchestration, TUN configuration, state management, split tunnel, and network monitoring. The decomposition extracted 5 coordinators/helpers, reducing the service to a ~270-line facade. The extraction preserved all existing sentinel tests by updating anchor assertions to point at the new coordinator classes.

## Key Points

- OzeroVpnService reduced from 1010 → ~270 lines via 5 extractions in sequential sub-tasks (72.1–72.5)
- Extracted classes: notification helper (buildNotification + enterForegroundOrLog), `TunBuilderHelper` (applyEngineTunSpec + buildTunBuilder), `EngineWatchdogCoordinator` (health/killswitch watcher + peer watchdog + engine failure handling), `StartSequenceCoordinator` (engine start sequence), `ShutdownCoordinator` (stopVpn + performShutdown + recordSessionEnd)
- `handleEngineFailure` kept public in `EngineWatchdogCoordinator` — called from 4 locations in VpnService
- Every extraction required immediate grep of sentinel test anchors — lifecycle test, peer watchdog test, split tunnel sentinel, lockdown killswitch test all had anchors to extracted methods
- `startSequence by lazy` — lazy initialization prevents coordinator creation before service dependencies are ready
- State grouped into bundles to avoid detekt `LongParameterList` on coordinator constructors

## Details

### Decomposition Strategy

The decomposition followed a sequential sub-task approach where each extraction was a self-contained commit:

1. **72.1 Notification helper**: `buildNotification()` and `enterForegroundOrLog()` extracted. Constants for notification channel/ID moved with them. Sentinel test for notification constants migrated.

2. **72.2 TunBuilderHelper**: `applyEngineTunSpec()` and `buildTunBuilder()` extracted. These methods configure the Android VPN TUN interface with routes, DNS, MTU, and split tunnel rules. The helper takes VpnService as a constructor parameter (needed for `Builder` creation).

3. **72.3 EngineWatchdogCoordinator**: Health monitoring, killswitch watcher, peer watchdog for URnetwork, and engine failure handling extracted. This was the largest extraction — these functions share state (active engine, running flag) and interact with each other (peer loss → engine failure → killswitch check). `handleEngineFailure` remained public because `OzeroVpnService` calls it from `onStartCommand` error paths.

4. **72.4 StartSequenceCoordinator**: The `runStartSequence()` method and its helpers extracted. This coordinator owns the engine start lifecycle: build TUN → start engine → await ready → record session start. Uses `by lazy` initialization because it references other coordinators that may not be ready at VpnService construction time.

5. **72.5 ShutdownCoordinator**: `stopVpn()`, `performShutdown()`, and `recordSessionEnd()` extracted. The shutdown sequence has strict ordering constraints (engine stop BEFORE TUN close, session recording after stop) that are preserved as a single coordinator.

### Sentinel Test Migration

The most error-prone part of the extraction was updating sentinel tests. Ozero's sentinel tests use source-text anchors (`substringAfter("functionName")`) to verify that specific patterns exist in production code. When a function moves from `OzeroVpnService.kt` to `EngineWatchdogCoordinator.kt`, the sentinel must update both the file path and potentially the anchor string.

Tests affected:
- `OzeroVpnServiceLifecycleTest` — anchors for `onCreate`, `onStartCommand`, `startVpn` updated
- `PeerWatchdogTest` — anchors moved to `EngineWatchdogCoordinator`
- `LockdownKillswitchTest` — anchors moved to `EngineWatchdogCoordinator`
- `SplitTunnelVpnServiceSentinelTest` — anchors updated to point at `TunBuilderHelper`

### Lessons from the Decomposition

The user provided critical feedback during this task: the decomposition took ~1 hour, which was too slow. Root causes identified:
- Calling `advisor()` on trivial extract-method operations (no genuine ambiguity)
- Writing 13+ sentinel tests for mechanical moves (compiler already validates the extraction)
- Delegating 30-second `Edit` operations to subagents with full context transfer overhead

The agreed balance: 1 sentinel per extraction + compile verification = sufficient for low-risk refactoring. Advisor reserved for genuine design decisions, not mechanical moves.

### Additional Audit Fixes During Decomposition

The decomposition session also addressed several AUDIT.md findings that were naturally exposed by reading the full service:
- **#77**: `killswitchCached` was a snapshot at start time — changed to live subscription on settings flow
- **#78**: `TelegramProxyCoordinator` job replacement used plain `var job: Job?` — changed to `AtomicReference<Job?>` for race safety
- **#79**: `runCatching` in crypto verifier caught `Throwable` including OOM — changed to `try/catch (e: Exception)`

## Related Concepts

- [[concepts/engine-ownership-boundary]] - VpnService remains the sole lifecycle owner; coordinators are internal helpers, not independent actors
- [[concepts/suppress-annotation-decomposition]] - Same principle: decompose instead of suppress; applied to ExpertMainContent badges extraction during same session
- [[concepts/sentinel-anchor-substringafter-trap]] - Sentinel anchors must be updated when code moves between files; related trap discovered in same session

## Sources

- [[daily/2026-05-16.md]] - Sessions 14:29–15:34: sequential decomposition of OzeroVpnService 1010→~270 lines; 5 coordinators extracted; sentinel tests migrated; user feedback on ceremony overhead; AUDIT.md findings #77/#78/#79 fixed
