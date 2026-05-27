---
title: "ByeDPI UDP/QUIC Routing in hev YAML"
aliases: [hev-udp-tcp, quic-routing-regression, byedpi-quic]
tags: [byedpi, hev, quic, udp, youtube, pipeline]
sources:
  - "daily/2026-05-22.md"
created: 2026-05-22
updated: 2026-05-22
---

# ByeDPI UDP/QUIC Routing in hev YAML

The `udp:` field in hev's YAML config controls how UDP traffic is forwarded to the ByeDPI SOCKS5 proxy. Setting `udp: tcp` causes hev to route UDP (including QUIC) traffic as TCP connections to the SOCKS5 server — which breaks QUIC-based services since the server receives TCP connections carrying QUIC payload bytes it cannot parse. The correct value is `udp: udp`, which routes via SOCKS5 UDP ASSOCIATE. This was a regression introduced by the `udp: tcp` → `udp: udp` change that fixed YouTube.

## Key Points

- `udp: tcp` in hev YAML = hev maps UDP packets to TCP connections toward SOCKS5 — server cannot parse QUIC payload
- `udp: udp` = hev uses SOCKS5 UDP ASSOCIATE for UDP traffic — correct for QUIC
- Without `-Ku` in ByeDPI args: ByeDPI rejects UDP ASSOCIATE → QUIC fast-fail → browser forces TCP fallback → TCP desync works for YouTube
- With `udp: tcp`: QUIC bytes arrive at ByeDPI as TCP data → ByeDPI cannot process → connection drops silently
- `hevLogLevel` was present in config store but never written to hev YAML — added; default changed to `"info"` for diagnostics
- CMD mode YouTube was never working in Ozero — systemic pipeline issue, not an args regression

## Details

### The Failure Mechanism

QUIC (used by YouTube, HTTP/3) is a UDP-based protocol. When `udp: tcp` is set:

1. hev receives UDP datagram from TUN (QUIC packet)
2. hev opens a **TCP connection** to ByeDPI SOCKS5 port
3. hev sends the UDP payload as TCP data
4. ByeDPI SOCKS5 server receives a TCP connection it can't interpret as UDP ASSOCIATE
5. Connection fails or is silently dropped

With `udp: udp`:

1. hev receives UDP datagram (QUIC packet)
2. hev sends SOCKS5 UDP ASSOCIATE request to ByeDPI
3. If ByeDPI has `-Ku`: accepts UDP ASSOCIATE → QUIC works end-to-end
4. If ByeDPI lacks `-Ku`: ByeDPI **rejects** UDP ASSOCIATE → QUIC fast-fail → browser forces HTTP/2 (TCP) → TCP path works → TCP desync applies → YouTube works via TCP

### CMD Mode vs UI Mode

In UI mode (auto-generated args including `-Ku`): ByeDPI accepts UDP ASSOCIATE; QUIC traffic flows. In CMD mode (user-provided args, verbatim): if user omits `-Ku`, QUIC fails fast and YouTube switches to TCP. This is why YouTube works in CMD mode without explicit QUIC support — the failure is beneficial.

With the regression `udp: tcp`: both modes broke YouTube because QUIC was silently misrouted as TCP at the hev layer, before ByeDPI even received it.

### hevLogLevel Missing from YAML

`hevLogLevel` was stored in `ByeDpiConfig` datastore but the YAML generator did not include it. Added to YAML output; default changed from `"warn"` to `"info"` to enable diagnostic logging in the next APK release. Enables inspection of `[I] socks5 client ...` lines that show what happens to YouTube traffic at the hev layer.

### Root Cause Diagnosis Pattern

When YouTube stops working in ByeDPI CMD mode:
1. Check `udp:` field in generated hev YAML first (should be `udp: udp`)
2. Check hev log for `[I] socks5 client` lines — shows UDP ASSOCIATE success/failure
3. Check ByeDPI args for `-Ku` presence — its absence causes expected QUIC fast-fail
4. Only investigate args/strategies after ruling out pipeline (hev routing) issues

## Related Concepts

- [[concepts/byedpi-args-parsing]] - ByeDPI getopt_long arg parsing; `-Ku` enables UDP relay
- [[concepts/byedpi-vpn-pipeline-upstream-divergence]] - Systemic pipeline differences from upstream reference as primary bug source
- [[concepts/byedpi-hev-pipeline-upstream-parity]] - Maintaining hev config parity with upstream reference
- [[concepts/tun-mtu-dual-layer]] - hev YAML and TUN Builder have independent config layers

## Sources

- [[daily/2026-05-22.md]] - Session 13:00: `udp: tcp` regressed QUIC; revert to `udp: udp`; without -Ku QUIC fast-fail → YouTube falls to TCP desync; with `udp: tcp` QUIC bytes sent as TCP to server → server breaks; hevLogLevel missing from YAML → added; default changed to "info"; CMD mode YouTube = pipeline issue not args
