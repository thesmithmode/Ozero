---
title: "WARP Split Tunnel: allowFamily(AF_INET) Without AF_INET6 Breaks IPv6-Only Services"
aliases: [warp-split-tunnel-allowfamily, allowfamily-ipv6-blocklist, split-tunnel-gemini-bug]
tags: [warp, split-tunnel, vpn, android, gotcha, fixed]
sources:
  - "daily/2026-05-22.md"
created: 2026-05-22
updated: 2026-05-22
---

# WARP Split Tunnel: allowFamily(AF_INET) Without AF_INET6 Breaks IPv6-Only Services

When split tunnel is enabled with at least one excluded application, IPv6-dependent services (e.g., Gemini) stop working through the WARP engine. Root cause: `applyEngineTunSpec` called `allowFamily(AF_INET)` conditionally on `spec.allowFamilyV4` without a corresponding `allowFamily(AF_INET6)` when `spec.allowFamilyV6=false` (default for WARP with `ipv6Enabled=false`), causing Android to set `allowIPv6=false`. In BLOCKLIST mode, non-excluded apps are routed via VPN; their IPv6 packets are blocked at the VPN layer (`allowIPv6=false`) and not allowed to bypass — packets die silently.

> **Status (2026-05-22 23:50):** FIXED. `TunBuilderHelper.applyEngineTunSpec` теперь безусловно вызывает `allowFamily(AF_INET)` + `allowFamily(AF_INET6)`. Sentinel-тест `applyEngineTunSpec allowFamily AF_INET6 безусловный` зафиксирован в `TunBuilderHelperContractTest`. Released в v0.1.15.

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

### Applied Fix

Call both `allowFamily(AF_INET)` AND `allowFamily(AF_INET6)` unconditionally in `applyEngineTunSpec`. This sets `allowIPv4=true` and `allowIPv6=true` at the VPN configuration. The actual IPv6 routing inside the tunnel still gates on `spec.allowFamilyV6 && v6 != null` (line 63), so:
- `ipv6Enabled=false` (default): VPN admits IPv6 packets at the family layer, no IPv6 address/route added → packets fall through to system default network (= underlying network bypass, like PORTAL_WG behavior)
- `ipv6Enabled=true`: VPN admits IPv6 and adds IPv6 address + `addRoute("::", 0)` → traffic enters the tunnel

The earlier hypothesis "drop both allowFamily calls" was incorrect — `addAddress(v4)` already sets `allowIPv4=true`, so dropping both leaves `allowIPv6=false` and the bug persists. The fix must positively assert IPv6 family admission.

Why this is architecturally correct: the intent "WARP tunnels IPv4, IPv6 bypasses" is now explicitly expressed via Android's VpnService API. The previous code accidentally declared "VPN only accepts IPv4", which has a subtle side-effect in BLOCKLIST mode where VPN-routed apps' IPv6 dies at the VPN layer.

### Reference: TunBuilderConfigurator

The relevant class is `TunBuilderConfigurator` / `applyEngineTunSpec` in `common-vpn`. The split-tunnel BLOCKLIST path is separate from the full-tunnel path that handles WARP's selective `AllowedIPs` routing (see [[concepts/warp-allowedips-tun-routing]] — that issue is about AWG config routes, not `allowFamily`).

## Related Concepts

- [[concepts/vpnservice-builder-traps]] — `VpnService.Builder` API traps; `allowFamily` is documented as a gotcha here
- [[concepts/tun-self-exclusion-sdk-engines]] — `excludeSelf=true` and `addDisallowedApplication` semantics
- [[concepts/warp-allowedips-tun-routing]] — separate WARP routing issue (AWG AllowedIPs vs TUN addRoute)
- [[concepts/byedpi-vpn-pipeline-upstream-divergence]] — `applyUnderlying` flag and `setUnderlyingNetworks(null)` as related per-engine TUN config divergence

## Sources

- [[daily/2026-05-22.md]] — Session 22:09: user binary reproduction (split tunnel OFF=Gemini OK, split tunnel ON=Gemini fails); `allowFamily(AF_INET)` without `allowFamily(AF_INET6)` in BLOCKLIST mode hypothesis; proposed fix = remove allowFamily calls; awaiting ozero.log for confirmation before applying
