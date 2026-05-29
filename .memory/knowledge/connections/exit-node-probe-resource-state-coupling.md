---
title: Exit-node probe resource state coupling
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---
# Exit-node probe resource state coupling

## Key Points
- Exit-node correctness depends on both routing policy and runtime resource state.
- A SOCKS-based probe is safe only while its local inbound actually belongs to the current successful engine runtime.
- Stale probe resources can reintroduce wrong-IP behavior even after direct fallback is removed.
- This connects [[concepts/singbox-active-socks-port-failure-reset]] and [[concepts/singbox-exit-ip-probe-chain-socks]].

## Details

The sing-box exit-node fix moved IP detection to a local SOCKS inbound so the request traverses the active outbound graph. That solves the direct-leak class of bugs only if the SOCKS resource lifecycle is also correct.

The later review identified `activeSocksPort` as a state-coupling risk: if the port remains set after a failed start, the resolver may choose a route that no longer corresponds to a live engine. Exit-node work therefore needs two sentinels: no direct fallback after proxy failure, and no stale local probe resource after failed startup.

## Related Concepts
- [[concepts/singbox-active-socks-port-failure-reset]]
- [[concepts/singbox-exit-ip-probe-chain-socks]]
- [[concepts/exit-node-strategy-no-direct-leak-sentinel]]
- [[connections/engine-exit-node-safe-routing-contract]]

## Sources
- [[daily/2026-05-29]] records the dedicated sing-box SOCKS inbound for exit IP probes.
- [[daily/2026-05-29]] records the later review risk that `activeSocksPort` can remain stale across failed startup paths.
