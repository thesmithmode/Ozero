---
title: "WARP AWG Obfuscation Required for Russian ISPs"
aliases: [warp-awg-tspu, warp-vanilla-revoked, warp-russian-isps]
tags: [warp, amneziawg, tspu, obfuscation, invariant]
sources:
  - "daily/2026-05-07.md"
created: 2026-05-07
updated: 2026-05-07
---

# WARP AWG Obfuscation Required for Russian ISPs

The invariant "Cloudflare WARP always uses vanilla WireGuard" was revoked on 2026-05-07. A real working WARP config from a Russian ISP environment (TSPU active) included AmneziaWG obfuscation fields (Jc=5, Jmin=100, Jmax=200, H1-H4). Without these fields, TSPU blocks the WireGuard handshake. `WarpIniBuilder` was updated to write AWG fields when `awgParams != VANILLA`, and the `forceVanilla=true` hack introduced in c173637 was reverted.

## Key Points

- Vanilla WireGuard (AwgParams.VANILLA = all zeros) is blocked by TSPU on Russian ISPs
- Real Cloudflare WARP configs obtained via mirror API include AWG obfuscation params: Jc=5, Jmin=100, Jmax=200, H1-H4
- `WarpIniBuilder` must write AWG fields when `awgParams != VANILLA` — prior behavior (always vanilla) broke WARP under TSPU
- `forceVanilla=true` in `RealWarpSdkBridge` was a wrong interim fix — reverted; correct fix is propagating awgParams from WarpConfig
- `AwgParams()` default constructor ≠ `AwgParams.VANILLA`: default = (Jc=5, Jmin=100, Jmax=200), VANILLA = all zeros — confusing them causes silent misconfiguration

## Details

### Invariant Revocation

Earlier documentation (including a memory record `project_warp_awg_invariants.md` and `PORTAL_WG README fact 2`) stated that Cloudflare WARP always negotiates vanilla WireGuard and AWG params are irrelevant for WARP. This was based on the assumption that Cloudflare's own WARP servers speak plain WireGuard.

The revocation came from a user-provided working config (`WARP_STR2565.conf`) obtained from a Russian ISP environment with TSPU active. That config contained:
```
Jc = 5
Jmin = 100
Jmax = 200
H1 = <value>
H2 = <value>
H3 = <value>
H4 = <value>
```

This proves that either: (a) the WARP mirror API already generates AWG-enhanced configs for Russian IPs, or (b) TSPU detection happens server-side and Cloudflare returns obfuscated configs. Either way, `WarpIniBuilder` must faithfully write whatever AWG params arrive in `WarpConfig` rather than hardcoding vanilla output.

### AwgParams Confusion Trap

`AwgParams()` (no-arg constructor) produces `(Jc=5, Jmin=100, Jmax=200, S1=0, S2=0, H1=0, H2=0, H3=0, H4=0)` — these are AmneziaWG library defaults. `AwgParams.VANILLA` is a sentinel value with all fields zero, used to signal "write plain WireGuard, no obfuscation." These are NOT the same object, and using `AwgParams()` when VANILLA is intended causes the `WarpIniBuilder` to write Jc/Jmin/Jmax lines into the WireGuard config, which AmneziaWG interprets as obfuscation. The reverse confusion (using VANILLA when obfuscation is needed) produces plain WireGuard that TSPU blocks.

The guard in `WarpIniBuilder`:
```kotlin
if (awgParams != AwgParams.VANILLA) {
    appendLine("Jc = ${awgParams.jc}")
    appendLine("Jmin = ${awgParams.jmin}")
    // ...
}
```

### CI Commit Without CI Run

Commit `ee1c1ea` introduced `forceVanilla=false` and a test expecting VANILLA output — a test/impl mismatch. This was undetected because `gh run list --commit ee1c1ea` returned empty — CI was not triggered on that commit. The lesson: CI green on the branch tip does not guarantee intermediate commits were validated. Inconsistencies introduced in non-HEAD commits can persist until the next full CI run.

## Related Concepts

- [[concepts/amnezia-wg-warp-migration]] - The migration that introduced AwgParams and WarpIniBuilder
- [[concepts/amneziawg-turnon-minus-one]] - AWG tunnel startup failures; obfuscation params affect tunnon behavior
- [[concepts/warp-config-generator-api]] - Mirror API provides the raw INI that WarpIniBuilder parses; now must preserve AWG fields

## Sources

- [[daily/2026-05-07.md]] - Session 15:11: user provided WARP_STR2565.conf with AWG fields; advisor confirmed vanilla invariant wrong under TSPU; forceVanilla reverted; AwgParams() vs VANILLA confusion documented
