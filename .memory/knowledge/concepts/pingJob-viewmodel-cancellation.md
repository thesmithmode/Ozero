---
title: "PingJob ViewModel Cancellation Pattern"
aliases: [ping-job-cancellation, viewmodel-job-reference, isPinging-clear-pattern]
tags: [android, kotlin, compose, viewmodel, coroutines, ping]
sources:
  - "daily/2026-05-26.md"
created: 2026-05-26
updated: 2026-05-26
---

# PingJob ViewModel Cancellation Pattern

When implementing a cancellable ping operation in a ViewModel, a `Job?` reference must be stored at the ViewModel level to enable cancellation. Without the stored reference, the running coroutine cannot be cancelled on demand. Calling `isPinging.clear()` before launching a new job is the correct pattern — the old job is already cancelled at that point, and the cleared state allows the new job to start cleanly.

## Key Points

- Store `pingJob: Job?` as a ViewModel field — no stored reference means no cancellation capability
- `isPinging.clear()` before launching a new pingJob is correct, not a race condition: the previous job is cancelled, the state is reset, then the new job starts
- `LaunchedEffect(key)` in Compose auto-cancels the previous effect when `key` changes — this is standard Compose behavior, not a race
- UI button text: `PlayArrow` icon → `TextButton("Тест")` for clarity; when `isPinging` is active → `TextButton("Отмена")` that invokes cancel
- "Отмена" string was already present in `strings.xml` — no new string resources needed

## Details

### The Missing Reference Problem

A ping coroutine launched via `viewModelScope.launch { ... }` returns a `Job`. If this `Job` is not stored, the only way to cancel it is to cancel the entire `viewModelScope`, which is drastic and cancels all other ViewModel operations. For a UI "Cancel" button to work for only the ping operation, the specific `Job` must be retained:

```kotlin
class SingboxServerListViewModel : ViewModel() {
    private var pingJob: Job? = null
    val isPinging = MutableStateFlow(false)

    fun startPing(serverId: String) {
        pingJob?.cancel()           // cancel any running ping
        isPinging.value = false     // reset before new job
        pingJob = viewModelScope.launch {
            isPinging.value = true
            try {
                // ... probe logic ...
            } finally {
                isPinging.value = false
            }
        }
    }

    fun cancelPing() {
        pingJob?.cancel()
        isPinging.value = false
    }
}
```

### isPinging.clear() Is Not a Race

A code review finding suggested that `isPinging.clear()` (or setting to `false`) before the new job starts could be a race where two jobs briefly co-exist. This is incorrect: `pingJob?.cancel()` is a cooperative cancellation signal — the old job may still be running a tiny moment, but `isPinging.clear()` before the new `launch` is idiomatic. The new job sets `isPinging = true` as its first action inside the coroutine body, so the false→true transition is well-ordered.

The review finding was verified against the actual code and rejected as a false positive.

### LaunchedEffect(key) Is Not a Race

Another review concern was that `LaunchedEffect(key)` in Compose could cause a race when `key` changes. This is also incorrect: `LaunchedEffect` guarantees that when the key changes, the previous effect's coroutine is cancelled before the new one starts. This is fundamental Compose semantics documented in the official API — not a potential race condition.

### UI Implementation

The ping button should use text rather than an icon to be self-explanatory:

```kotlin
val isPinging by viewModel.isPinging.collectAsStateWithLifecycle()

if (isPinging) {
    TextButton(onClick = { viewModel.cancelPing() }) {
        Text(stringResource(R.string.cancel))
    }
} else {
    TextButton(onClick = { viewModel.startPing(server.id) }) {
        Text(stringResource(R.string.test))
    }
}
```

## Related Concepts

- [[concepts/singbox-ping-abstractbean-deserialization]] - Companion: deserialization must be correct before ping results are meaningful
- [[concepts/viewmodel-stateflow-test-race]] - Related: StateFlow race conditions in ViewModel testing
- [[concepts/compose-launchedeffect-crash-invisibility]] - LaunchedEffect crash patterns in Compose
- [[concepts/viewmodel-polling-runtest-trap]] - Testing ViewModel coroutine loops

## Sources

- [[daily/2026-05-26.md]] - Session 13:59: `pingJob: Job?` stored in ViewModel for cancel; `isPinging.clear()` before new job confirmed correct (old job cancelled, not a race); `LaunchedEffect(key)` auto-cancel confirmed standard Compose; button changed from PlayArrow icon to TextButton "Тест"/"Отмена"; "Отмена" string already existed in strings.xml
- [[daily/2026-05-26.md]] - Session 16:22: subagent returned 2 false positives (isPinging.clear race + LaunchedEffect race); both verified against code and rejected; 1 real finding accepted (Exception.message null fallback)
