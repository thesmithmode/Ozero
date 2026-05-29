---
title: Stale engine signals and cross-engine failures
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---
# Stale engine signals and cross-engine failures

## Summary
Stale callbacks from one engine attempt can surface as a failure under another engine label when start/stop orchestration, candidate retries, and UI state transitions overlap.

## Key Points
- `Failed(BYEDPI, timeout)` can be a symptom of an earlier FPTN or orchestration failure.
- Candidate retries and reset paths must preserve attempt identity.
- Stop timeout and stale job completion must not overwrite current active engine state.
- Runtime diagnosis must correlate `startChain`, `onEngineDied`, `onEngineFailed`, and UI state by timestamp and engine attempt.
- The pattern connects [[concepts/auto-candidate-terminal-status-invariant]] with [[concepts/byedpi-wedged-lane-generation-restart]].

## Details
The daily log repeatedly showed apparent ByeDPI failures while FPTN authentication was still active, timing out, or being reset. The conclusion was that displayed engine status is not always the event origin. Cross-engine state can be overwritten when stale callbacks arrive after a new candidate or new engine has become active.

This connection explains why fixes focused only on ByeDPI stop logic or only on FPTN token handling were insufficient. Correct recovery needs both sides: candidate failure isolation in `StartSequenceCoordinator`/`TunnelController`, and per-engine lifecycle isolation such as ByeDPI generation guards.

## Related Concepts
- [[concepts/auto-candidate-terminal-status-invariant]]
- [[concepts/byedpi-wedged-lane-generation-restart]]
- [[concepts/fptn-cancellation-cooperative-auth-lifecycle]]
- [[concepts/engine-switch-failure-containment]]

## Sources
- [[daily/2026-05-29]]: log analysis tied false `Failed(BYEDPI, timeout)` to FPTN auth cycles and overlapping lifecycle events.
- [[daily/2026-05-29]]: accepted fix plan required stale-signal guards, candidate attempt identity, and generation protection for wedged ByeDPI jobs.
