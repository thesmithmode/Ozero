---
title: "ViewModel Polling Loop Breaks advanceUntilIdle in runTest"
aliases: [polling-viewmodel-runtest, whiletrue-delay-runtest, advanceuntilidle-deadlock]
tags: [kotlin, testing, coroutines, viewmodel, gotcha]
sources:
  - "daily/2026-05-10.md"
  - "daily/2026-05-19.md"
  - "daily/2026-05-20.md"
created: 2026-05-10
updated: 2026-06-09
---

# ViewModel Polling Loop Breaks advanceUntilIdle in runTest

A `while(true) + delay(N)` polling coroutine launched in `viewModelScope` (or any scope under `TestCoroutineScheduler`) causes `advanceUntilIdle()` to hang indefinitely in `runTest`. The coroutine never finishes — it is not "idle" — so the test scheduler cannot exhaust the work queue. The fix is to replace `advanceUntilIdle()` with `advanceTimeBy(N * steps)` + `runCurrent()` to advance virtual time by a specific amount and process scheduled work without waiting for all coroutines to complete.

**Extended rule (2026-05-19):** ANY `advanceUntilIdle()` call in a `runTest` block that has an active `collectJob` (backgroundScope subscriber, `launchIn(scope)`, or any coroutine collecting a StateFlow) = deadlock. The while(true) loop is not required — a subscriber coroutine alone is sufficient to prevent `advanceUntilIdle()` from returning. Replace with `runCurrent()`. Import required: `import kotlinx.coroutines.test.runCurrent`.

## Key Points

- `advanceUntilIdle()` runs virtual time forward until no more work is scheduled — `while(true)` never satisfies this condition → test hangs indefinitely
- `while(true) + delay(POLL_MS)` in a viewModelScope coroutine creates an infinite chain of scheduled `delay` continuations
- Fix: replace `advanceUntilIdle()` with `advanceTimeBy(POLL_MS * N)` to advance past N polling cycles, then `runCurrent()` to process any resulting state updates
- Related but different from `feedback_runtest_while_true_init` (init-loop causing 25-min CI timeout) — this is about test API choice, not about restructuring the VM
- The `while(true) + delay()` polling pattern itself is valid (simpler than `stateIn(WhileSubscribed)` for one-shot polling); the test must adapt

## Details

### The Mechanism

`runTest` uses a `TestCoroutineScheduler` that controls virtual time. All `delay()` calls in coroutines on `Dispatchers.Main.immediate` (the default for ViewModels in tests via `Dispatchers.setMain(UnconfinedTestDispatcher())`) use this virtual clock. `advanceUntilIdle()` advances virtual time in increments until there are no more pending coroutine continuations.

A `while(true) + delay(POLL_MS)` coroutine never reaches a terminal state — each `delay(POLL_MS)` produces a new continuation. After every advance, `advanceUntilIdle()` finds another pending item (the next `delay(POLL_MS)`) and advances again. This loop never terminates, leaving the test thread stuck in the `advanceUntilIdle()` call.

### The Ozero Discovery (v0.0.9 GROUP B)

`MainViewModel` added a polling coroutine in `viewModelScope` to refresh the URnetwork exit location every `URNETWORK_LOCATION_POLL_MS` milliseconds:

```kotlin
// In MainViewModel
init {
    viewModelScope.launch {
        while (true) {
            val location = sdkBridge.selectedLocationInfo()
            _locationState.value = location
            delay(URNETWORK_LOCATION_POLL_MS)
        }
    }
}
```

Tests that previously used `advanceUntilIdle()` to wait for the ViewModel to settle now hung indefinitely. The polling loop was structurally correct for production but incompatible with `advanceUntilIdle()`.

### The Fix: advanceTimeBy + runCurrent

```kotlin
// BROKEN: hangs forever
@Test fun `should display location after polling`() = runTest {
    val vm = MainViewModel(fakeSdkBridge)
    advanceUntilIdle()  // ← hangs
    assertEquals("Россия", vm.locationState.value?.country)
}

// CORRECT: advance past N polling cycles, process results
@Test fun `should display location after polling`() = runTest {
    val vm = MainViewModel(fakeSdkBridge)
    advanceTimeBy(URNETWORK_LOCATION_POLL_MS + 1)
    runCurrent()
    assertEquals("Россия", vm.locationState.value?.country)
}
```

`advanceTimeBy(N)` moves virtual time forward by exactly N milliseconds, triggering all `delay(M)` where `M <= N` to resume. `runCurrent()` executes all coroutines that are now ready to run without advancing time further. Together they provide deterministic test control over polling VMs without requiring the loop to terminate.

### When to Use Each Approach

| Test scenario | Use |
|--------------|-----|
| ViewModel launches finite work (fetch, one-shot init) | `advanceUntilIdle()` |
| ViewModel has infinite polling loop | `advanceTimeBy(POLL_MS * N) + runCurrent()` |
| Any active collectJob / backgroundScope subscriber | `runCurrent()` (never `advanceUntilIdle()`) |
| Need to test after exactly K poll cycles | `advanceTimeBy(POLL_MS * K) + runCurrent()` |
| Need to test that polling stops on cancellation | `vm.onCleared()` then `advanceUntilIdle()` |

### Relationship to viewModelScope Lifecycle

The polling coroutine is cancelled when `viewModelScope` is cleared (ViewModel `onCleared()`). In tests, call `vm.onCleared()` explicitly after the test body if teardown is needed. After `onCleared()`, the `while(true)` loop terminates and `advanceUntilIdle()` can safely be called to drain any remaining work.

### Alternative: stateIn + WhileSubscribed

The `stateIn(WhileSubscribed(stopTimeoutMillis))` pattern from `feedback_runtest_while_true_init` converts a polling `Flow` into a `StateFlow` that starts/stops automatically based on subscriber count. This approach works correctly with `advanceUntilIdle()` because the flow completes when all collectors are gone. However, it requires restructuring the data source as a `Flow` rather than a suspend loop, which adds complexity when the data source is a callback-based SDK (as with URnetwork's `selectedLocationInfo()`).

Both approaches are valid — choose based on whether the data source is naturally a `Flow` (use `stateIn`) or a suspend function called repeatedly (use `while(true) + delay` with `advanceTimeBy` in tests).

### Production Coroutine Wall Clock Trap (2026-05-20)

A complementary trap: using `System.currentTimeMillis()` in production coroutine code (not mock servers) to track duration between events.

```kotlin
// BROKEN: wall clock in EngineWatchdogCoordinator
private var zeroPeersSince: Long = 0L

while (true) {
    val peers = sdkBridge.peerCount()
    if (peers == 0) {
        if (zeroPeersSince == 0L) zeroPeersSince = System.currentTimeMillis()
        val elapsed = System.currentTimeMillis() - zeroPeersSince
        if (elapsed >= ZERO_PEERS_TIMEOUT_MS) triggerWatchdog()
    } else {
        zeroPeersSince = 0L
    }
    delay(PEER_POLL_MS)
}
```

In `runTest` with virtual dispatcher:
- `System.currentTimeMillis()` returns the **real wall clock**, not virtual time
- `advanceTimeBy()` / `delay()` advance virtual time but NOT `currentTimeMillis()`
- `elapsed` computed from wall clock is always tiny (microseconds between loop iterations in CI)
- `elapsed >= ZERO_PEERS_TIMEOUT_MS` never becomes true → watchdog never triggers → test asserts fail

Fix: replace wall clock with a poll counter that increments deterministically:

```kotlin
// CORRECT: poll counter
private var zeroPeersPolls: Int = 0

while (true) {
    val peers = sdkBridge.peerCount()
    if (peers == 0) {
        zeroPeersPolls++
        if (zeroPeersPolls >= ZERO_PEERS_POLL_THRESHOLD) triggerWatchdog()
    } else {
        zeroPeersPolls = 0
    }
    delay(PEER_POLL_MS)
}
```

Poll count is deterministic in tests: `advanceTimeBy(PEER_POLL_MS * N)` + `runCurrent()` produces exactly N poll iterations. Sentinel: `EngineWatchdogCoordinator` must not use `System.currentTimeMillis()` — checked by `OzeroVpnServicePeerWatchdogTest`.

The underlying rule: **never use wall clock (`System.currentTimeMillis`, `Instant.now()`) inside a coroutine that will be tested with virtual dispatcher.** Use virtual-clock-compatible primitives: `delay()`, counter increments, or `TestCoroutineScheduler.currentTime`.

## Related Concepts

- [[concepts/viewmodel-stateflow-test-race]] - Related ViewModel test ordering issue: VM created before state setup; both are about coroutine lifecycle in test contexts
- [[concepts/byedpi-mock-server-ci-fragility]] - `System.currentTimeMillis()` in mock server context (parallel trap in test code, not production code)
- [[concepts/urnetwork-peer-watchdog-recovery]] - The peer watchdog where the wall clock trap was discovered; fixed with poll counter
- [[concepts/urnetwork-sdk-integration]] - URnetwork location polling was the context where the original delay-loop deadlock was discovered
- [[concepts/urnetwork-ip-refresh-active-location-polling]] - Runtime reason the active URnetwork polling loop exists

### Diagnostic Pattern

Grep for any test file containing both an active subscriber and `advanceUntilIdle()`:
```
backgroundScope.launch { .*.collect
advanceUntilIdle()
```
Any test with both patterns (without explicit cancel between them) is a potential deadlock.

## Sources

- [[daily/2026-05-10.md]] - Session 17:46 GROUP B: `while(true) + delay(URNETWORK_LOCATION_POLL_MS)` in MainViewModel → `advanceUntilIdle()` eternal loop in tests; fix = `advanceTimeBy(URNETWORK_LOCATION_POLL_MS + 1)` + `runCurrent()`
- [[daily/2026-05-19.md]] - Session 13:53: extended rule — ANY `advanceUntilIdle()` with active `collectJob` = deadlock, not just when preceded by `advanceTimeBy()`; pattern is `backgroundScope.launch { flow.collect }` + `advanceUntilIdle()` before cancel; fix = `runCurrent()`; `import kotlinx.coroutines.test.runCurrent` required explicitly
- [[daily/2026-05-20.md]] - Session 19:52: production coroutine wall clock trap — `EngineWatchdogCoordinator` used `System.currentTimeMillis()` for peer-loss duration; virtual dispatcher doesn't advance wall clock → elapsed always near zero → watchdog never fires in CI; fix: `zeroPeersPolls` poll counter replacing wall clock; sentinel added to `OzeroVpnServicePeerWatchdogTest`
