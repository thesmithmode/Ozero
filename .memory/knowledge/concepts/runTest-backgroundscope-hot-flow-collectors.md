---
title: runTest backgroundScope for hot flow collectors
sources:
  - daily/2026-06-01.md
created: 2026-06-01
updated: 2026-06-01
---
# runTest backgroundScope for hot flow collectors

## Summary
Infinite or long-lived Flow collectors in coroutine tests must not be launched in the main `runTest` scope. They should use `backgroundScope` or an explicitly controlled test dispatcher so `runTest` can finish deterministically.

## Key Points
- A `Flow.toList()` or endless `collect {}` launched with plain `launch` inside `runTest` keeps the test scope alive.
- Such collectors can hang CI without JUnit `<failure>` or `<error>` entries.
- `backgroundScope.launch` is the right tool for collectors that are expected to live until test teardown.
- Hot flows on `StandardTestDispatcher` can still race with emissions; an `UnconfinedTestDispatcher(testScheduler)` collector can be needed for deterministic subscription.
- Timing fixes such as `runCurrent()` or `advanceTimeBy()` are insufficient if the collector or debounced coroutine has not actually reached the expected suspension point.

## Details
The June 1 CI investigation found that `Tests - app` could hang because `EngineSettingsRestartObserverTest` and later app tests used infinite Flow collection in the main `runTest` scope. Since `runTest` waits for child jobs in its scope, a collector that never completes blocks the whole job and produces no ordinary assertion failure in JUnit XML.

The fix pattern is to place expected-long-lived collectors in `backgroundScope.launch`. For hot `MutableSharedFlow` tests, deterministic ordering may also require a collector dispatcher that subscribes and handles emissions immediately, such as `UnconfinedTestDispatcher(testScheduler)`. A separate runtime fix used `CoroutineStart.UNDISPATCHED` so a debounce coroutine could enter `delay()` before virtual time was advanced.

This concept relates to [[concepts/runtest-uncompleted-coroutines-trap]] and [[concepts/stateflow-waitfor-zero-test-race]], but adds the app-CI symptom: no JUnit failures, just a hanging job. It also connects to [[concepts/regression-test-bounded-waits]] because tests that rely on long waits need explicit termination behavior.

## Related Concepts
- [[concepts/runtest-uncompleted-coroutines-trap]]
- [[concepts/stateflow-waitfor-zero-test-race]]
- [[concepts/regression-test-bounded-waits]]
- [[concepts/runtime-restart-pending-fingerprint-baseline]]

## Sources
- [[daily/2026-06-01]]: The log records that `EngineSettingsRestartObserverTest` hung because infinite collectors were launched in the main `runTest` scope and were moved to `backgroundScope.launch`.
- [[daily/2026-06-01]]: Later failures showed races around `StandardTestDispatcher`, hot `MutableSharedFlow` emissions, and the need for controlled collector dispatch.
