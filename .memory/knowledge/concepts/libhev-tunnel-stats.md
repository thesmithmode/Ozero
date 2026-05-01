---
title: "libhev Tunnel Stats and EWMA Smoothing"
aliases: [tunnel-stats, native-counters, ewma-speed, tproxy-stats]
tags: [native, libhev, ui, performance]
sources:
  - "daily/2026-04-30.md"
created: 2026-04-30
updated: 2026-04-30
---

# libhev Tunnel Stats and EWMA Smoothing

`hev_socks5_tunnel_get_stats()` provides cumulative traffic counters (tx_packets, tx_bytes, rx_packets, rx_bytes) from the native libhev tunnel. These counters auto-reset on each `TProxyStartService` call, making session-level byte tracking trivial. Raw per-second deltas exhibit TCP burst jitter, requiring EWMA smoothing for stable UI display.

## Key Points

- `TProxyGetStats()` returns a `LongArray` via JNI `RegisterNatives` — cheap call, safe at 1Hz polling
- Counters are **cumulative for the tunnel lifetime** and auto-reset on `TProxyStartService` — session bytes = raw native value, no virtual reset needed in Kotlin
- Raw `Δbytes / Δtime` between 1-second samples shows TCP burst jitter (large spikes followed by near-zero)
- EWMA with α=0.4 smooths jitter: `smoothed = 0.4 * rawDelta + 0.6 * prevSmoothed`
- Computation belongs in `TunnelController.updateStats`, not in Compose recomposition — one `Double` field, cheaper than a moving average buffer

## Details

### Native Counter Behavior

The native function `hev_socks5_tunnel_get_stats` writes four cumulative values: transmitted packets, transmitted bytes, received packets, received bytes. These accumulate from the moment `TProxyStartService` is called until `TProxyStopService`. On the next `TProxyStartService`, all counters reset to zero automatically in the native code.

This auto-reset behavior means session tracking requires no state management in Kotlin. The raw native value directly represents "bytes transferred in this VPN session." There is no need for offset tracking, snapshot-on-start, or virtual reset logic. This was confirmed during v0.0.1 development and simplifies the stats implementation considerably.

### EWMA Smoothing for UI

TCP transfers are inherently bursty: the congestion window opens, a burst of segments arrives, then the sender waits for ACKs. At 1Hz sampling, this produces wildly fluctuating speed readings (e.g., 5MB/s → 100KB/s → 8MB/s). Displaying raw deltas to the user creates an impression of instability even when overall throughput is consistent.

Exponentially Weighted Moving Average (EWMA) with α=0.4 provides a good balance between responsiveness and stability. The formula `smoothed = α * rawDelta + (1-α) * prevSmoothed` requires storing a single `Double` value — dramatically simpler and cheaper than maintaining a circular buffer for a windowed moving average.

The smoothing computation runs in `TunnelController.updateStats` rather than during Compose recomposition. This ensures the calculation happens once per sample regardless of how many UI elements observe the value, and avoids polluting the render path with arithmetic.

## Related Concepts

- [[concepts/v001-dpi-bypass-fix-chain]] - Stats counters were used to diagnose the traffic-zero symptom (fix #1: `setBlocking` froze epoll → counter stuck at 0)
- [[concepts/tun-mtu-dual-layer]] - The burst/drop pattern visible in stats was caused by MTU misconfiguration

## Sources

- [[daily/2026-04-30.md]] - Native counter auto-reset behavior confirmed; EWMA α=0.4 chosen for session speed card; 1Hz TProxyGetStats polling validated as safe
