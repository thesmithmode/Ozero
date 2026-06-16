---
title: "WARP Amber Flash: activeConnections=0 at Ready"
aliases: [warp-amber-flash, warp-stats-initialisation, warp-ready-activeconnections]
tags: [warp, android, ui, sentinel, bug, stats]
sources:
  - "daily/2026-05-26.md"
created: 2026-05-26
updated: 2026-06-13
---

# WARP Amber Flash: activeConnections=0 at Ready

When the WARP engine transitions to `ReadyResult.Ready`, the stats object momentarily shows `activeConnections=0` before being updated. This causes a brief amber flash in the connection status UI — the indicator shows "degraded/connecting" for one frame before settling to green. The fix is to initialise `activeConnections=1` at the moment `Ready` is received, before the first stats poll completes.

## Key Points

- Root cause: `_stats.value` is not updated synchronously when `ReadyResult.Ready` fires; default stats have `activeConnections=0`
- Fix (commit `cbbbab76`): `_stats.value = _stats.value.copy(activeConnections = 1)` in the `ReadyResult.Ready` branch
- A sentinel test is **mandatory immediately after this fix** — without it the amber flash regression is invisible in CI
- The sentinel should assert: when `ReadyResult.Ready` is emitted, `stats.activeConnections >= 1` before the next stats poll
- Category: stats initialisation at engine-ready moment; distinct from steady-state stats tracking bugs

## Details

### The Visual Regression

The WARP engine emits `ReadyResult.Ready` when the WireGuard handshake completes and the tunnel is confirmed live. However, the stats state (`_stats`) continues to hold the previous polling cycle's values, which during initial connection has `activeConnections=0`. The UI derives the connection status chip color from `activeConnections`: 0 → amber, ≥1 → green. The result is a single-frame amber flash immediately after the tunnel becomes ready.

This is cosmetically minor but confusing to users who interpret the amber state as a connection problem. It also makes automated UI testing unreliable if the test checks state immediately after `Ready`.

### The Fix

```kotlin
// In WarpEngine or equivalent, where ReadyResult.Ready is handled:
ReadyResult.Ready -> {
    _stats.value = _stats.value.copy(activeConnections = 1)
    // ... rest of Ready handling
}
```

This sets the minimum expected value (`activeConnections=1`) synchronously so the UI transitions directly from "connecting" to "connected" without an amber intermediate state.

### Sentinel Requirement

This fix has no unit test by default. The amber flash is a timing issue between state emissions — it only manifests if the stats poll fires before the `Ready` handler sets the value. The fix was identified and applied without a sentinel in commit `cbbbab76`, which was flagged as a gap in session 21:41.

The sentinel must:
1. Simulate `ReadyResult.Ready` emission
2. Assert `stats.activeConnections >= 1` **at the moment of Ready**, before any subsequent poll
3. Fail if `activeConnections=0` is ever observed after `Ready` is emitted

Without this sentinel, any future refactor of the stats initialisation path can silently reintroduce the amber flash.

## Related Concepts

- [[concepts/warp-false-connected-no-handshake]] - Related: WARP false connected state; amber flash is the inverse (correctly connected but momentarily shows amber)
- [[concepts/warp-uapi-handshake-polling]] - WARP UAPI handshake polling that drives the Ready transition
- [[concepts/engine-await-ready-pattern]] - Engine await-ready synchronisation pattern
- [[concepts/sentinel-protecting-bug-trap]] - Sentinel discipline: a fix without a sentinel can be silently reverted

## Sources

- [[daily/2026-05-26.md]] - Session 21:41: WARP amber flash fix (`cbbbab76`) identified — `_stats.value = _stats.value.copy(activeConnections = 1)` при ReadyResult.Ready; noted as missing sentinel — "новая категория regression: stats initialisation при Ready должна быть закреплена тестом немедленно после фикса"
