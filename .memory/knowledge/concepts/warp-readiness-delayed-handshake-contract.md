---
title: WARP Readiness Delayed Handshake Contract
sources:
  - daily/2026-05-31.md
created: 2026-05-31
updated: 2026-06-09
---
# WARP Readiness Delayed Handshake Contract

## Key Points
- WARP startup must not report `Connected` unless readiness is proven by real handshake or equivalent engine-owned signal.
- A delayed first handshake is not automatically a startup failure when TUN is attached and watchdog can continue observing peer state.
- A global custom-TUN timeout relaxation is unsafe because `TunFdAcceptor` also covers engines such as FPTN and sing-box.
- WARP needs more time or engine-specific readiness handling rather than a false-connected shortcut.
- Reference parity with PORTAL WG should be checked when Ozero leaks or fails where the same WARP config works in the reference app.

## Details

The v1.0.13 to v1.0.14 regression investigation on 2026-05-31 showed WARP logs with peers present, no traffic, and no handshake after about ten seconds. That was interpreted as delayed readiness, not proof that WARP should immediately fail before the runtime watchdog can observe the peer. At the same time, returning `Connected` on timeout was rejected because it would recreate a false-connected class of bugs.

The safer contract is engine-specific: WARP can allow more time for real network readiness, but it must still transition to `Connected` only after a verified readiness signal. The generic custom-TUN path cannot convert timeout into success because the same acceptor is used by other engines with different readiness semantics.

The same day later added a reference-parity path for WARP leak reports. If PORTAL WG succeeds with the same WARP config and Ozero leaks or fails, the first comparison should cover config extraction, `wgQuick` versus `amQuick` precedence, TUN routes, DNS, IPv6, application allow/disallow lists, underlying networks, and socket protection before changing readiness or routing behavior.

This relates directly to [[concepts/warp-false-connected-no-handshake]] and [[concepts/warp-uapi-handshake-polling]]. It also reinforces [[connections/startup-readiness-runtime-recovery-boundary]]: startup gates should be short enough not to hang forever, but runtime recovery must not be replaced by fake success.

## Related Concepts
- [[concepts/warp-false-connected-no-handshake]]
- [[concepts/warp-uapi-handshake-polling]]
- [[concepts/engine-poisoned-state-recovery-proof]]
- [[concepts/warp-proxy-config-wgquick-precedence]]
- [[connections/startup-readiness-runtime-recovery-boundary]]

## Sources
- [[daily/2026-05-31]]: Session 21:49 records the WARP delayed-handshake interpretation from v1.0.14 logs.
- [[daily/2026-05-31]]: Session 22:09 records the decision that `Connected` must require real readiness and that global custom-TUN timeout success is unsafe.
- [[daily/2026-05-31]]: Session 23:44 records the PORTAL WG comparison path after WARP resources worked in the reference app but not in Ozero.
