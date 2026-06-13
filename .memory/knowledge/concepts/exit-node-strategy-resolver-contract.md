---
title: "Exit node strategy resolver contract"
sources:
  - "daily/2026-05-29.md"
created: 2026-05-29
updated: 2026-05-29
---
# Exit node strategy resolver contract
## Key Points
- Exit node display should be engine-owned: each engine declares how its exit node can be known.
- Direct HTTP fallback is unsafe when an engine requires routed probing; it can show the real device IP.
- Useful strategy types include routed SOCKS probe, location-only, provider label, and explicit unavailable/error state.
- `MainViewModel` should not encode per-engine exit-node policy directly.
## Details
The sing-box IP issue exposed a wider architecture problem: different engines need different exit-node strategies. A proxy-chain engine needs a probe through its outbound graph. URnetwork can honestly show country/flag/location-only data from SDK state when it does not expose a reliable public IP. WARP may be safer as a provider label until a proved route-specific probe exists.

The resolver should centralize fallback and presentation policy. If `Socks` probing fails for a proxy-based engine, the resolver should not silently call direct HTTP; that would display or leak the device's real IP. Instead it should return a state that the UI can render as unavailable, provider-only, or location-only depending on the engine's declared strategy.

`IpProbeRoute` already acts as a low-level route primitive, but the engine-level contract needs to be higher-level. `ExitNodeStrategy` can express intent, while `ExitNodeResolver` performs the probe and applies consistent no-leak behavior.
## Related Concepts
- [[concepts/singbox-exit-ip-probe-chain-socks]] - Concrete sing-box implementation using SOCKS probe.
- [[concepts/ip-probe-route-architecture]] - Existing lower-level route mechanism.
- [[concepts/vpn-ip-detection-contract]] - Earlier service/UI IP detection boundary.
- [[connections/release-regression-ci-vs-runtime-proof]] - Explains why runtime proof is required beyond green CI.
## Sources
- [[daily/2026-05-29]]: records the proposal to introduce `ExitNodeStrategy`/`ExitNodeResolver`.
- [[daily/2026-05-29]]: records the rule that direct fallback should not be used when routed engine probing is required.
- [[daily/2026-05-29]]: records engine-specific examples: sing-box SOCKS probe, URnetwork location-only, WARP provider label.
