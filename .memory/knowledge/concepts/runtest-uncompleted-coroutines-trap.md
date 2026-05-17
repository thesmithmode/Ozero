---
title: "runTest + UncompletedCoroutinesError: testScope Children Trap"
aliases: [uncompleted-coroutines, testscope-infinite-coroutine, backgroundscope-pattern]
tags: [kotlin, testing, coroutines, gotcha]
sources:
  - "daily/2026-05-14.md"
created: 2026-05-14
updated: 2026-05-14
---

# runTest + UncompletedCoroutinesError: testScope Children Trap

`runTest` enforces that all child coroutines of `testScope` complete before the test finishes. When production objects (coordinators, DataStore, plugins) launch infinite coroutines via `launchIn(testScope)` or `scope = testScope`, those coroutines never complete — `runTest` throws `UncompletedCoroutinesError`. The fix is a separate `CoroutineScope(dispatcher + SupervisorJob())`, not a child of testScope.

## Key Points

- `launchIn(testScope)` creates a child coroutine of testScope — `runTest` waits for it and throws `UncompletedCoroutinesError` if it never completes
- `@AfterEach` runs AFTER `runTest` throws the exception — cleanup in tearDown is too late for stopping infinite coroutines
- Fix: create `coordinatorScope = CoroutineScope(dispatcher + SupervisorJob())` — same dispatcher for eagerness, but not a testScope child
- `PreferenceDataStoreFactory.create(scope = testScope)` has the same problem — DataStore holds background coroutines in testScope indefinitely; fix with per-test `datastoreScope`
- `backgroundScope` (kotlinx-coroutines-test 1.7+) auto-cancels after test — canonical pattern for long-running background jobs in tests

## Details

### The Mechanism

`runTest` wraps the test body in a `TestScope`. Any coroutine launched as a child of this scope (via `launch`, `launchIn`, or passing `testScope` as a constructor parameter) becomes part of the structured concurrency tree. After the test body completes, `runTest` waits for all children to finish. If a child is infinite (e.g., `flow.collect {}` or `while(true) { delay() }`), the wait never ends and `runTest` throws `UncompletedCoroutinesError` after a timeout.

The critical insight: `@AfterEach` in JUnit5 runs AFTER the test method returns — but `runTest` throws before returning control. So `coordinator.stop()` in `@AfterEach` never executes for the failing test.

### The TelegramProxyCoordinatorTest Incident (2026-05-14)

`TelegramProxyCoordinator` uses `combine(tunnelState, configFlow).launchIn(scope)` to observe VPN state. Tests passed `testScope` as the coordinator's scope. The combine-collect never completes → 6 tests failed with `UncompletedCoroutinesError`, each timing out at ~1 minute.

First fix attempt (symptom): added `coordinator.stop()` in `@AfterEach` — failed because `@AfterEach` runs after `runTest` throws.

Root fix: `coordinatorScope = CoroutineScope(dispatcher + SupervisorJob())` — the coordinator's infinite coroutine is not a testScope child. `runTest` ignores it. `@AfterEach coordinatorScope.cancel()` works because it cleans up a separate scope.

### DataStore Scope Trap

`PreferenceDataStoreFactory.create(scope = testScope)` injects testScope into DataStore internals. DataStore maintains background coroutines for disk I/O that never complete during a test lifecycle. Same `UncompletedCoroutinesError`.

Fix: per-test `datastoreScope = CoroutineScope(dispatcher + SupervisorJob())` passed to DataStore factory. `@AfterEach datastoreScope.cancel()`.

### backgroundScope Pattern (1.7+)

`kotlinx-coroutines-test` 1.7+ provides `backgroundScope` inside `runTest {}`. Coroutines launched in `backgroundScope` are automatically cancelled when the test body completes — before `runTest` checks for uncompleted children. This is the canonical solution for `while(true)` polling loops, stats collection, and similar long-running jobs in tests.

Used in Ozero for `startStatsPolling` in `EngineUrnetworkAwaitReadyTest`: `pluginScope = backgroundScope` instead of `this` (TestScope).

### Rules

- Never pass `testScope` to production objects with infinite coroutines
- `@AfterEach` cannot save you from `UncompletedCoroutinesError` — exception fires before tearDown
- Use `backgroundScope` for long-running background jobs in tests
- Use separate `CoroutineScope(dispatcher + SupervisorJob())` when `backgroundScope` is not available (e.g., object created outside `runTest` block)

## Related Concepts

- [[concepts/viewmodel-polling-runtest-trap]] - Similar: `while(true) + delay()` in viewModelScope hangs `advanceUntilIdle()`; different mechanism but same family of coroutine-test timing traps
- [[concepts/stateIn-eagerly-test-trap]] - Related coroutine test trap: `WhileSubscribed` vs `Eagerly` affects `.value` reads in tests
- [[concepts/byedpi-mock-server-ci-fragility]] - `backgroundScope` used for pluginScope in ByeDPI engine tests to prevent same error

## Sources

- [[daily/2026-05-14.md]] - Session 15:xx: `TelegramProxyCoordinatorTest` 6x `UncompletedCoroutinesError` — `launchIn(testScope)` root cause; `@AfterEach` too late; fix = separate coordinatorScope
- [[daily/2026-05-14.md]] - Session 17:xx: `DataStoreTelegramConfigStoreTest` 7x same error — DataStore with `testScope`; fix = per-test `datastoreScope`
- [[daily/2026-05-14.md]] - Session 18:00+: `EngineUrnetworkAwaitReadyTest` 4x same error — `startStatsPolling` while(true) in TestScope; fix = `backgroundScope`
