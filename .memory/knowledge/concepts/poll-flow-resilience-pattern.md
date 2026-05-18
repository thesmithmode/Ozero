---
title: "Poll-Flow Resilience: runCatching + Last-Value Fallback"
aliases: [poll-flow-runcatching, flow-exception-fallback, stateflow-poll-resilience]
tags: [kotlin, coroutines, pattern, android, reliability]
sources:
  - "daily/2026-05-18.md"
created: 2026-05-18
updated: 2026-05-18
---

# Poll-Flow Resilience: runCatching + Last-Value Fallback

Polling flows (`while(true) { delay(); emit(bridge.method()) }`) converted to `StateFlow` via `stateIn` cancel silently when the bridge method throws an exception. The flow terminates, the StateFlow holds the last emitted value forever, and re-subscription requires the ViewModel to be recreated. The fix wraps each poll call in `runCatching` with a last-known-value fallback, keeping the flow alive through transient failures. Combined with `distinctUntilChanged()` on the output to prevent unnecessary recompositions when the fallback value equals the previous emission.

## Key Points

- `flow { while(true) { emit(bridge.method()); delay(N) } }` — if `bridge.method()` throws, the flow terminates permanently
- `stateIn(WhileSubscribed)` does NOT restart the terminated upstream — the StateFlow freezes at the last value
- Fix: `runCatching { bridge.method() }.getOrElse { lastValue }` inside the poll loop — flow survives exceptions
- `distinctUntilChanged()` prevents recomposition when fallback returns the same value as previous emission
- Applied to 3 poll flows in Ozero: `peerCount`, `sharedTrafficBytes`, `accountPoints` — all call URnetwork SDK bridge methods that can throw during engine teardown

## Details

### The Termination Problem

A `flow { }` builder terminates when an uncaught exception propagates out of the block. For polling flows, this means a single transient error (bridge method called during engine teardown, network timeout, SDK internal error) permanently kills the data source. The ViewModel's `StateFlow` — derived via `stateIn` — freezes at whatever value was last emitted. The UI continues showing stale data with no indication that updates stopped.

This is particularly insidious because:
1. The flow doesn't crash the app (exception is caught by the coroutine framework)
2. The StateFlow still has a valid `.value` (the last emission before failure)
3. No error state is visible in the UI — data simply stops updating
4. Only a ViewModel recreation (screen navigation away and back) restarts the flow

### The Resilient Pattern

```kotlin
// BROKEN: single exception kills the flow
val peerCount = flow {
    while (true) {
        emit(sdkBridge.peerCount())  // throws during teardown → flow dead
        delay(PEER_POLL_MS)
    }
}.stateIn(viewModelScope, WhileSubscribed(5000), 0)

// CORRECT: survives transient exceptions
val peerCount = flow {
    var last = 0
    while (true) {
        val value = runCatching { sdkBridge.peerCount() }
            .getOrElse { last }  // use previous value on failure
        last = value
        emit(value)
        delay(PEER_POLL_MS)
    }
}.distinctUntilChanged()
 .stateIn(viewModelScope, WhileSubscribed(5000), 0)
```

The `runCatching` block catches any exception from the bridge call. On failure, the last known value is re-emitted. `distinctUntilChanged()` suppresses the duplicate emission when fallback == previous, preventing unnecessary UI recomposition. The flow continues polling — the next iteration may succeed if the bridge recovers.

### When This Pattern Applies

The pattern is necessary when:
- The poll target is a JNI bridge, SDK call, or network request that can throw
- The flow is long-lived (ViewModel scope, Application scope)
- Transient failures are expected (engine switching, teardown, network flaps)
- The UI should show stale-but-valid data rather than error state during transient failures

The pattern is NOT needed when:
- The poll target is a local in-memory data source (no exceptions expected)
- The flow is short-lived (single-screen lifecycle)
- Failure should propagate as an error state to the UI

### The Ozero Discovery (2026-05-18)

During the autonomous fix cycle (session 19:55), a code reviewer identified that `peerCount`, `sharedTrafficBytes`, and `accountPoints` poll flows in URnetwork-related ViewModels lacked exception handling. All three called URnetwork SDK bridge methods that throw `IllegalStateException` or `NullPointerException` during engine teardown (when the Go runtime is being released). A single engine switch could freeze all three data displays permanently.

The fix applied `runCatching` + last-value fallback to all three flows in the same commit (`1764d4b3`), establishing the pattern as a standard for all poll-based StateFlows in the project.

## Related Concepts

- [[concepts/collect-vs-collectlatest-restart-semantics]] - Related flow lifecycle issue: `collect` accumulates, `collectLatest` cancels; this article covers exception-driven termination
- [[concepts/engine-ownership-boundary]] - Bridge JNI calls during teardown are the primary exception source; ownership boundary prevents UI from calling bridge directly, but poll flows cross this boundary
- [[concepts/viewmodel-polling-runtest-trap]] - Related coroutine test trap for polling flows; `runCatching` does not affect test behavior but the fallback logic should be tested
- [[concepts/stateIn-eagerly-test-trap]] - `stateIn` behavior in tests; `distinctUntilChanged()` interacts with test assertions on emission counts

## Sources

- [[daily/2026-05-18.md]] - Session 19:55: code reviewer found 3 poll flows without exception handling; `runCatching` + last-value fallback applied to peerCount/sharedTrafficBytes/accountPoints; commit 1764d4b3
