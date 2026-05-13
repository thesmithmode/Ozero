---
title: "ByeDPI Strategy Runtime Disconnect: Test Results Not Applied to Engine"
aliases: [byedpi-winning-args-static, strategy-test-runtime-gap, byedpi-runtime-rotation]
tags: [byedpi, architecture, gotcha, strategy]
sources:
  - "daily/2026-05-12.md"
created: 2026-05-12
updated: 2026-05-12
---

# ByeDPI Strategy Runtime Disconnect: Test Results Not Applied to Engine

`ManualEngineConfigBuilder` reads `byedpiWinningArgs` — a static string from `SettingsRepository` — and passes it to the ByeDPI engine at start time. The Strategy Test UI tab finds the optimal strategy through 75-strategy iteration or genetic evolution, but the winning result is only saved to `byedpiWinningArgs` in DataStore. There is no runtime rotation: the running ByeDPI proxy continues using whatever args it was started with, even if the Strategy Test discovers a better strategy. Additionally, `jniStartProxy=-1` failures (proxy binary startup failure) leave the SOCKS port unbound, and the strategy is never applied because the proxy never started.

## Key Points

- `byedpiWinningArgs` is read once at engine start by `ManualEngineConfigBuilder` — no hot-reload mechanism
- Strategy Test UI discovers optimal args but only writes to DataStore; the running engine is not restarted or reconfigured
- User must manually restart VPN after Strategy Test completes for new args to take effect — no automatic restart trigger
- `jniStartProxy=-1` means the native ByeDPI binary failed to start its SOCKS proxy; no strategy can take effect without a running proxy
- Evolution mode `live re-sort`: `sortedForUi` is now applied after each probe evaluation (not just in `finally`), giving real-time UI feedback — but still doesn't affect the running engine

## Details

### The Static Args Pipeline

The ByeDPI engine lifecycle for strategy application:

1. Strategy Test UI runs 75-strategy or genetic evolution test cycle
2. Winning strategy args are written to `SettingsRepository.byedpiWinningArgs` (DataStore)
3. User taps "Connect" or VPN auto-reconnects
4. `ManualEngineConfigBuilder.build()` reads `byedpiWinningArgs` from DataStore
5. Args are passed to `ByeDpiEngine.start(config)` which calls native `jniStartProxy(args)`
6. The proxy runs with those args for the entire session

There is no step between 2 and 3 that triggers a VPN restart. If the VPN is already connected when Strategy Test completes, the engine continues using the old args. The user sees "Best strategy found: -s5 -o2 -At -f-1" in the test results but must manually disconnect and reconnect for the strategy to take effect.

### jniStartProxy=-1 Failure

The `jniStartProxy` JNI call returns the SOCKS proxy port number on success (typically 1080) or -1 on failure. Failure causes:
- Missing or incompatible `libbyedpi.so` binary
- Port already in use (another ByeDPI instance, or port conflict)
- Invalid args that the native parser rejects (see [[concepts/byedpi-args-parsing]])
- Android SELinux restrictions on the socket operation

When `jniStartProxy=-1`, the SOCKS5 port is never bound. The hev-socks5-tunnel layer has no upstream proxy to forward to, and all traffic through the TUN stalls. The engine reports a start failure, but the diagnostic message is opaque — the -1 return provides no detail about which native operation failed.

### Evolution Mode Live Re-Sort

Sprint 5's evolution mode originally collected all probe results and sorted them only in the `finally` block after the full population was evaluated. This meant the UI showed strategies in their original order during testing, with the final ranking appearing only after completion. The fix applies `sortedForUi` (sorted by fitness descending) after each individual probe completes, giving the user real-time feedback about which strategies are winning as the test progresses. However, this is purely a UI improvement — it does not affect the running engine.

### Architectural Gap

The disconnect between Strategy Test and runtime engine reflects a deliberate design choice: the VPN tunnel is a system-level resource (TUN fd, foreground service, network routing) that should not be disrupted by a background test operation. However, the lack of any notification or automatic restart creates a UX gap:

- User completes 6-minute strategy test → sees "Best strategy: X"
- User assumes "X" is now active → VPN is still using the old strategy
- User browses websites, encounters blocks → files bug report

A future improvement could add a "Применить и перезапустить" (Apply and restart) button on the test results screen, or a persistent notification when the active strategy differs from the test winner.

## Related Concepts

- [[concepts/byedpi-auto-strategy-testing]] - The fixed-list 75-strategy testing that writes winning args to the same static pipeline
- [[concepts/genetic-strategy-evolution]] - The genetic algorithm that also writes results to byedpiWinningArgs without runtime application
- [[concepts/byedpi-args-parsing]] - Invalid args passed to jniStartProxy can cause the -1 return
- [[concepts/engine-switch-chain-cascading-failures]] - VPN restart to apply new args may trigger cascading failures if done during rapid switching

## Sources

- [[daily/2026-05-12.md]] - Session 21:19: ManualEngineConfigBuilder reads byedpiWinningArgs — static string, no runtime rotation; jniStartProxy=-1 prevents any strategy from taking effect; evolutionMode live re-sort applied per-probe for real-time UI
