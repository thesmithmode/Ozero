---
title: "StateFlow.update{} vs .value= Assignment Race"
aliases: [stateflow-update-atomic, mutablestateflow-race, update-vs-value]
tags: [kotlin, coroutines, concurrency, android, gotcha]
sources:
  - "daily/2026-05-16 (1).md"
created: 2026-05-16
updated: 2026-05-16
---

# StateFlow.update{} vs .value= Assignment Race

`MutableStateFlow.value = current.copy(...)` is a read-modify-write sequence split into two operations: read `value`, compute `copy()`, write `value`. If two coroutines execute this sequence concurrently, the second read can happen before the first write completes, causing one update to silently overwrite the other. `MutableStateFlow.update { current -> current.copy(...) }` is atomic: it performs the read-modify-write in a single compare-and-set loop, retrying if the value changed between read and write.

## Key Points

- `.value = state.copy(field = x)` is NOT atomic — two concurrent calls can lose one update
- `.update { it.copy(field = x) }` is atomic — uses CAS loop, safe for concurrent calls
- ViewModel functions that mutate `_uiState` should always use `.update {}` unless the function is provably single-threaded (rare in Android)
- The race is non-deterministic: tests on `TestCoroutineDispatcher` may pass while real device fails
- Affected functions in Ozero: `selectLocation`, `setProvidePaused`, `applyFilter` in `UrnetworkEngineSettingsViewModel`

## Details

### The Race Window

```kotlin
// BROKEN: read-modify-write split
fun selectLocation(id: String) {
    val current = _uiState.value          // READ
    _uiState.value = current.copy(        // WRITE (stale if concurrent)
        selectedLocation = id
    )
}

fun setProvidePaused(paused: Boolean) {
    val current = _uiState.value          // READ — may read before selectLocation writes
    _uiState.value = current.copy(        // WRITE — overwrites selectLocation's change
        providePaused = paused
    )
}
```

If `selectLocation` and `setProvidePaused` are called near-simultaneously (e.g., from two different coroutines on `Dispatchers.Default`), whichever writes last wins. The other write is silently discarded.

### The Fix

```kotlin
// CORRECT: atomic read-modify-write
fun selectLocation(id: String) {
    _uiState.update { current ->
        current.copy(selectedLocation = id)
    }
}

fun setProvidePaused(paused: Boolean) {
    _uiState.update { current ->
        current.copy(providePaused = paused)
    }
}
```

`update {}` uses a `compareAndSet` loop internally: reads the current value, applies the transform, attempts to set — if the value changed between read and set, it retries with the new current value. No update is lost.

### Android ViewModel Context

Android ViewModels process events from multiple sources: user clicks (main thread), flow collectors (`viewModelScope.launch`), and callbacks. Even with `Dispatchers.Main`, coroutines can interleave at suspension points. The `.update {}` idiom is the standard defensive pattern for any `MutableStateFlow` with multiple writers.

### Testing Blind Spot

The race is timing-dependent and rarely manifests in unit tests using `StandardTestDispatcher` or `UnconfinedTestDispatcher`, where coroutines execute in a controlled, non-concurrent order. The bug surfaces on real devices under load. This makes `.value=` dangerous even when tests pass.

## Related Concepts

- [[concepts/viewmodel-stateflow-test-race]] - StateFlow emission ordering in tests; complementary: tests may miss races that `.update{}` prevents
- [[concepts/collect-vs-collectlatest-restart-semantics]] - Another concurrency idiom for flow processing where the wrong choice causes parallel accumulation
- [[concepts/standard-test-dispatcher-lies]] - Test dispatcher masking real-device concurrency issues

## Sources

- [[daily/2026-05-16 (1).md]] - Session 13:37 task #48: `_uiState.value = current.copy(...)` race in `selectLocation`/`setProvidePaused`/`applyFilter` → replaced with `_uiState.update { }` for atomic read-modify-write
