---
title: "ByeDPI ensureUdpDesync — UDP Coverage Gap in Winning Strategy Args"
aliases: [ensure-udp-desync, byedpi-udp-coverage, auto-strategy-udp-gap]
tags: [byedpi, strategy, udp, gotcha]
sources:
  - "daily/2026-05-20.md"
  - "daily/2026-05-21.md"
created: 2026-05-20
updated: 2026-05-21
---

# ByeDPI ensureUdpDesync — UDP Coverage Gap in Winning Strategy Args

Auto-strategy testing (both fixed-list and genetic evolution) probes target sites over HTTPS (TCP/TLS). Winning strategies are validated exclusively for TCP DPI bypass. When these winning args are applied to production traffic, UDP/QUIC connections pass without any desync — `getopt_long` sees no `-K` flag with `u` → default protocol whitelist is all, but desync techniques targeting QUIC headers are absent. `ByeDpiEngine.ensureUdpDesync` closes the gap by appending `-Ku -a1 -An` if the winning args string contains no UDP desync selector.

## Key Points

- Auto-strategy and evolution probes use HTTPS (TCP) only — winning strategy never sees UDP/QUIC traffic
- Without `-Ku -a1 -An`, ByeDPI applies no desync to UDP/QUIC streams → QUIC handshake not modified → ISP DPI blocks it
- `ensureUdpDesync(args: String): String` checks for `-K` containing `u`; if absent, appends ` -Ku -a1 -An`
- **Default args path and UI mode args already include UDP desync** — these are not modified by `ensureUdpDesync`
- `ensureUdpDesync` is a side-fix at the apply-strategy boundary, not a re-test: if the winning strategy is genuinely incompatible with UDP, this may cause suboptimal behavior
- This is **NOT the root cause of YouTube failure** (root = `setUnderlyingNetworks(null)` in `TunBuilderHelper`, see [[concepts/byedpi-vpn-pipeline-upstream-divergence]]); ensureUdpDesync commit `7da7e852` improves UDP coverage independently

## Details

### Problem: HTTPS-only Strategy Testing

`ByeDpiAutoStrategyTester` and `EvolutionEngine` probe 1–3 target HTTPS URLs over SOCKS5 to score each strategy. The SOCKS5 probe is TCP — it opens a socket, performs a TLS handshake, and measures success and latency. No UDP probe exists.

The consequence: a strategy like `-s1 -q1 -Ar` that fragments TCP streams works fine for Instagram and other TLS-over-TCP but has no effect on QUIC because it specifies no UDP desync flags. When `StrategyTestViewModel` saves the winning args to `SavedStrategyStore` and applies them to the running engine, QUIC traffic (YouTube) flows through ByeDPI's SOCKS5 UDP_ASSOCIATE without any packet modification.

### Fix: ensureUdpDesync

```kotlin
fun ensureUdpDesync(args: String): String {
    // -K flag controls protocol whitelist. 'u' in the value means UDP is included.
    // If winning args have no UDP desync, append the standard UDP desync suffix.
    val hasUdpFlag = Regex("""-K[^\s]*u""").containsMatchIn(args)
    return if (hasUdpFlag) args else "$args -Ku -a1 -An"
}
```

The appended suffix `-Ku -a1 -An` is the standard ByeDPI UDP desync chain: `-Ku` selects UDP protocol, `-a1` starts a strategy group, `-An` sets trigger to "none" (always apply). This mirrors the default args baseline used when no winning strategy is present.

### Scope

`ensureUdpDesync` is called when applying a winning strategy from `SavedStrategyStore` to the live engine. It does NOT:
- Modify the strategy args stored in `SavedStrategyStore` (bookmark is preserved verbatim)
- Affect the auto-strategy test or evolution run (probes remain TCP-only)
- Change behavior for manual/custom args set by the user in UI (those go through verbatim)

Default args (no saved strategy) and UI mode args are constructed with UDP desync included from the start — they are not routed through `ensureUdpDesync`.

### Relation to YouTube Root Cause

Session 20:00 found this gap as a side-defect while investigating why YouTube failed with identical args that work in upstream ByeByeDPI. The user confirmed this is NOT the primary root: "те же args работают в оригинале, не работают у нас → корень в pipeline (hev routing/init order/DNS/split tunnel), не в args/strategies". The real root (`setUnderlyingNetworks(null)`) was confirmed in session 20:30. `ensureUdpDesync` is an independent correctness improvement.

## Related Concepts

- [[concepts/byedpi-vpn-pipeline-upstream-divergence]] — root cause of YouTube QUIC failure: `setUnderlyingNetworks(null)` per-engine fix
- [[concepts/genetic-strategy-evolution]] — evolution probes HTTPS only; winning chromosome fitness doesn't account for UDP
- [[concepts/byedpi-auto-strategy-testing]] — fixed-list test also TCP-only; same coverage gap
- [[concepts/byedpi-args-parsing]] — `-Ku` flag semantics: UDP-only whitelist for desync operations

## CMD Path Removal (2026-05-21)

Task16 (daily/2026-05-21.md) found that `ensureUdpDesync` was being applied to CMD mode winning args — mutating user-supplied strategy strings. A CMD strategy like `-s1 -q1 -a1 -Y -At -a1 -S -f-1 -r1+s -a1 -As -d1+s -O1 -s29+s -a1` (no `-Ku`) received the suffix `-Ku -a1 -An`, which added a duplicate `-a` group, overrode `-As` with `-An`, and broke byedpi topology → YouTube failure.

**Resolution**: CMD mode now passes `settings.byedpiWinningArgs!!.trim()` verbatim to the engine. `ensureUdpDesync` and `UDP_DESYNC_SUFFIX` were removed entirely from the CMD path. If the evolution engine needs to guarantee UDP coverage in winning strategies, the fix belongs in `AutoStrategyPicker` (where args are generated), not at the apply boundary.

UI mode args are constructed with `-Ku -a1 -An` from the start and were not affected.

**Sentinel**: `ByeDpiBuildManualConfigTest` — CMD mode winningArgs transmitted verbatim; no auto-suffix added even without `-Ku`.

## Sources

- [[daily/2026-05-20.md]] — Session 20:00: auto-strategy testing confirmed HTTPS-only probe gap; side-defect `ensureUdpDesync` implemented (commit 7da7e852); user confirmed this is not YouTube root cause
- [[daily/2026-05-21.md]] — Task16: `ensureUdpDesync` found mutating CMD args → YouTube fail; removed from CMD path; CMD verbatim contract restored
