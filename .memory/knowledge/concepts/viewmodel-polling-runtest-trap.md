---
title: "ViewModel Polling Loop Breaks advanceUntilIdle in runTest"
aliases: [polling-viewmodel-runtest, whiletrue-delay-runtest, advanceuntilidle-deadlock]
tags: [kotlin, testing, coroutines, viewmodel, gotcha]
sources:
  - "daily/2026-05-10.md"
created: 2026-05-10
updated: 2026-05-19
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

## Related Concepts

- [[concepts/viewmodel-stateflow-test-race]] - Related ViewModel test ordering issue: VM created before state setup; both are about coroutine lifecycle in test contexts
- [[concepts/byedpi-mock-server-ci-fragility]] - The `System.currentTimeMillis()` vs virtual clock issue is a parallel gotcha; outer loop time vs inner delay time
- [[concepts/urnetwork-sdk-integration]] - URnetwork location polling was the context where this trap was discovered

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
