---
title: ByeDPI strategy scan requires isolated engine and structured argv evolution
sources:
  - daily/2026-05-31.md
created: 2026-05-31
updated: 2026-06-09
---

# ByeDPI strategy scan requires isolated engine and structured argv evolution

## Key Points
- Deep strategy scan must not reuse the production singleton `ByeDpiEngine`, because scan start/stop/probe cycles can race with the active VPN engine.
- `EvolutionEngine.evaluate()` needs bounded start/probe time and `stop()` in a non-cancellable cleanup path after every attempted evaluation.
- ByeDPI genetic evolution must mutate option blocks, not arbitrary single CLI tokens, so valid detached values such as `-s 25+s` and quoted `-n` values survive crossover.
- Candidate metadata such as `verified` must reflect actually tested strategies, not all seed entries.
- Reduction should run only for confirmed positive-fitness candidates, and the scan service should be cancelable/non-sticky to avoid extra native churn.

## Details

The 2026-05-31 stabilization found that deep-scan and the working VPN shared the same native-backed ByeDPI engine. A hanging native start, stop, or probe could leave the UI at the "creating population" phase because `isInitializing` stayed true until the first generation finished. The fix direction is to inject a separate strategy-test engine, add hard timeouts around evaluation, and guarantee cleanup with `stop()` even when evaluation is cancelled.

The later review cycle showed that a simple allowlist validator is not enough. Real shipped strategies can contain short options with detached values, such as `-s 25+s`, and quoted SNI values for `-n`. The evolution layer should preserve option/value blocks and normalize only the intended value forms. This keeps the validator from rejecting valid production strategies while still preventing invalid generated argv from reaching native code.

Service lifecycle matters because a scan is not a VPN session. `START_NOT_STICKY`, a user-visible cancel action, and no self-restart from `onTaskRemoved` prevent a background strategy experiment from coming back after the user dismisses it. Timeout sizing also belongs to the evaluation contract: total evaluation time must account for probe batches and low concurrency, otherwise valid strategies can be marked `startFailed` simply because sequential domain probes exceeded a fixed window.

## Related Concepts
- [[concepts/byedpi-args-parsing]]
- [[concepts/byedpi-cmd-verbatim-pipeline]]
- [[concepts/byedpi-wedged-lane-generation-restart]]
- [[concepts/byedpi-argv-grammar-aware-validation]]
- [[connections/runtime-engine-fix-ci-proof-loop]]

## Sources
- [[daily/2026-05-31]]: sessions 11:32, 11:46, 13:07, 15:03, 16:19, and 17:08 describe the shared-engine race, bounded evaluation, structured argv issue, metadata risk, and Codex review findings.
- [[daily/2026-05-31]]: Sessions 13:07 and 17:08 record `START_NOT_STICKY`, cancel action, no task-removal self-restart, and timeout scaling for sequential probe batches.
