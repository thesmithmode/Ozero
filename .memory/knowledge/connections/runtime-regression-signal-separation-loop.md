---
title: Runtime regression signal separation loop
sources:
  - daily/2026-05-30.md
created: 2026-06-09
updated: 2026-06-09
---
# Runtime regression signal separation loop
## Summary
WARP, FPTN, sing-box, MasterDNS, and ByeDPI runtime symptoms must be separated by their concrete signal before fixes are batched, because bytes, crashes, auth timeouts, container failures, and video playback failures point to different owning layers.
## Key Points
- WARP traffic bytes do not prove that a blocked site failure is caused by code or by the exit node.
- FPTN HTTP 608 bursts after health-preselect point to startup/auth policy, not MasterDNS or sing-box.
- sing-box native crash plus exit-IP timeout indicates traffic-path or process lifecycle risk.
- MasterDNS `run_failed` after image build belongs to Docker/container execution evidence, not DNS UI state.
- ByeDPI page success with video failure points to QUIC/UDP/googlevideo coverage.
## Details
The 2026-05-30 runtime diagnosis handled multiple user-visible regressions at once. The important move was to avoid collapsing them into one shared root cause. WARP had bytes but ChatGPT still failed; FPTN showed repeated HTTP 608 authentication timeouts; sing-box crashed and later started without real egress; MasterDNS built an image but failed at `docker run`; ByeDPI loaded YouTube pages while video still failed.

Each signal mapped to a different owning layer and evidence type. FPTN needed comparison against the stable auth flow and candidate order. MasterDNS needed read-only Docker/container/port/log evidence. sing-box needed process lifecycle and runtime health proof. ByeDPI needed media-domain and UDP/QUIC acceptance coverage. WARP needed safe exit-node proof rather than direct IP guesses.

The connection is a triage loop: split symptoms by evidence, assign the owning layer, integrate fixes independently, then validate through fresh CI and runtime-oriented sentinels. Mixing these signals risks fixing the easiest visible symptom while leaving the actual engine contract broken.
## Related Concepts
- [[concepts/fptn-health-preselect-auth-timeout-regression]]
- [[concepts/masterdns-docker-build-run-proof-contract]]
- [[concepts/singbox-process-stop-and-wait-sentinel]]
- [[concepts/byedpi-youtube-quic-probe-domain-contract]]
- [[concepts/warp-vpn-mode-exit-ip-proof-boundary]]
## Sources
- [[daily/2026-05-30]]: The runtime report separated WARP, FPTN, sing-box, MasterDNS, and ByeDPI into distinct signatures.
- [[daily/2026-05-30]]: FPTN was tied to health-preselect and HTTP 608 auth timeouts.
- [[daily/2026-05-30]]: MasterDNS needed Docker/container diagnostics because failure happened after image build at container run.
- [[daily/2026-05-30]]: ByeDPI pages worked while video playback still failed, pointing to QUIC/UDP/googlevideo paths.
