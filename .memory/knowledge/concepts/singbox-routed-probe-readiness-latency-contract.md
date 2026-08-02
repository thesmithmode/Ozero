---
title: sing-box readiness and latency have separate proof scopes
sources:
  - daily/2026-05-31.md
created: 2026-05-31
updated: 2026-05-31
---

# sing-box readiness and latency have separate proof scopes

## Key Points
- TCP connect to a profile host or local SOCKS port does not prove sing-box auth, TLS/Reality, routing, or outbound egress.
- Startup readiness is limited to a live runtime and a successful local SOCKS5 handshake.
- Health and latency use a routed HTTP 204-style probe through the sing-box SOCKS route, without direct fallback.
- Probe code must distinguish a runtime it temporarily started from an already connected user tunnel.
- Latency refresh must not write `LATENCY_FAILED` for every profile when the runtime is busy because the user is already connected.
- `activeSocksPort` remains runtime state and must be cleared on failure, disconnect, or process death.

## Details

The 2026-05-31 investigation identified a false-positive sing-box health model: TCP-open only proved that a port accepted connections, not that real traffic worked through the configured outbound chain. The corrected contract is routed proof: the probe must make real HTTP egress through the local SOCKS path owned by sing-box and avoid direct HTTP fallback.

The review cycle added an ownership boundary for probing. A temporary probe may start a runtime, but it must not stop or corrupt an already active user tunnel. Similarly, a latency refresh that cannot acquire an isolated probe runtime because sing-box is already connected must not persist failure latency across all profiles. Production `awaitReady()` must not depend on an external endpoint: endpoint DNS, filtering, TLS, EOF, or timeout cannot tear down a runtime that already started and accepts SOCKS5 handshakes. Routed probes remain post-connection health and latency diagnostics.

## Почему такое решение, даже если кажется странным:
Routed HTTP proves more than a local SOCKS handshake, but it also introduces state outside the application: the endpoint and its network path. Treating that state as a startup invariant made a third-party endpoint failure indistinguishable from an engine failure and stopped healthy local runtime state. The application now reports connection after its own runtime contract is met, while routed health evidence remains diagnostic.

## Related Concepts
- [[concepts/singbox-probe-socks-port-lifecycle]]
- [[concepts/singbox-exit-ip-probe-chain-socks]]
- [[concepts/singbox-active-socks-port-failure-reset]]
- [[connections/exit-node-probe-resource-state-coupling]]

## Sources
- [[daily/2026-05-31]]: sessions 11:39, 12:27, 16:19, 17:08, and 18:04 describe TCP-open false positives, routed probe adoption, runtime-busy latency risk, and readiness hardening.
