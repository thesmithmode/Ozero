---
title: "Auto-Mode Traffic-Fail Blind Spot After Engine Start"
aliases: [auto-mode-blindspot, traffic-fail-undetected, auto-mode-stuck-dead-engine]
tags: [vpn, auto-mode, architecture, warp, gotcha]
sources:
  - "daily/2026-05-15 (1).md"
created: 2026-05-15
updated: 2026-05-15
---

# Auto-Mode Traffic-Fail Blind Spot After Engine Start

Auto-mode in Ozero detects engine failure only at startup (preflight checks). Once an engine reports a successful start, auto-mode considers it healthy and does not monitor traffic flow. If the engine later enters a "connected but dead" state — where the VPN is established but no traffic passes — auto-mode never switches to the next engine candidate.

## Key Points

- Auto-mode failure detection is one-shot at startup: if `start()` returns success and preflight passes, the engine is marked healthy
- Post-start traffic failures (e.g., WARP AWG connected but app traffic not routing) are invisible to auto-mode
- WARP without `excludeSelf=true` produces exactly this failure: AWG tunnel is up, handshake succeeds, but app traffic enters TUN → AWG can't handle Ozero's own routed packets → internet dead
- Engine reports "connected" (UAPI handshake seen), auto-mode never fires recovery, user sees VPN icon but no connectivity
- Fix requires a post-start liveness probe: e.g., HTTP probe after tunnel up, with timeout → trigger auto-mode switch on failure

## Details

### The Failure Mode

Auto-mode iterates through `engineAutoPriority` candidates, attempting `start()` on each until one succeeds. "Succeeds" means:

1. `start()` completes without throwing
2. Optional preflight checks (DNS resolution, IP probe) pass within the configured timeout

If both conditions are met, auto-mode commits to the engine and stops trying alternatives. The assumption is that a successfully started engine with passing preflights will deliver traffic.

This assumption breaks for WARP in the `excludeSelf=false` regression: the AWG handshake completes (UAPI shows peer with latest-handshake non-zero), the IP probe may have passed before the TUN was established, and `start()` returns successfully. The traffic failure only materializes once the TUN interface captures Ozero's own packets and routes them through AWG — which cannot process them correctly in the cross-process architecture.

### Why Standard Preflight Cannot Detect This

Standard preflight (DNS + HTTP probe through the tunnel) runs before the TUN is fully established for all app traffic. The probe uses a protected socket or runs before `excludeSelf` takes effect, so it may route correctly even when normal app traffic will loop. The dead-traffic state is only observable by monitoring sustained throughput after the engine is "live."

### Known Gap vs Accepted Risk

As of 2026-05-15, this is documented as a known architectural gap (not a P0 bug). The root cause of the specific WARP instance (excludeSelf regression) was fixed separately. A general liveness monitor that samples traffic bytes post-start and triggers auto-mode rotation on zero-throughput detection remains a potential future improvement.

## Related Concepts

- [[concepts/tun-self-exclusion-sdk-engines]] — the excludeSelf regression that exposed this blind spot; WARP without self-exclusion produces the "connected but dead" state
- [[concepts/warp-false-connected-no-handshake]] — adjacent failure mode: WARP appears connected before handshake completes; this article covers the inverse (handshake completes but traffic dead)
- [[concepts/vpn-engine-pipeline]] — auto-mode engine priority list and the rotation logic that stops on first apparent success
- [[concepts/engine-await-ready-pattern]] — startup synchronization: engines report ready before traffic is fully flowing, contributing to this blind spot

## Sources

- [[daily/2026-05-15 (1).md]] - Session 12:27: "Auto-mode не умеет детектить traffic-fail после старта (только preflight-fail) → WARP 'запускается' но мёртв → auto-mode не переключается на следующий движок"; documented as known gap after excludeSelf regression analysis
