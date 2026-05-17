---
title: "stateIn(WhileSubscribed) Test Trap: .value Without Subscriber"
aliases: [stateIn-whilesubscribed-test, sharingstarted-eagerly-test, stateflow-value-inactive]
tags: [kotlin, testing, coroutines, viewmodel, gotcha]
sources:
  - "daily/2026-05-13.md"
created: 2026-05-13
updated: 2026-05-14
---

# stateIn(WhileSubscribed) Test Trap: .value Without Subscriber

`stateIn(started = SharingStarted.WhileSubscribed(...))` only activates the upstream flow while there is at least one active collector. When a test reads `.value` on the resulting `StateFlow` without first subscribing (e.g., before `collectAsState` or `runCurrent()` after a subscription), the stream is inactive and `.value` returns the `initialValue` passed to `stateIn`, not the current upstream value. This produces tests that pass against an uninitialized state and miss real behavior.

## Key Points

- `SharingStarted.WhileSubscribed` stops the upstream flow when subscriber count drops to zero — `.value` then holds the last emitted value or `initialValue`
- In tests using `StandardTestDispatcher`, `stateIn(Eagerly)` is required when tests need to read `.value` directly without a subscriber
- `stateIn(Eagerly)` activates the upstream immediately on creation and keeps it active for the coroutine scope's lifetime
- Symptom: test calls `vm.someStateFlow.value` immediately after VM creation and sees `null`/default instead of the ViewModel-emitted state
- Fix: change `SharingStarted.WhileSubscribed(N)` to `SharingStarted.Eagerly` for StateFlows whose `.value` is directly read in tests; OR add `runCurrent()` after creating a subscription in the test

## Details

### The Mechanism

`StateFlow.value` is always accessible — it returns whatever was last emitted, or `initialValue` if nothing has been emitted yet. When `SharingStarted.WhileSubscribed` is used, the upstream `Flow` that feeds the StateFlow is only collected while a subscriber exists. Without a subscriber:

1. The upstream `Flow` is not running
2. No new emissions occur
3. `.value` returns the last known value — which, immediately after ViewModel creation, is `initialValue`

In a production app, UI components subscribe via `collectAsStateWithLifecycle()`, activating the stream. In tests using `StandardTestDispatcher`, coroutines are not launched until `runCurrent()` or explicit advancement. A test that creates a ViewModel and immediately reads `.value` finds the stream inactive.

### The WarpEngineSettingsViewModel Incident (2026-05-13)

`WarpEngineSettingsViewModel.selectedDoHProvider` was implemented as `stateIn(WhileSubscribed(0))`. Tests read `vm.selectedDoHProvider.value` directly after creating the VM. With `WhileSubscribed(0)`, there were zero subscribers → stream inactive → `.value` returned `initialValue` (null) even after `vm.onSetDoHProvider(DoHProvider.CLOUDFLARE)` was called. The fix: change to `stateIn(Eagerly)`, which immediately activates the upstream and keeps it active.

### When to Use Each

| Scenario | `WhileSubscribed` | `Eagerly` |
|----------|------------------|-----------|
| Production UI collecting via `collectAsStateWithLifecycle` | ✓ Saves resources | OK but wastes resources |
| Test reads `.value` directly | ✗ Returns initialValue | ✓ Returns current state |
| Test uses `collectAsState` or `launch { collect {} }` | ✓ Works after runCurrent() | ✓ Always works |
| ViewModel used in multiple tests with reset | Either (with fresh VM per test) | Either |

### The Alternative: runCurrent() After Subscription

If `WhileSubscribed` is semantically correct for the API (e.g., resources should be freed when UI leaves), tests can work around it:

```kotlin
@Test fun `should reflect set provider`() = runTest {
    val collector = launch { vm.selectedDoHProvider.collect {} }  // subscribe
    runCurrent()  // activate the stream
    vm.onSetDoHProvider(DoHProvider.CLOUDFLARE)
    runCurrent()
    assertEquals(DoHProvider.CLOUDFLARE, vm.selectedDoHProvider.value)
    collector.cancel()
}
```

This is more verbose but preserves `WhileSubscribed` semantics in production.

### StateFlow Deduplication: distinctUntilChanged() Redundant on stateIn

`stateIn()` produces a `StateFlow`, which inherently deduplicates emissions by structural equality (`equals()`). Adding `.distinctUntilChanged()` before or after `stateIn()` is redundant — the StateFlow contract already guarantees no duplicate consecutive emissions. This applies to both `Eagerly` and `WhileSubscribed` variants.

Confirmed in session 21:13 (2026-05-13): polling loop with `stateIn` + `data class` state (where `equals()` is auto-generated) does not need `distinctUntilChanged()`. `AutoSelected` singleton and `Loaded` data class both provide correct equality semantics. Three-agent review confirmed this as sufficient dedup protection.

## Related Concepts

- [[concepts/viewmodel-stateflow-test-race]] - Related ViewModel test race: VM created before state setup → wrong initial emission; both are lifecycle ordering issues in tests
- [[concepts/viewmodel-polling-runtest-trap]] - `advanceUntilIdle()` hangs with `while(true)` polling; this article covers a different coroutine scheduler timing trap
- [[concepts/debounce-split-heterogeneous-flow]] - Related StateFlow sharing started patterns; production code uses `WhileSubscribed` for resource efficiency

## Sources

- [[daily/2026-05-13.md]] - Session 15:06: `WarpEngineSettingsViewModel.selectedDoHProvider` with `WhileSubscribed(0)` → test read `.value` without subscriber → saw `initialValue` not current state; fix = `SharingStarted.Eagerly`; Session 21:13: `distinctUntilChanged()` confirmed redundant on StateFlow from `stateIn()` — StateFlow deduplicates by equality
