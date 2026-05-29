---
title: Auto-candidate terminal status invariant
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---
# Auto-candidate terminal status invariant

## Summary
Auto-candidate startup may try several candidates, but only the final candidate is allowed to publish terminal failure to UI or watchdog state. Earlier failures must remain internal diagnostics.

## Key Points
- Non-terminal candidate failures must not call the same path as a final engine failure.
- `notifyFailure=false` is not enough if stale callbacks can later overwrite current state.
- Candidate attempts need a correlation boundary such as `candidateAttemptId` or equivalent generation tracking.
- `resetAfterAutoCandidateFailure` must be idempotent and safe during stop/restart races.
- This invariant protects [[concepts/byedpi-wedged-lane-generation-restart]] and [[concepts/fptn-cancellation-cooperative-auth-lifecycle]] from cross-engine false status.

## Details
The daily log shows a recurring false symptom: `Failed(BYEDPI, timeout)` appeared while FPTN candidate authentication was still active or after its timeout path. The important lesson was that the displayed engine label can be a stale or overwritten state, not the true source of the failure.

The accepted fix direction is to separate intermediate candidate errors from terminal engine failure. A candidate failure can trigger cleanup, logging, or the next pick, but it must not drive UI/watchdog failure until the candidate list is exhausted and the event still belongs to the active start attempt.

## Related Concepts
- [[connections/stale-engine-signals-cross-engine-failures]]
- [[concepts/fptn-cancellation-cooperative-auth-lifecycle]]
- [[concepts/byedpi-wedged-lane-generation-restart]]
- [[concepts/engine-switch-failure-containment]]

## Sources
- [[daily/2026-05-29]]: repeated analysis tied false `Failed(BYEDPI, timeout)` to FPTN/start-sequence candidate transitions.
- [[daily/2026-05-29]]: final plan required non-terminal auto-candidate failures to avoid terminal UI failure before the last candidate.
