---
title: FPTN cancellation-cooperative auth lifecycle
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---

# FPTN cancellation-cooperative auth lifecycle

## Key Points
- FPTN auth fallback must stop cooperatively when the engine is stopped or switched.
- `CancellationException` must not be treated as an ordinary auth failure that continues fallback.
- Mandatory serial auth over every candidate can create a `15s * N` startup ladder.
- The runtime start path should avoid turning fallback into a long blocking orchestration task.
- This refines [[concepts/fptn-dead-server-fallback]] and [[concepts/engine-switch-failure-containment]].

## Details

The 2026-05-29 log repeatedly traced FPTN failures against `v0.2.0` and later upstream evidence. The important finding was not that the token was dead. The user explicitly confirmed the token was valid, and upstream comparison rejected unrelated schema hypotheses. The actual lifecycle risk was serial auth fallback continuing after stop or engine switch.

In Ozero, `selectServerCandidates()` plus `authenticateFirstAvailable()` can turn startup into several sequential network attempts. With `AUTH_TIMEOUT_S=15`, one failed runtime start can hold the engine lifecycle for a long time. Because the chain orchestrator serializes start and stop, this long-lived auth path can block shutdown, restart, and neighboring engine transitions.

The fix direction recorded in the daily log is cancellation-cooperative auth: cancellation exits the attempt path instead of being converted into another fallback error, and the default runtime start avoids unnecessary full-candidate enumeration. Full fallback can remain as a controlled diagnostic or explicit fallback path, but not as an unbounded critical startup path.

## Related Concepts
- [[concepts/fptn-dead-server-fallback]]
- [[concepts/fptn-upstream-websocket-dns-boundary]]
- [[concepts/engine-switch-failure-containment]]
- [[concepts/regression-test-bounded-waits]]

## Sources
- [[daily/2026-05-29]]: FPTN token was confirmed valid; the bug was in code/config lifecycle, not token validity.
- [[daily/2026-05-29]]: Ozero log evidence showed serial auth fallback continuing after stop or switch.
- [[daily/2026-05-29]]: Commit `e53229e9` was recorded as a minimal cancellation/fallback lifecycle fix.
