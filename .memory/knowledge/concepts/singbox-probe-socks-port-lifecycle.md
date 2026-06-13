---
title: sing-box probe SOCKS port is runtime state
sources:
  - daily/2026-05-29.md
created: 2026-05-31
updated: 2026-05-31
---

## Key Points
- sing-box IP probe SOCKS port must represent a successfully started runtime, not a pending start.
- The probe port must be cleared on failed-start paths to avoid stale exit-node probes.
- Dedicated SOCKS probe inbound should not alter the main TUN route.
- This lifecycle state is part of the exit-node safety contract.

## Details
The 2026-05-29 review found a risk in sing-box runtime state: `activeSocksPort` could be set before a successful start and remain stale on failure paths. That creates a false route for exit-node probing, because later UI checks may believe a valid local SOCKS probe path exists when the engine is not actually running.

This finding complements the broader exit-node strategy work. The SOCKS inbound is the correct way to probe a proxy-chain exit IP, but only if its port is tied to a confirmed active runtime and cleared on failure. Otherwise the probe layer can report stale or misleading state.

## Related Concepts
- [[concepts/singbox-active-socks-port-failure-reset]]
- [[concepts/singbox-exit-ip-probe-chain-socks]]
- [[concepts/exit-node-strategy-ui-unification]]
- [[connections/exit-node-probe-resource-state-coupling]]

## Sources
- [[daily/2026-05-29]] records the review finding that `activeSocksPort` was set before successful sing-box start and not reset on all failures.
- [[daily/2026-05-29]] records the decision to use a dedicated local SOCKS inbound for sing-box exit IP probing.
- [[daily/2026-05-29]] records the rule that failed SOCKS probing must not fall back to direct HTTP.
