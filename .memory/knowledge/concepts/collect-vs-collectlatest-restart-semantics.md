---
title: "collect vs collectLatest for Restart-Semantic Flows"
aliases: [collect-vs-collectlatest, collectlatest-cancel, flow-restart-semantics]
tags: [kotlin, coroutines, gotcha, android, state-management]
sources:
  - "daily/2026-05-16.md"
created: 2026-05-16
updated: 2026-05-16
---

# collect vs collectLatest for Restart-Semantic Flows

`Flow.collect { }` runs the collector lambda to completion for each emission before processing the next. If the lambda contains a long-running or infinite operation (retry loop, polling), a new emission does NOT cancel the previous collector — both run in parallel. `Flow.collectLatest { }` cancels the previous collector lambda when a new value arrives, ensuring only the latest emission's work is active. For flows where each emission represents a "restart" semantic (new engine selection, new configuration), `collectLatest` is required.

## Key Points

- `collect { active -> startPolling(active) }` — if `active` flips true→false→true, two `startPolling(true)` run in parallel
- `collectLatest { active -> startPolling(active) }` — second emission cancels the first, only the latest polling loop runs
- Critical for ViewModel `init` blocks that observe settings/config flows and launch dependent operations
- In Ozero: `UrnetworkEngineSettingsViewModel.init` used `collect` for provider-mode flow — rapid toggle launched parallel retry loops consuming resources
- Rule: if the collector lambda has suspension points (delay, retry, network call), default to `collectLatest` unless parallel execution is intentionally desired

## Details

### The Parallel Accumulation Problem

`StateFlow.collect` guarantees that the collector processes each distinct value, but the collector lambda runs as a regular suspend function — it is NOT cancelled when a new value arrives. Consider:

```kotlin
// BROKEN: parallel accumulation
init {
    viewModelScope.launch {
        configStore.provideMode.collect { mode ->
            if (mode == ALWAYS) {
                while (true) {
                    refreshPeers()
                    delay(5000)
                }
            }
        }
    }
}
```

When `provideMode` emits `ALWAYS` → `AUTO` → `ALWAYS`:
1. First `ALWAYS`: starts `while(true)` polling
2. `AUTO`: collect waits for current polling iteration to complete (or next suspension point)
3. Second `ALWAYS`: starts ANOTHER `while(true)` polling — first one still running

After N toggles, N parallel polling loops consume CPU and potentially produce concurrent state mutations.

### The collectLatest Fix

```kotlin
// CORRECT: cancels previous on new emission
init {
    viewModelScope.launch {
        configStore.provideMode.collectLatest { mode ->
            if (mode == ALWAYS) {
                while (true) {
                    refreshPeers()
                    delay(5000)
                }
            }
        }
    }
}
```

When `provideMode` emits `ALWAYS` → `AUTO`:
1. First `ALWAYS`: starts polling
2. `AUTO`: cancels the polling coroutine (at the next `delay` suspension point), starts new collector for `AUTO`

No accumulation. Only the latest emission's work runs.

### When collect Is Correct

`collect` is appropriate when:
- The collector lambda is fast and non-suspending (simple state assignment)
- Parallel processing of emissions is intentionally desired (event logging, analytics)
- The flow is cold and terminates (not an infinite StateFlow)

`collectLatest` is appropriate when:
- The collector launches long-running work (polling, retries, network calls)
- Each new emission should "replace" the previous work (configuration change, engine switch)
- The flow is a StateFlow or SharedFlow that may re-emit during collector execution

### The Ozero Discovery

In `UrnetworkEngineSettingsViewModel.init`, `collect` was used on `configStore.provideActive` — a flow that emits `true/false` when the user toggles the URnetwork provider mode. Each `true` emission launched a retry loop for peer connection. Rapid toggling (which happens during engine-switch chains) accumulated parallel retry loops. Changing to `collectLatest` ensured only the latest toggle's retry loop ran, fixing resource leak under rapid switching.

## Related Concepts

- [[concepts/debounce-split-heterogeneous-flow]] - Debounce for batching rapid emissions; collectLatest for cancelling stale work — complementary tools for flow management
- [[concepts/viewmodel-polling-runtest-trap]] - while(true)+delay() in viewModelScope; collectLatest cancels the loop on new emission, preventing the accumulation that advanceUntilIdle can't handle
- [[concepts/engine-switch-chain-cascading-failures]] - Rapid engine switching (7 startVpn in 30s) is the production scenario where collect accumulation manifests

## Sources

- [[daily/2026-05-16.md]] - Session 13:37 task #47: `UrnetworkEngineSettingsViewModel.init` used `collect` → parallel retry loops on rapid toggle; fix = `collectLatest` — old loop not cancelled on flip-back
