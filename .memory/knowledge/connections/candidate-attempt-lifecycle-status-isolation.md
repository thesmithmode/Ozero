---
title: Candidate attempt lifecycle status isolation
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---

# Candidate attempt lifecycle status isolation

## Summary
Auto-candidate retries, engine lifecycle events, and UI terminal status must be isolated by attempt identity so stale or intermediate failures cannot appear as the active engine failure.

## Key Points
- Intermediate auto-candidate failures must not publish terminal UI/watchdog failure until the final candidate fails.
- Long FPTN auth attempts under the chain orchestrator can overlap stop/restart transitions and create stale signals.
- ByeDPI `Failed(timeout)` can be a downstream status leak from an earlier FPTN or orchestration failure, not the real current root cause.
- Attempt IDs or generation guards are needed for both engine jobs and lifecycle status events.

## Details
The FPTN/ByeDPI investigation showed a non-obvious link between candidate retry behavior and false engine attribution. FPTN could run a serial `15s * N` auth ladder, while `ChainOrchestrator` serialized start/stop with a mutex. When a timeout or stop happened mid-ladder, stale signals could later surface under another engine label such as `Failed(BYEDPI, timeout)`.

The same principle appeared in the ByeDPI fix: wedged native/proxy jobs needed lane rotation and `proxyGeneration` guards so old jobs could not overwrite state from a later start. The connection is that both systems require attempt-scoped ownership of status publication.

## Related Concepts
- [[concepts/auto-candidate-terminal-status-invariant]]
- [[concepts/chain-start-timeout-stale-engine-failure-cascade]]
- [[concepts/byedpi-wedged-lane-generation-restart]]
- [[connections/stale-engine-signals-cross-engine-failures]]

## Sources
- [[daily/2026-05-29]]: Logs and diff analysis tied false `Failed(BYEDPI, timeout)` states to overlapping FPTN candidate/auth lifecycle and stale callbacks.
- [[daily/2026-05-29]]: The final plan required non-terminal auto-candidate failures, candidate attempt tracing, and generation guards for stale native jobs.
