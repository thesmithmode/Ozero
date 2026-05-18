---
title: "Modular Boundary Violation: Engine-Specific Logic in common-vpn"
aliases: [modular-boundary-violation, engine-specific-in-common, common-vpn-engineid-trap]
tags: [architecture, modularity, vpn, gotcha]
sources:
  - "daily/2026-05-15.md"
created: 2026-05-15
updated: 2026-05-15
---

# Modular Boundary Violation: Engine-Specific Logic in common-vpn

When `common-vpn` module contains conditionals like `if (engineId != EngineId.WARP)` to apply `excludeSelf`, the modular boundary is violated — a shared module now knows about a specific engine. This creates tight coupling: adding a new engine requires modifying `common-vpn`, and engine-specific behavior lives outside its owning module. The correct fix is unconditional behavior in `common-vpn` (always `excludeSelf = true`) with engine-specific overrides living in `engine-*` modules.

## Key Points

- `common-vpn` checking `engineId != EngineId.WARP` to decide `excludeSelf` = modular boundary violation
- Commit 5a8089dd introduced the conditional; commit 9cc8749a (26 minutes earlier) had the correct unconditional `excludeSelf = true`
- The violation was motivated by PORTAL_WG reference (which uses excludeSelf=false for itself) — but Ozero's architecture is different (process isolation, multi-engine)
- Fix: `excludeSelf = true` unconditionally in `common-vpn`; sentinel test added forbidding `EngineId.WARP` string in `common-vpn` source
- `EngineWarp.ipProbeRoute()` → `IpProbeRoute.StaticLocation("Cloudflare WARP", null)` — IP detection without self-traffic bypass, engine-local knowledge

## Details

### The Regression

Two commits 26 minutes apart on the same day tell the story:

1. **9cc8749a** (working): `excludeSelf = true` for ALL engines unconditionally in `common-vpn`
2. **5a8089dd** (regression): changed to `excludeSelf = (engineId != EngineId.WARP)` — WARP excluded from self-exclusion, intending to route WARP's own traffic through its TUN for IP detection parity with PORTAL_WG

The regression broke all engines: WARP without self-exclusion → app traffic enters TUN → AWG (in separate process) can't handle it → "connected" but internet dead. The symptom was identical for all engines but the fix was immediate: revert to unconditional `true`.

### Why common-vpn Must Not Know Engines

Ozero's architectural contract: `common-vpn` provides VPN infrastructure (TUN setup, split tunnel, routing). Engine-specific behavior lives in `engine-*` modules. When `common-vpn` inspects `EngineId`:

1. **Coupling**: adding `engine-foo` requires editing `common-vpn` if `foo` needs different self-exclusion
2. **Testing**: `common-vpn` tests need awareness of engine IDs they should be agnostic to
3. **Reasoning**: developers reading `common-vpn` must understand WARP-specific constraints to maintain the conditional
4. **Regression risk**: the conditional was wrong (excluded WARP from a necessary protection) and the wrongness was non-obvious because it required multi-engine testing to discover

### Sentinel Enforcement

A sentinel test now asserts that `EngineId.WARP` (and any engine-specific identifier) does not appear in `common-vpn` source files. Two previous sentinel tests that *protected* the broken conditional (`excludeSelf=(engineId != WARP)`) were deleted — they were classic [[concepts/sentinel-protecting-bug-trap]] instances.

### IP Detection for WARP

The motivation for the conditional was IP detection: with `excludeSelf=true`, the app's HTTP requests bypass the TUN, so an IP checker shows the real ISP IP, not the WARP exit IP. The correct fix is engine-local:

`EngineWarp.ipProbeRoute()` returns `IpProbeRoute.StaticLocation("Cloudflare WARP", null)` — the engine knows its exit location statically (it's always Cloudflare). No HTTP request needed, no self-traffic routing needed. This keeps the knowledge in `engine-warp` where it belongs.

A future P2 improvement could implement IP detection via WireGuard UAPI within `engine-warp`, but the static approach is correct and sufficient.

### Auto-Mode Gap

The investigation also revealed that auto-mode (engine priority chain) cannot detect traffic-fail after start — only preflight-fail triggers fallback to the next engine. WARP "starting" but dead (due to missing excludeSelf) appeared as a successful start to the auto-mode logic, preventing fallback. This is a separate architectural gap documented for future work.

## Related Concepts

- [[concepts/tun-self-exclusion-sdk-engines]] - The excludeSelf invariant that this modular boundary violation broke
- [[concepts/ip-probe-route-architecture]] - IpProbeRoute sealed class; StaticLocation is the correct WARP override
- [[concepts/sentinel-protecting-bug-trap]] - Two sentinels guarding the broken conditional had to be deleted
- [[concepts/engine-ownership-boundary]] - Related principle: VpnService owns lifecycle, engines own their behavior

## Sources

- [[daily/2026-05-15.md]] - Session 12:27: git-bisect 9cc8749a→5a8089dd; `excludeSelf=(engineId != WARP)` in common-vpn broke all engines; fix = unconditional `true`; sentinel forbids EngineId.WARP in common-vpn; EngineWarp.ipProbeRoute → StaticLocation; auto-mode post-start failure detection gap identified
