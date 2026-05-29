---
title: Stale engine signals and cross-engine failures
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---

# Stale engine signals and cross-engine failures

## Key Points
- A failure shown under one engine label can originate from an older lifecycle event from another engine.
- `Failed(BYEDPI, timeout)` during FPTN activity was treated as a stale or cross-engine signal candidate.
- Generation ids, candidate attempt ids, and stale-signal guards are needed to preserve status ownership.
- Stop/start serialization can amplify stale signals into app-wide poisoned state.
- This links [[concepts/byedpi-wedged-lane-generation-restart]], [[concepts/fptn-cancellation-cooperative-auth-lifecycle]], and [[concepts/engine-failure-recovery-isolation]].

## Details

The 2026-05-29 investigation repeatedly found that visible failures were not always owned by the label shown in UI. The most important example was `Failed(BYEDPI, timeout)` appearing after or during FPTN auth/start cycles, often without an explicit current ByeDPI start in the same interval. This led to the interpretation that stale callbacks or overlapping lifecycle transitions could repaint the current state with an old engine id.

The same pattern appeared in several fixes. ByeDPI needed `proxyGeneration` so an old proxy/native job could not clear the state of a new lane. StartSequence needed non-terminal candidate handling so intermediate failures would not become UI terminal failures. FPTN needed cancellation-cooperative auth so stopped attempts would not continue to emit fallback errors after the user moved on.

This connection explains why engine regressions must be diagnosed through timeline ownership, not only module logs. A single stale signal can poison ChainOrchestrator, trigger wrong stop/restart behavior, and make unrelated engines look broken. The durable invariant is that every failure must be correlated with the active sequence, candidate, and engine generation before becoming terminal state.

## Related Concepts
- [[concepts/byedpi-wedged-lane-generation-restart]]
- [[concepts/fptn-cancellation-cooperative-auth-lifecycle]]
- [[concepts/engine-failure-recovery-isolation]]
- [[concepts/engine-switch-regressions-baseline-runtime-proof]]

## Sources
- [[daily/2026-05-29]]: Logs showed `Failed(BYEDPI, timeout)` near FPTN cycles, leading to stale/cross-engine signal analysis.
- [[daily/2026-05-29]]: `proxyGeneration` was introduced for ByeDPI to prevent old jobs from overwriting new runtime state.
- [[daily/2026-05-29]]: StartSequence and FPTN fixes targeted non-terminal candidate failures and cancellation-cooperative auth.
