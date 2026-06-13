---
title: Multi-engine lifecycle and exit-node regression loop
sources:
  - [[daily/2026-05-29]]
created: 2026-06-13
updated: 2026-06-13
---
# Multi-engine lifecycle and exit-node regression loop

## Key Points
- The 2026-05-29 repair sequence treated ByeDPI, FPTN, URnetwork, relay, and sing-box as connected runtime symptoms rather than isolated module bugs.
- Shared lifecycle poisoning can surface as a neighboring engine failure, so fixes must separate root event, stale callback, and final UI label.
- Readiness gates must be short at startup and move long recovery windows into runtime watchdogs.
- Exit-node display is part of the same runtime proof boundary because direct probes can show a false success with the device IP.
- CI is necessary but not sufficient; each engine fix needs a sentinel for the specific runtime contract it changed.

## Details

The daily log shows a staged regression loop. A repeated ByeDPI start could wedge a native/proxy lane and poison `ChainOrchestrator`; FPTN could continue serial auth fallback after stop or switch; URnetwork could block startup for 300 seconds on `peers=0`; sing-box could report an exit IP from the wrong route. Each symptom was user-visible, but the repair strategy depended on finding the owning layer before patching.

The non-obvious connection is that a visible `Failed(BYEDPI, timeout)` or wrong exit IP may not belong to the engine named in the UI. It can be a stale lifecycle event, a long startup still holding a mutex, or a direct diagnostic request bypassing the active engine route. Therefore the correct loop is evidence first: compare against `v0.2.0`, map log timestamps to start/stop/state transitions, identify the owner, add a focused fix, and protect the contract with targeted tests.

This also explains the final ordering. ByeDPI lane isolation came first to remove shared poisoning. FPTN cancellation and DNS/WebSocket boundaries came next. URnetwork readiness was fixed by separating startup from runtime peer grace. Relay was handled separately from the client engine. sing-box exit IP was fixed through routed probe strategy, then generalized into `ExitNodeStrategy`.

## Related Concepts
- [[concepts/byedpi-wedged-lane-restart-isolation]]
- [[concepts/fptn-cancellation-cooperative-auth-lifecycle]]
- [[concepts/fptn-upstream-dns-websocket-boundary]]
- [[concepts/urnetwork-startup-readiness-runtime-peer-grace]]
- [[concepts/urnetwork-relay-provideenabled-sol-contract]]
- [[concepts/exit-node-strategy-resolver-contract]]
- [[concepts/singbox-exit-ip-probe-chain-socks]]

## Sources
- [[daily/2026-05-29]]: records the staged plan and implementation order from ByeDPI to FPTN, URnetwork, relay, sing-box, CI, and review.
- [[daily/2026-05-29]]: records that false `Failed(BYEDPI, timeout)` events were treated as possible stale/cross-engine lifecycle effects.
- [[daily/2026-05-29]]: records that URnetwork `CONNECTING peers=0` was moved from startup waiting into runtime peer grace.
- [[daily/2026-05-29]]: records that sing-box exit IP must be measured through the active outbound graph and not direct HTTP fallback.
