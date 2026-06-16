---
title: "Connection: Runtime Symptom Fixes Need Reference-Proof Backstops"
aliases: [symptom-fix-reference-proof-loop, runtime-reference-proof-loop, honest-root-cause-audit-loop]
tags: [runtime, ci, reference, audit, connection]
sources:
  - "daily/2026-05-19.md"
created: 2026-06-12
updated: 2026-06-12
---

# Connection: Runtime Symptom Fixes Need Reference-Proof Backstops

The 2026-05-19 sessions connect WARP, ByeDPI, URnetwork, and UI regressions through one review loop: user-visible runtime symptoms are easy to "fix" at the nearest knob, but durable fixes require proving the owning layer against logs, reference source, and sentinels.

## Key Points

- WARP timeout changes looked plausible until primary-source review proved `handle=0` was valid.
- ByeDPI default args changes looked plausible until runtime mode analysis showed winning/CMD args could override defaults.
- URnetwork UI bugs were fixed correctly when the owning source of truth was identified: `filterLocations("")` for SDK delivery and `TunnelState` for auto-mode peer visibility.
- CI green and release success did not prove device behavior for traffic, WARP handshake, or ByeDPI throughput.
- The recurring corrective action is an honest audit: classify symptom fixes, revert wrong assumptions, and add sentinels at the owner layer.

## Details

The non-obvious relationship is that the same failure mode appears across very different modules. In WARP, changing timeout constants treated missing handshake as a timing issue; in ByeDPI, changing defaults treated traffic failure as a preset issue; in URnetwork UI, checking `manualEngine` treated visibility as a settings issue. Only the fixes that targeted the owning layer survived review.

This connection ties [[concepts/warp-awg-handle-zero-valid]], [[concepts/byedpi-reference-parity-scope-discipline]], and [[concepts/urnetwork-peer-column-auto-mode]]. Each article records a specific instance where the correct source of truth was not the first visible symptom. The broader practice matches [[connections/runtime-regression-signal-separation-loop]]: split runtime failures by concrete signals before batching fixes.

The release process implication is explicit. A green CI run or successful APK publication is delivery evidence, not runtime proof. Device-blocked items must remain open when the observable signal requires a live VPN slot, Cloudflare WARP endpoint, DPI path, or URnetwork SDK response.

## Related Concepts

- [[concepts/warp-awg-handle-zero-valid]] - Corrects the WARP `handle=0` false root cause.
- [[concepts/byedpi-reference-parity-scope-discipline]] - Requires layer-scoped reference parity claims.
- [[concepts/urnetwork-peer-column-auto-mode]] - Shows runtime state, not manual config, owns peer-column visibility.
- [[connections/runtime-regression-signal-separation-loop]] - General runtime regression triage loop across engines.

## Sources

- [[daily/2026-05-19.md]] - Honest root-cause audit and later sessions: WARP timeout/default-handle assumptions were revised; ByeDPI default args and YAML parity were narrowed by runtime mode and reference checks; URnetwork peer column auto-mode bug was fixed by using `TunnelState` visibility; device-only verification items remained open after release.
