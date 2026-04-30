---
title: "v0.0.1 DPI Bypass Fix Chain"
aliases: [v001-fixes, dpi-bypass-fix-chain, retag-cycle]
tags: [vpn, byedpi, debugging, native, fix-chain]
sources:
  - "daily/2026-04-30.md"
created: 2026-04-30
updated: 2026-04-30
---

# v0.0.1 DPI Bypass Fix Chain

The v0.0.1 release required 4 retag cycles (retag-1 through retag-6) to achieve working DPI bypass. Nine distinct fixes were applied, each addressing a different root cause. The fixes span VpnService.Builder configuration, native library interaction, DNS setup, and ByeDPI argument parsing. Each symptom mapped to a single root cause, but the combination of issues masked individual diagnosis.

## Key Points

- 9 fixes across 4 retag cycles were needed to go from "tunnel up but no traffic" to "working DPI bypass"
- Three distinct symptom clusters: traffic-zero (`setBlocking`), reconnect deadlock (stop order), burst/drop pattern (MTU + IPv6 + setMetered combo)
- Most fixes achieved parity with ByeByeDPI/ByeDPIAndroid reference implementation — deviations from upstream were the primary bug source
- Fix #5 (args `-Ku` → working preset) was the DPI bypass root cause — all other fixes addressed transport-level issues
- The retag approach (tag, test on device, find issue, fix, retag) was the practical methodology for native VPN debugging where emulators are insufficient

## Details

### Fix Timeline

The fixes were applied in chronological order across a single day (2026-04-30):

**Phase A (morning, retag-1 through retag-4):**

1. **Remove `setBlocking(true)`** — libhev uses `epoll`-based async I/O internally. Setting the TUN file descriptor to blocking mode froze the event loop entirely. Symptom: traffic counter stuck at 0 bytes despite tunnel being "up."

2. **Invert stop order: byedpi → libhev** — Previously libhev was stopped first, but its event loop waits for outstanding SOCKS5 connections to byedpi. Stopping libhev first left abandoned native threads holding the singleton mutex, causing deadlock on reconnect.

3. **Replace DNS 100.64.0.1 with public DNS** — 100.64.0.0/10 is CGNAT reserved space. No DNS server listens there on the device. All DNS queries timed out silently.

4. **Remove `fd.dup()`** — Passing duplicated file descriptor to native code diverged from ByeByeDPI behavior. Raw `tunPfd.fd` is correct; dup creates a second fd that outlives the original, causing lifecycle issues.

**Phase D1 (afternoon):**

5. **Replace default args** — The initial default args contained `-Ku` (UDP-only protocol whitelist), which disabled all TCP/TLS desync operations. This was the actual DPI bypass failure — the tunnel transported traffic correctly, but ByeDPI applied no evasion to TLS handshakes.

**Retag-5 (evening):**

6. **Remove `setMtu(8500)`** — The 8500-byte MTU on the TUN interface caused applications to send oversized packets that fragmented on the cellular path (MTU ~1500), producing retransmit storms.

7. **Remove IPv6 routes** — ByeDPI does not support IPv6 outbound. Adding v6 routes to the TUN caused dual-stack applications to attempt v6 first, timeout, then fall back to v4 with added latency.

8. **Add `setMetered(false)`** — Nubia/RedMagic ROM aggressively throttles metered VPN connections. Marking the VPN as unmetered prevents OS-level background throttling.

9. **Prepend `argv[0]="ciadpi"` + explicit `--ip`** — `getopt_long` treats `argv[0]` as program name and parses from `argv[1]`. Without the prefix, the first real flag was consumed as the program name.

### Root Cause Mapping

| Symptom | Root Cause | Fix # |
|---------|-----------|-------|
| Traffic counter = 0 | `setBlocking(true)` froze epoll loop | 1 |
| Reconnect deadlock | Stop order (libhev before byedpi) | 2 |
| DNS timeout | CGNAT address as DNS server | 3 |
| fd lifecycle issues | `fd.dup()` creating extra descriptor | 4 |
| HTTPS hangs (DPI not bypassed) | `-Ku` disabling TCP desync | 5 |
| Burst/drop download pattern | 8500-byte TUN MTU on cellular | 6 |
| Per-request latency spike | IPv6 silent drop + v4 fallback | 7 |
| Unstable throughput on Nubia | Metered VPN throttle | 8 |
| Partial arg loss | Missing `argv[0]` program name | 9 |

### Key Lesson

Every fix except #5 was a transport-layer configuration error — the tunnel worked as a pipe but had flow control, lifecycle, or routing issues. Fix #5 was the only one addressing the actual product function (DPI bypass). This distinction matters for future debugging: "tunnel up + bytes flowing" does not mean "DPI bypass active."

## Related Concepts

- [[concepts/byedpi-args-parsing]] - Fixes #5 and #9 relate to ByeDPI argument parsing traps
- [[concepts/tun-mtu-dual-layer]] - Fix #6 stems from misunderstanding the MTU dual-layer architecture
- [[concepts/nubia-rom-permission-enforcement]] - Fix #8 addresses Nubia-specific throttling
- [[concepts/libhev-tunnel-stats]] - Stats counters validated traffic-zero diagnosis in fix #1

## Sources

- [[daily/2026-04-30.md]] - Complete 9-fix chain documented across 4 retag cycles on 2026-04-30; each fix mapped to specific root cause and symptom
