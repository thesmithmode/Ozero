---
title: FPTN v1.0.9 health-preselect regression boundary
sources:
  - daily/2026-05-30.md
created: 2026-05-31
updated: 2026-05-31
---
# FPTN v1.0.9 health-preselect regression boundary

## Key Points
- FPTN failures after a stable `v1.0.9` baseline should be treated as likely regression until the new startup flow is proven safe.
- Health-preselect before auth can reorder servers and select candidates different from the visible selected location.
- A short per-candidate auth timeout can turn slow but viable servers into HTTP 608 startup failures.
- Auto mode may try multiple token servers, but this must fit one bounded startup budget and preserve clear diagnostics.
- This expands [[concepts/fptn-health-preselect-auth-timeout-regression]] and [[concepts/fptn-healthcheck-auth-diagnostics-contract]].

## Details

The 2026-05-30 runtime trace showed FPTN failing with repeated HTTP 608 auth timeouts after a release baseline where FPTN had been stable. The important regression boundary was not simply that one server was dead. Current code had introduced health-preselect before auth, changed server ordering, and limited authentication to about three seconds per candidate.

That combination can make the UI-selected location misleading and can make startup fail before a stable server has a fair auth window. The log pattern included a selected France location while auth attempts went to other candidates and failed with HTTP 608. The durable lesson is that health checking, candidate ordering, and auth timeout are one contract, not independent tuning knobs.

The desired architecture remains bounded: `autoSelect=true` can try token servers in order or by proven availability within one total startup budget, while `autoSelect=false` should use the explicit selected server and avoid silent fallback. Failures should be classified and propagated through the start pipeline so UI and diagnostics distinguish no viable candidates, auth timeout, HTTP 608, and cancellation.

## Related Concepts
- [[concepts/fptn-health-preselect-auth-timeout-regression]]
- [[concepts/fptn-healthcheck-auth-diagnostics-contract]]
- [[concepts/fptn-single-auth-default-start-contract]]
- [[connections/startup-readiness-runtime-recovery-boundary]]

## Sources
- [[daily/2026-05-30]]: User reported FPTN no longer started although release `1.0.9` had worked stably.
- [[daily/2026-05-30]]: Trace analysis found repeated HTTP 608 auth timeouts and current code doing health-preselect before auth with short per-candidate timeout.
- [[daily/2026-05-30]]: Earlier sessions clarified that the problem was not fallback itself, but serial or poorly bounded startup policy and weak diagnostics.
- [[daily/2026-05-30]]: The planned repair was a consolidated health-check plus ordered auth pipeline with structured failure reasons.
