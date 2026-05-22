---
title: "applyUnderlying Split Contract — ByeDPI vs Killswitch TUN"
aliases: [applyunderlying-split, tun-applylockdown-split, byedpi-killswitch-tun-split]
tags: [byedpi, killswitch, tun, architecture, sentinel, gotcha]
sources:
  - "daily/2026-05-21.md"
created: 2026-05-21
updated: 2026-05-21
---

# applyUnderlying Split Contract — ByeDPI vs Killswitch TUN

`TunBuilderHelper.applyLockdown` accepts an `applyUnderlying: Boolean = false` parameter that gates whether `setUnderlyingNetworks(null)` is called. The split is per-call-site: ByeDPI engine TUN uses `applyUnderlying = false` (QUIC upstream parity); killswitch startup TUN uses `applyUnderlying = true` (P37 lockdown invariant on WiFi↔Mobile transition).

## Key Points

- `applyUnderlying = false` → ByeDPI engine path (`buildTunBuilder`) — mirrors upstream ByeByeDPI which never calls `setUnderlyingNetworks`; required for QUIC/UDP to route correctly
- `applyUnderlying = true` → killswitch preliminary startup TUN (`applyEngineTunSpec`, WARP/URnetwork) — P37 invariant: lockdown survives WiFi↔Mobile handoff
- Two call-sites exist in `TunBuilderHelper`; sentinels must cover both explicitly
- Sentinel anti-pattern discovered: asserting literal `"applyUnderlying = false"` breaks when a default parameter replaces the literal in source. Correct: assert on the function signature `"applyUnderlying: Boolean = false"` + assert on pass-through `"applyUnderlying = applyUnderlying"`
- `OzeroVpnServiceLockdownKillswitchTest` was rewritten to assert the signature form after a sentinel-breaking refactor

## Details

### Why the Split Exists

`setUnderlyingNetworks(null)` was added as a fix for the P37 incident: on WiFi→Mobile transition, WARP/URnetwork TUN lost routing in killswitch mode. The sentinel enforced this call globally. When ByeDPI was audited against upstream ByeByeDPI 1.7.5 (see [[concepts/byedpi-vpn-pipeline-upstream-divergence]]), the upstream code never calls `setUnderlyingNetworks` — this was found to be the root cause of YouTube/QUIC failure in ByeDPI CMD mode. The over-correction was per-engine: ByeDPI overrides to `false`, WARP/URnetwork keep `true`.

### Sentinel Literal vs Signature

The original sentinel for the killswitch TUN was:

```kotlin
// WRONG: breaks when default parameter replaces inline literal
assertTrue(source.contains("applyUnderlying = false"))
```

After the refactor added `applyUnderlying: Boolean = false` as a function parameter and the body now reads `applyLockdown(builder, callerTag, applyUnderlying = applyUnderlying)`, the literal `"applyUnderlying = false"` no longer appears in the call-site. The sentinel passed vacuously (searching the full file).

Correct sentinel form:

```kotlin
// Assert the parameter exists in the signature
assertTrue(source.contains("applyUnderlying: Boolean = false"))
// Assert the pass-through in the call-site
assertTrue(source.contains("applyUnderlying = applyUnderlying"))
```

This is the **4th sentinel trap type**: asserting a literal value that a default-parameter refactor converts to a pass-through. The sentinel must be updated to assert the signature, not the literal.

### Two Call-Sites Require Two Sentinels

When a function is called from two different code branches with different semantic contracts, a single sentinel using `indexOf` (finds first match) passes vacuously even when the second call-site is wrong. Both call-sites need explicit branch-specific sentinel assertions:

- Sentinel A: `buildTunBuilder` call-site → `applyUnderlying = false` (ByeDPI engine)
- Sentinel B: `applyEngineTunSpec` call-site → `applyUnderlying = true` (WARP/URnetwork)

## Related Concepts

- [[concepts/byedpi-vpn-pipeline-upstream-divergence]] — root cause: `setUnderlyingNetworks(null)` never called by upstream; per-engine fix
- [[concepts/sentinel-anchor-substringafter-trap]] — related sentinel pattern failure: anchor miss
- [[connections/sentinel-trap-family]] — family of sentinel failure modes; literal-vs-signature is the 4th
- [[concepts/vpnservice-builder-traps]] — Builder API gotchas including `setUnderlyingNetworks`

## Sources

- [[daily/2026-05-21.md]] — Session 00:04: split-contract discovery; sentinel `OzeroVpnServiceLockdownKillswitchTest:136` rewrote asserting signature not literal; both call-sites documented
