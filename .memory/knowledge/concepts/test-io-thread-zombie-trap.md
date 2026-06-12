---
title: "IO Thread Zombie Trap: Thread.sleep in Mocked Background Tasks"
aliases: [test-thread-sleep-zombie, android-test-io-cleanup, countdownlatch-vs-sleep]
tags: [testing, android, coroutines, gotcha, flaky-tests]
sources:
  - "daily/2026-05-08.md"
created: 2026-05-08
updated: 2026-06-12
---

# IO Thread Zombie Trap: Thread.sleep in Mocked Background Tasks

When a test mock simulates a long-running IO operation using `Thread.sleep(60_000)`, the sleeping thread is not automatically interrupted when `@AfterEach` runs. If the test uses `Dispatchers.IO` or a `CoroutineScope(Dispatchers.IO)` that is not explicitly cancelled in teardown, the sleeping thread remains alive past the test boundary. Subsequent tests in the same Gradle daemon process accumulate these zombie IO threads; eventually resource exhaustion causes unrelated IO-timeout tests to fail nondeterministically.

## Key Points

- `Thread.sleep(N)` in a mock running on `Dispatchers.IO` is not interrupted by `@AfterEach` — interruption requires `scope.cancel()` or explicit `thread.interrupt()`
- `proxyScope(Dispatchers.IO)` (or similar `CoroutineScope`) not closed in `@AfterEach` leaves its threads alive between test cases
- Zombie threads accumulate in the Gradle daemon JVM across multiple test cases — 5-10 zombies is enough to delay or timeout subsequent IO-bound operations
- Symptom: test passes in isolation, fails when run with the full suite under resource pressure; flaky on CI, reproducible under load
- Fix: replace `Thread.sleep(N)` with `CountDownLatch(1).await()` + `latch.countDown()` in teardown, and cancel the owning scope when possible
- Also: OOM / Gradle daemon crash with exit code 0 can mask the real zombie-thread cause — always check thread count when diagnosing flaky IO tests

## Details

### The Zombie Thread Mechanism

The pattern appears in tests for background services that accept connections or perform blocking IO:

```kotlin
// PROBLEMATIC mock setup
val mockServer = object : SocksProxyMock {
    override fun listen(port: Int) {
        proxyScope(Dispatchers.IO).launch {
            Thread.sleep(60_000)  // simulate long-lived proxy
        }
    }
}
```

When `@AfterEach` calls `mockServer.stop()`, it sets a flag and returns. The coroutine launched on `Dispatchers.IO` is still sleeping — `stop()` does not cancel the coroutine scope or interrupt the thread. The JVM thread pool behind `Dispatchers.IO` keeps the thread parked for up to 60 seconds.

In a typical test class with 10+ test cases, each `@BeforeEach` + `@AfterEach` cycle spawns one zombie. By test #6 or #7, the `Dispatchers.IO` pool may be saturated with sleeping threads. The next test that tries to start a real IO operation gets queued behind these zombies. If the test has a 5-second timeout on `waitSocksReady`, it fails even though the underlying implementation is correct.

### The Fix: CountDownLatch

```kotlin
// CORRECT: deterministic thread release
private lateinit var latch: CountDownLatch

@BeforeEach
fun setUp() {
    latch = CountDownLatch(1)
    mockServer = object : SocksProxyMock {
        override fun listen(port: Int) {
            proxyScope(Dispatchers.IO).launch {
                latch.await()  // blocks until latch.countDown() called
            }
        }
    }
}

@AfterEach
fun tearDown() {
    latch.countDown()  // releases the thread immediately
    scope.cancel()     // cancel the coroutine scope
}
```

`CountDownLatch.await()` blocks the thread just like `Thread.sleep`, but `latch.countDown()` in `tearDown` releases it immediately — no wall-clock wait, no accumulation. The thread returns to the pool before the next test starts.

The teardown must release both the blocking primitive and the coroutine owner when the test owns that scope. The latch prevents long wall-clock sleeps from crossing test boundaries; scope cancellation prevents newly scheduled background work from surviving after the test case.

### The ByeDpiEngineTest Incident (v0.0.7)

`ByeDpiEngineTest.startSuccessWhenSocksPortReady` tested that the engine successfully starts when a SOCKS5 proxy is listening. The test mock used `Thread.sleep(60_000)` inside `proxyScope(Dispatchers.IO)` to simulate a long-lived proxy. The test passed in isolation.

On CI, after the full `engine-byedpi` test suite ran (13+ test cases), the zombie thread pool was exhausted. The `waitSocksReady` function has a 5-second timeout; the underlying coroutine needed a thread from `Dispatchers.IO` to complete the readiness check but couldn't get one. Result: `startSuccessWhenSocksPortReady` failed with a timeout, causing CI to go red, blocking the v0.0.7 release.

The Gradle daemon exited with code 0 during one attempt (indicating OOM kill), which initially masked the zombie-thread diagnosis. Thread count analysis in the subsequent attempt confirmed the pattern.

### Related Anti-Patterns

This trap shares a family with `Thread.sleep` in test assertions (fixed by `advanceUntilIdle()` in coroutine tests) and scope-leak between test cases (fixed by explicit `scope.cancel()` in `@AfterEach`). The common root: test cleanup must be deterministic and complete before the next test starts. Any resource that isn't explicitly cleaned up will eventually cause nondeterministic failures under load.

## Related Concepts

- [[concepts/viewmodel-stateflow-test-race]] - Related test infrastructure issue: ViewModel created before state setup leads to race; both are about test lifecycle ordering
- [[concepts/junit-platform-silent-skip]] - Another test infrastructure trap causing CI to report incorrect results
- [[concepts/gradle-continue-full-failures]] - `--continue` ensures zombie-thread failures in one module are visible alongside other module failures
- [[concepts/byedpi-connection-probe-injection-contract]] - ByeDPI tests should inject IO readiness probes to avoid real timing races

## Sources

- [[daily/2026-05-08.md]] - Session 19:15: release tag was deleted and reissued after replacing the sleeping mock with deterministic latch teardown.

- [[daily/2026-05-08.md]] - Session 19:15: `ByeDpiEngineTest.startSuccessWhenSocksPortReady` flaky on CI; root cause = `Thread.sleep(60_000)` in mock not interrupted by `@AfterEach`; `proxyScope(Dispatchers.IO)` not cancelled; fix = `CountDownLatch.await()` + `countDown()` in tearDown; Gradle daemon exit code 0 masked root cause temporarily
