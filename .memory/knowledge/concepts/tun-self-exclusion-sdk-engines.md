---
title: "TUN Self-Exclusion Required for All VPN Engines"
aliases: [excludeSelf, tun-self-exclusion, sdk-routing-loop, socks-routing-loop]
tags: [android, vpn, urnetwork, byedpi, gotcha, native]
sources:
  - "daily/2026-05-09.md"
  - "daily/2026-05-10.md"
created: 2026-05-09
updated: 2026-05-10
---

# TUN Self-Exclusion Required for All VPN Engines

Setting `excludeSelf = false` in `establishTunForEngine` kills all engine types via distinct routing loop mechanisms. For Go SDK engines (URnetwork): the SDK's network sockets are captured by the TUN and looped back into itself. For SOCKS-based engines (ByeDPI via hev-socks5-tunnel): the VPN app's own traffic enters the TUN, gets routed through the SOCKS proxy (hev→ciadpi), which re-injects it into the VPN — an infinite loop. The parameter has exactly one correct value (`true`); it should be removed from the API and hardcoded unconditionally.

## Key Points

- Commit `65e5b13` in v0.0.7 introduced `excludeSelf = false` in `establishTunForEngine`, breaking URnetwork (routing loop via Go SDK sockets)
- v0.0.9 regression: `buildTunBuilder()` not passing `excludeSelf=true` for SOCKS path broke ByeDPI — traffic loop: Ozero's own packets enter TUN → routed through hev→ciadpi→VPN → back into TUN
- Go SDK engines: require self-exclusion because Go runtime has no `protect()` callback; all SDK sockets enter TUN
- SOCKS engines (ByeDPI via hev): require self-exclusion because hev captures ALL app traffic including its own SOCKS relay traffic
- WARP/AWG: `protect()` on WireGuard socket already handles this; self-exclusion still correct for defense-in-depth
- Sentinel test enforces `excludeSelf = true`; remove parameter from `TunBuilderConfigurator.apply` API entirely

## Details

### The Routing Loop Mechanism

Android VPN services intercept all device traffic by establishing a TUN interface. When the VPN app's own package is NOT excluded from the TUN (via `addDisallowedApplication`), the app's outbound network traffic — including the Go SDK's peer discovery, relay connections, and control plane — enters the TUN interface. The SDK receives its own packets back as incoming tunnel traffic, creating an infinite routing loop.

For engines that use `VpnService.protect(socket)` on their own sockets (WARP/AWG), self-exclusion is optional because protected sockets bypass the TUN. But Go SDK engines (URnetwork) rely on the Go runtime's network stack, which does not call `protect()` on its sockets. The only way to give the Go SDK direct internet access is to exclude the app package from the TUN via `addDisallowedApplication(context.packageName)`.

### The SOCKS Engine Loop (ByeDPI via hev)

ByeDPI uses hev-socks5-tunnel as the TUN layer, which captures all device traffic and forwards it through ByeDPI's SOCKS5 proxy. When the VPN app itself is not excluded from the TUN, hev captures the Ozero app's own outbound traffic. That traffic enters the hev→ciadpi pipeline, which processes it as SOCKS5 requests and re-injects it through the TUN — which hev captures again. The result is an infinite routing loop consuming CPU and producing no actual connectivity.

The v0.0.9 regression exposed this: `buildTunBuilder()` added an `excludeSelf` parameter to allow optional configuration. When called for the SOCKS path (ByeDPI) with the parameter's default value (`false`), the exclusion was not applied. Fixing this requires not just reverting the specific call site but ensuring `excludeSelf=true` is passed unconditionally for ALL engine types through all paths in `buildTunBuilder()`.

### Discovery Context

The regression was introduced in commit `65e5b13` during the v0.0.7 development cycle. The change was part of a broader refactoring of `establishTunForEngine` that added the `excludeSelf` parameter. The developer set it to `false` — likely intending to route all traffic through the tunnel for completeness — without realizing that URnetwork's Go SDK requires self-exclusion to function.

The bug was discovered by the user reporting "URnetwork не работает после обновления." A diff between v0.0.6 and v0.0.7 releases identified the `excludeSelf = false` change as the sole regression candidate. Reverting to `true` immediately restored URnetwork connectivity.

### API Design Lesson

The `excludeSelf` parameter in `TunBuilderConfigurator.apply` is a footgun: it exposes a configuration that has exactly one correct value (`true`) for all current and foreseeable engines. Parameters with no valid alternative value should not exist in the API — they invite accidental misconfiguration. The recommended fix is to remove the parameter entirely and hardcode `addDisallowedApplication(context.packageName)` unconditionally.

The sentinel test enforces this invariant at the test level, but removing the parameter from the API is the structural fix that prevents the class of bug entirely.

## Related Concepts

- [[concepts/urnetwork-sdk-integration]] - URnetwork is the engine that broke; Go SDK socket protection model requires self-exclusion
- [[concepts/vpnservice-builder-traps]] - `addDisallowedApplication` is another Builder API with non-obvious routing side-effects
- [[concepts/android-vpn-self-traffic-bypass]] - Related: self-exclusion is intentional here (engine needs it), but IP checker self-bypass is unintentional
- [[concepts/dual-go-runtime-eager-loading]] - Go runtime lifecycle management; both articles address Go SDK integration challenges

## Sources

- [[daily/2026-05-09.md]] - Session 09:37: diff v0.0.6→v0.0.7 found `excludeSelf = false` (commit 65e5b13) as regression cause for URnetwork; fix = revert to `true`; sentinel test added; v0.0.7 tag recreated
- [[daily/2026-05-10.md]] - Session 17:46 GROUP A fix: `buildTunBuilder()` not passing `excludeSelf=true` for SOCKS path (ByeDPI) → traffic loop via hev→ciadpi→VPN; same root cause, different mechanism; confirms all engine types require exclusion
