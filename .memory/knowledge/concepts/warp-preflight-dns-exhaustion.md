---
title: "WARP Preflight DNS Exhaustion on engage.cloudflareclient.com"
aliases: [warp-dns-preflight, cloudflareclient-dns-fail, warp-20s-timeout]
tags: [warp, networking, dns, gotcha, debugging]
sources:
  - "daily/2026-05-12.md"
created: 2026-05-12
updated: 2026-05-12
---

# WARP Preflight DNS Exhaustion on engage.cloudflareclient.com

The WARP engine performs a DNS preflight check against `engage.cloudflareclient.com` before establishing the WireGuard tunnel. When DNS resolution fails (common on Russian ISPs with TSPU or DNS hijacking), each attempt burns a 5-second timeout. With 3 retries, the preflight phase alone consumes 20 seconds before the engine reports failure — during which the user sees "Connecting" with no feedback about the underlying DNS issue. No auto-recovery mechanism exists; the engine transitions to Failed without retrying alternative DNS resolvers.

## Key Points

- `engage.cloudflareclient.com` DNS resolution is a blocking preflight step before `awgTurnOn` — failure prevents tunnel establishment entirely
- 5-second timeout × 3 retries = 20 seconds of dead air before the engine reports failure
- Russian ISPs with TSPU may block or poison DNS for Cloudflare domains — the preflight fails even when the WARP endpoint itself is reachable via IP
- No auto-recovery: after 3 failed DNS attempts, the engine transitions to Failed state without trying alternative DNS (DoH, DoT, hardcoded IP)
- Diagnosed via 6-subagent parallel log analysis of ozero.log; WARP log lines showed sequential DNS timeouts for the same hostname

## Details

### The Preflight Mechanism

Before calling `GoBackend.awgTurnOn`, the WARP engine resolves the Cloudflare API hostname `engage.cloudflareclient.com` to obtain the WARP endpoint configuration or verify reachability. This DNS resolution uses the device's default DNS resolver — which on Russian ISPs may be a TSPU-filtered resolver that blocks or poisons Cloudflare-related domains.

Each DNS query has a 5-second timeout. The implementation retries 3 times before giving up, creating a total blocking window of up to 20 seconds. During this window:
- The UI shows "Connecting" state with no indication of DNS failure
- The user cannot distinguish between "connecting slowly" and "will never connect"
- No alternative DNS resolution path is attempted (e.g., DoH to 1.1.1.1, direct IP connection)

### Diagnostic Discovery

The failure was identified during a comprehensive 6-subagent diagnostic session that analyzed ozero.log across all three engine types (WARP, URnetwork, ByeDPI). The WARP-specific agent found sequential log entries showing DNS resolution attempts for `engage.cloudflareclient.com` with 5-second gaps between each attempt, followed by an engine failure state transition.

### Impact on User Experience

The 20-second timeout creates a particularly poor UX because:
1. The user expects WARP to connect within seconds (typical WireGuard handshake is <2s)
2. No progress indicator distinguishes "DNS resolving" from "tunnel establishing"
3. After the 20-second timeout, the engine simply fails — the user must manually retry
4. On ISPs where the DNS is permanently poisoned for Cloudflare, every retry repeats the same 20-second wait

### Fix Directions

Three approaches in order of impact:

1. **Hardcoded WARP endpoint IPs as DNS fallback**: If DNS fails, connect directly to known Cloudflare WARP IPs (162.159.192.1, 162.159.193.1). This eliminates DNS dependency entirely for WARP connections.

2. **DoH/DoT DNS resolution**: Use DNS-over-HTTPS (https://1.1.1.1/dns-query) or DNS-over-TLS as an alternative resolver when the default system DNS fails. This bypasses TSPU DNS filtering while still resolving the hostname.

3. **Reduced timeout + parallel resolution**: Reduce per-attempt timeout from 5s to 2s and run system DNS + DoH in parallel, using whichever resolves first. Total worst-case drops from 20s to 6s.

## Related Concepts

- [[concepts/warp-config-generator-api]] - The WARP mirror API that provides configs; DNS failure in preflight occurs before mirror config is even used
- [[concepts/warp-false-connected-no-handshake]] - Complementary failure: preflight DNS exhaustion prevents connection entirely, while false-connected is a post-connection issue
- [[concepts/warp-awg-obfuscation-russian-isps]] - TSPU context: the same ISP filtering that blocks vanilla WG may also filter DNS for Cloudflare domains
- [[concepts/nubia-rom-permission-enforcement]] - Device-specific behaviors may compound DNS resolution issues

## Sources

- [[daily/2026-05-12.md]] - Session 21:19: 6-subagent parallel log analysis found WARP DNS-fail on engage.cloudflareclient.com, 5s timeout × 3 = 20s exhaustion, no auto-recovery
