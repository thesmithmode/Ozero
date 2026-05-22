---
title: "WARP Split Tunnel: allowFamily(AF_INET) Without AF_INET6 Breaks IPv6-Only Services"
aliases: [warp-split-tunnel-allowfamily, allowfamily-ipv6-blocklist, split-tunnel-gemini-bug]
tags: [warp, split-tunnel, vpn, android, gotcha, investigation]
sources:
  - "daily/2026-05-22.md"
created: 2026-05-22
updated: 2026-05-22
---

# WARP Split Tunnel: allowFamily(AF_INET) Without AF_INET6 Breaks IPv6-Only Services

When split tunnel is enabled with at least one excluded application, IPv6-dependent services (e.g., Gemini) stop working through the WARP engine. Hypothesis: `applyEngineTunSpec` calls `allowFamily(AF_INET)` in BLOCKLIST mode without a corresponding `allowFamily(AF_INET6)`, causing Android to block all IPv6 traffic at the VPN routing layer.

> **Status (2026-05-22):** Bug reproduced by user; root cause hypothesis established; fix not yet applied — awaiting `ozero.log` confirmation before patching.

## Key Points

- **Reproduction**: split tunnel OFF → Gemini works; split tunnel ON (even with one excluded app, e.g., Telegram) → Gemini fails
- **Scope**: WARP engine only (BLOCKLIST mode has a different code path from ALL-apps mode)
- **Hypothesis**: `VpnService.Builder.allowFamily(AF_INET)` without `allowFamily(AF_INET6)` → Android silently drops IPv6 traffic not from an allowed family, preventing Gemini (which prefers IPv6) from connecting
- **Proposed fix**: remove both `allowFamily` calls from `applyEngineTunSpec` — VPN routes (`addRoute`) already control which traffic enters the tunnel; `allowFamily` is redundant and creates implicit IPv6 blocking
- **Log path for diagnosis**: `C:\Users\thesm\Downloads\ozero.log`

## Details

### Reproduction Conditions

The bug was identified by the user in a binary split-tunnel experiment:
1. Split tunnel disabled (all apps through VPN) → Gemini functions normally
2. Split tunnel enabled with Telegram excluded → Gemini fails, even though Gemini is not in the excluded list

This rules out Gemini being accidentally excluded from the tunnel. The failure is caused by a side-effect of entering BLOCKLIST mode itself.

### BLOCKLIST vs ALL-Apps TUN Building

In BLOCKLIST mode (`addDisallowedApplication` per excluded app), `VpnService.Builder` behaves differently from the ALL-apps path:

- ALL mode: single `addDisallowedApplication` call; `allowFamily` may not be called
- BLOCKLIST mode: multiple `addDisallowedApplication` calls; `allowFamily(AF_INET)` may be called to explicitly admit IPv4 traffic

Android's `Builder.allowFamily(int)` semantics: when called, it restricts VPN traffic to **only** the specified address family unless the counterpart is also called. Calling `allowFamily(AF_INET)` without `allowFamily(AF_INET6)` means IPv6 packets from non-excluded apps are implicitly blocked at the VPN layer — they are not routed through the tunnel and are also not forwarded to the underlying network.

### Affected Service Pattern

Services that prefer IPv6 (Google's infrastructure, including Gemini, YouTube, etc.) will attempt IPv6 first. Under the bug condition:
- IPv6 attempt → silently dropped by `allowFamily` restriction
- Browser/client may retry via IPv4 → succeeds, but with latency penalty
- Some services (Gemini's GRPC streaming) may not fall back to IPv4, causing complete failure

### Proposed Fix

Remove `allowFamily(AF_INET)` (and any `allowFamily(AF_INET6)`) calls from `applyEngineTunSpec`. Traffic routing through the WARP TUN is controlled by `addRoute("0.0.0.0", 0)` (IPv4 default route) and `addRoute("::", 0)` (IPv6 default route). Per-app exclusions via `addDisallowedApplication` do not require `allowFamily` to function correctly. The `allowFamily` calls are defensive coding that inadvertently creates the IPv6 block.

### Reference: TunBuilderConfigurator

The relevant class is `TunBuilderConfigurator` / `applyEngineTunSpec` in `common-vpn`. The split-tunnel BLOCKLIST path is separate from the full-tunnel path that handles WARP's selective `AllowedIPs` routing (see [[concepts/warp-allowedips-tun-routing]] — that issue is about AWG config routes, not `allowFamily`).

## Related Concepts

- [[concepts/vpnservice-builder-traps]] — `VpnService.Builder` API traps; `allowFamily` is documented as a gotcha here
- [[concepts/tun-self-exclusion-sdk-engines]] — `excludeSelf=true` and `addDisallowedApplication` semantics
- [[concepts/warp-allowedips-tun-routing]] — separate WARP routing issue (AWG AllowedIPs vs TUN addRoute)
- [[concepts/byedpi-vpn-pipeline-upstream-divergence]] — `applyUnderlying` flag and `setUnderlyingNetworks(null)` as related per-engine TUN config divergence

## Sources

- [[daily/2026-05-22.md]] — Session 22:09: user binary reproduction (split tunnel OFF=Gemini OK, split tunnel ON=Gemini fails); `allowFamily(AF_INET)` without `allowFamily(AF_INET6)` in BLOCKLIST mode hypothesis; proposed fix = remove allowFamily calls; awaiting ozero.log for confirmation before applying
