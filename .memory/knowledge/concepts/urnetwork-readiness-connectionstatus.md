---
title: URnetwork readiness needs SDK connectionStatus
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---

# URnetwork readiness needs SDK connectionStatus

## Key Points
- `grid.windowCurrentSize` or peer count is not equivalent to URnetwork SDK connection readiness.
- Reference behavior uses `ConnectViewController.connectionStatus`; Ozero readiness should accept `CONNECTED` as a primary signal.
- Peer count remains useful telemetry, but it must not be the only startup gate.
- Increasing `awaitReady` timeout to 5 minutes is insufficient if the readiness predicate is wrong.

## Details

During the v1.0.3 review, URnetwork was found to have a deeper readiness problem than the release fix initially addressed. The release fix mostly increased the wait window, matching the user's expectation that peer discovery can continue for about five minutes. The architectural review found that waiting longer does not fix a wrong gate: an SDK-level `CONNECTED` state can be meaningful even while the grid peer count is still zero.

The chosen root fix was to expose `connectionStatus()` through `UrnetworkSdkBridge`, subscribe through `addConnectionStatusListener`, clean the subscription on stop, and let `awaitReady()` accept either `CONNECTED` or `peers > 0`. This aligns startup readiness with the reference URnetwork app while preserving peer count as a secondary metric. It also connects to prior URnetwork lessons in [[concepts/urnetwork-provide-tun-investigation]] and [[concepts/urnetwork-provide-secret-keys-identity]]: SDK lifecycle signals and identity setup cannot be replaced by indirect counters.

## Related Concepts
- [[concepts/urnetwork-provide-tun-investigation]]
- [[concepts/urnetwork-provide-secret-keys-identity]]
- [[concepts/urnetwork-jwt-bootstrapper]]
- [[concepts/release-regression-evidence-checklist]]
- [[connections/release-regression-ci-vs-runtime-proof]]

## Sources
- [[daily/2026-05-28.md]] records that URnetwork initially dropped after `awaitReady` around 45 seconds and was later extended to 300000 ms.
- [[daily/2026-05-28.md]] records the review finding that readiness based only on grid peer count was architecturally wrong.
- [[daily/2026-05-28.md]] records the decision to accept SDK `connectionStatus=CONNECTED` as a readiness signal and clean the listener during stop.
