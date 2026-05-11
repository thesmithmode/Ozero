---
title: "Engine-Switch Chain Cascading Failures"
aliases: [rapid-engine-switching, engine-switch-cascade, startVpn-rapid-fire]
tags: [vpn, architecture, crash, debugging, engine]
sources:
  - "daily/2026-05-09.md"
  - "daily/2026-05-10.md"
created: 2026-05-09
updated: 2026-05-10
---

# Engine-Switch Chain Cascading Failures

Rapid engine switching (7 `startVpn` calls in 30 seconds) in Ozero v0.0.8 caused 4 distinct production bugs that appeared independent but traced to a single root cause: the engine-switch chain. Each `startVpn` interrupts the previous engine's teardown/startup sequence, accumulating stale state across GoRuntimeGuard, IP warmup, and split tunnel initialization. The bugs: empty IP display, URnetwork engine broken (GoRuntimeGuard deadlock), split tunnel empty on entry, split tunnel showing all apps in ALL mode.

## Key Points

- 7 `startVpn` in 30s observed in ozero.log — each interrupts previous engine lifecycle
- Bug 1: IP display empty — warmup fetch (3s delay, reduced from 8s in v0.0.9) cancelled on every restart, never completes
- Bug 2: URnetwork broken — `GoRuntimeGuard` owner stuck on previous engine, blocks new engine's `acquire()`
- Bug 3: Split tunnel empty on entry — initial state `Content(apps=[])` instead of `Loading` causes empty flash
- Bug 4: Split tunnel ALL mode shows app list — should hide list when mode=ALL (user preference)
- Bug 5 (from session 18:38): splitMode in `EngineSettingsRestartObserver.Snapshot` triggers VPN restart on tab toggle → SIGABRT on Nubia
- 100% unit test coverage didn't catch any of these — fakes miss behavioral/integration race conditions
- `OzeroVpnService.kt` touched 6+ times in 2 days = walking in circles without fixing root

## Details

### The Chain Mechanism

When a user rapidly switches engines (e.g., tapping through WARP → URnetwork → ByeDPI → back), each tap triggers `startVpn` which calls `stopVpn` on the current engine before starting the new one. But `stopVpn` involves async teardown (coroutine cancellation, native library cleanup, TUN fd close). If a new `startVpn` arrives before teardown completes, the new engine starts alongside partially-torn-down state from the previous engine.

The log analysis revealed 7 `startVpn` entries in a 30-second window. Each call:
1. Cancels the previous engine's coroutine scope (interrupting teardown)
2. Resets the IP warmup timer (3-second delay, reduced from 8s in v0.0.9, restarts from zero)
3. Attempts `GoRuntimeGuard.acquire(newOwner)` — fails if previous owner never released

### GoRuntimeGuard Deadlock

`GoRuntimeGuard` (commit `633304f`) was a mutex added to prevent concurrent Go runtime access. It requires `acquire(owner)` before using Go JNI and `release(owner)` on teardown. During rapid switching, the teardown coroutine (which calls `release`) is cancelled by the new `startVpn`. The guard's `owner` remains set to the old engine, and the new engine's `acquire()` blocks indefinitely.

This contradicts the eager-loading invariant from [[concepts/dual-go-runtime-eager-loading]]: if both Go runtimes are loaded in `OzeroApp.onCreate` and stay resident, there is no concurrent init/teardown scenario. The guard was a symptom-patch that created a new failure mode (deadlock) worse than the original (SIGSEGV, which eager loading already prevents).

### IP Warmup Cancellation

The IP display uses a warmup delay after engine connect before fetching the external IP (originally 8 seconds, reduced to 3 seconds with 3 retries at 1.5s intervals in v0.0.9). Each engine restart resets this delay. With 7 restarts in 30 seconds, the warmup timer never completes — the fetch never fires, and the IP display remains empty. Users see "Connected" with no IP address, interpreting it as "VPN not working." The v0.0.9 tuning (8→3s, 4→3 retries, 1000→1500ms retry delay) mitigated but did not eliminate this for rapid switching.

### Split Tunnel State Bugs

Two split tunnel bugs were both UI-state issues unrelated to the engine-switch chain itself but discovered during the same debugging session:

1. **Empty on entry**: `SplitTunnelViewModel` initialized with `Content(apps=[])` instead of `Loading`. The app list loads asynchronously, but the UI shows an empty list during the loading window.

2. **ALL mode shows list**: When split tunnel mode is ALL (route everything), the app list is irrelevant — showing it confuses users. The fix hides the list when mode=ALL. Design decision: user preference over Karing's always-show approach.

### splitMode Restart Observer (Session 18:38)

A fifth bug was found: `splitMode` was included in `EngineSettingsRestartObserver.Snapshot`. Toggling split tunnel tabs (ALL/ALLOWLIST/BLOCKLIST) changed the snapshot, triggering a VPN restart. On Nubia devices, the restart during `libam-go` cleanup caused SIGABRT. Fix: remove `splitMode` from Snapshot — tab toggling should not restart VPN.

### Advisor Recommendation

Advisor was called before substantive work. Recommendation: treat all bugs as manifestations of one root cause (engine-switch chain) rather than patching each symptom independently. The approach: sentinel tests first, then structural fixes.

## Related Concepts

- [[concepts/dual-go-runtime-eager-loading]] - GoRuntimeGuard contradicts eager-loading invariant; guard should be removed
- [[concepts/warp-handle-leak-sigabrt]] - Related handle lifecycle issue during engine switching
- [[concepts/urnetwork-sdk-integration]] - URnetwork was one of the affected engines
- [[connections/false-positive-engine-status]] - IP warmup cancellation is a fourth false-positive status vector
- [[concepts/vpn-ip-detection-contract]] - Architectural fix for IP detection during rapid switching
- [[concepts/tun-self-exclusion-sdk-engines]] - Another engine-switching regression in the same release cycle

## Sources

- [[daily/2026-05-09.md]] - Session 13:12: 4 production bugs in v0.0.8 traced to engine-switch chain (7 startVpn in 30s); GoRuntimeGuard deadlock, IP warmup cancelled, split tunnel state bugs; advisor recommended single root cause approach
- [[daily/2026-05-09.md]] - Session 18:38: splitMode in Snapshot caused VPN restart on tab toggle → SIGABRT; removed from observer; BYPASS_LAN mode removed from UI
