---
title: "StateFlow waitFor(0) Test Race: Instant Completion vs Subscription"
aliases: [waitfor-zero-race, stateflow-subscription-race, exitlatch-test-pattern]
tags: [testing, kotlin, coroutines, gotcha, android]
sources:
  - "daily/2026-05-17.md"
created: 2026-05-17
updated: 2026-05-17
---

# StateFlow waitFor(0) Test Race: Instant Completion vs Subscription

When a mock process uses `waitFor()` returning `0` (instant exit), the `Running→Error` state transition happens faster than the test's second `awaitState()` call can subscribe to the StateFlow. The StateFlow emits `Running` then `Error` before the test collector is active — the test misses the transition and hangs or sees stale state.

## Key Points

- `Process.waitFor()` returning `0` = instant process exit → StateFlow transitions Running→Error in nanoseconds
- Test calls `awaitState(Running)` (succeeds) then `awaitState(Error)` — but Error was emitted before second subscription started
- StateFlow is conflated: intermediate emissions are lost if no active collector at emission time
- Fix: `exitLatch = CountDownLatch(1)` controls when the process "dies" — test subscribes to Error state BEFORE calling `latch.countDown()`
- Pattern established in other tests (ByeDpiEngineTest, MtgWrapperTest); `TelegramProxyServiceStateTest` missed it

## Details

### The Race Mechanism

```kotlin
// BROKEN: race between state transitions and test subscription
@Test fun `should transition to Error on process exit`() = runTest {
    service.start(config)
    awaitState(Running)       // ← subscribes, sees Running, returns
    // ... StateFlow emits Error HERE (process.waitFor()=0 returned instantly)
    awaitState(Error)         // ← subscribes AFTER Error was already emitted
                              //    StateFlow.value IS Error, but awaitState
                              //    may use flow.first { it == Error } which
                              //    needs a NEW emission, not current value
}
```

The exact failure depends on `awaitState` implementation. If it uses `flow.first { it == target }`, it waits for a new emission matching the target — but the emission already happened. If it reads `.value` directly, it works but has its own timing issues.

### The exitLatch Fix

```kotlin
// CORRECT: deterministic control of process lifecycle
val exitLatch = CountDownLatch(1)
every { process.waitFor() } answers { exitLatch.await(); 0 }

@Test fun `should transition to Error on process exit`() = runTest {
    service.start(config)
    awaitState(Running)       // process alive (latch blocks waitFor)
    // subscribe to Error BEFORE releasing the process
    val errorJob = launch { awaitState(Error) }
    exitLatch.countDown()     // NOW process exits → Running→Error
    errorJob.join()           // collector was active, catches the transition
}
```

The `CountDownLatch` pattern is the same as [[concepts/byedpi-mock-server-ci-fragility]] Root Cause 4: `answers { latch.await(); 0 }` simulates a blocking operation that the test controls explicitly.

### Discovery in TelegramProxyServiceStateTest

`TelegramProxyServiceStateTest` was the last test class to adopt the exitLatch pattern. Other test classes (`ByeDpiEngineTest`, `MtgWrapperTest`) had already migrated after similar race discoveries. The CI failure manifested as a timeout on `awaitState(Error)` — the test hung waiting for an emission that had already passed.

The fix also included a ktlint correction: the `answers` block was initially written as a one-liner `answers { latch.await(); 0 }` which violated the single-statement-per-line rule. Expanded to a multiline block.

### General Rule

Any test that:
1. Starts a mock process/service that can exit instantly
2. Observes state transitions via StateFlow
3. Has sequential `awaitState` calls for different states

Must use a latch (or similar synchronization) to control when the process exits, ensuring the test is subscribed to the target state BEFORE the transition occurs.

## Related Concepts

- [[concepts/byedpi-mock-server-ci-fragility]] - Same latch pattern for blocking JNI mocks; `answers { latch.await(); 0 }` prevents instant completion
- [[concepts/viewmodel-stateflow-test-race]] - Related StateFlow lifecycle issue: VM collects before test sets state; different trigger but same family
- [[concepts/test-io-thread-zombie-trap]] - Related test infrastructure issue: CountDownLatch also used there to replace Thread.sleep

## Sources

- [[daily/2026-05-17.md]] - Session 11:30+: TelegramProxyServiceStateTest race condition — `waitFor()=0` instant → Running→Error faster than second awaitState subscribes; fix = exitLatch pattern per ByeDpiEngineTest precedent
