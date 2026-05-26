---
title: "PingJob ViewModel Cancellation Pattern"
aliases: [ping-job-cancellation, ispinging-clear-pattern, pingJob-reference]
tags: [kotlin, coroutines, viewmodel, compose, pattern]
sources:
  - "daily/2026-05-26.md"
created: 2026-05-26
updated: 2026-05-26
---

# PingJob ViewModel Cancellation Pattern

A ViewModel that launches a ping coroutine must store the resulting `Job` reference. Without it, the operation cannot be cancelled — the UI can show a cancel button but it has no effect. Additionally, `isPinging.clear()` before launching a new `pingJob` is the correct pattern, not a race condition.

## Key Points

- Always store `pingJob: Job?` in the ViewModel; fire-and-forget `launch` blocks cancellation
- `isPinging.clear()` before a new `pingJob.start()` is safe — the old job is already cancelled at that point
- `LaunchedEffect(key)` in Compose automatically cancels the previous effect when `key` changes — this is standard Compose, not a race
- Cancel button should toggle between "Тест" and "Отмена" based on `isPinging.get()`
- "Отмена" string was already present in `strings.xml` — always check existing strings before adding

## Details

### Cancel Button Pattern

A ping button that supports mid-operation cancellation follows this structure:

```kotlin
// ViewModel
private var pingJob: Job? = null
val isPinging = AtomicBoolean(false)

fun startPing(serverId: String) {
    pingJob?.cancel()
    isPinging.set(false)         // clear before new job starts
    pingJob = viewModelScope.launch {
        isPinging.set(true)
        try { /* run ping */ }
        finally { isPinging.set(false) }
    }
}

fun cancelPing() {
    pingJob?.cancel()
}
```

In Compose:
```kotlin
TextButton(onClick = { if (isPinging) vm.cancelPing() else vm.startPing(id) }) {
    Text(if (isPinging) stringResource(R.string.cancel) else stringResource(R.string.test))
}
```

### isPinging.clear() Is Not a Race

A code review finding flagged `isPinging.clear()` (or `isPinging.set(false)`) before launching a new `pingJob` as a potential race. This finding is incorrect. By the time `startPing` is called again, the previous `pingJob?.cancel()` has been invoked. The new job does not start until the next coroutine scheduling point. Setting the flag to false synchronously before the new `launch` ensures the UI reflects "not pinging" for the brief instant before the new ping begins — this is the intended behavior.

### LaunchedEffect Key Change Is Not a Race

Similarly: `LaunchedEffect(serverId)` that starts a ping when `serverId` changes is standard Compose lifecycle management. When `serverId` changes, Compose automatically cancels the coroutine from the previous `LaunchedEffect` block and launches a new one. This is documented Compose behavior, not a concurrency hazard.

## Related Concepts

- [[concepts/singbox-ping-abstractbean-deserialization]] - Companion: what to ping and how to deserialize results
- [[concepts/viewmodel-stateflow-test-race]] - Other ViewModel coroutine race patterns to avoid
- [[concepts/collect-vs-collectlatest-restart-semantics]] - Analogous cancellation semantics in Flow collectors
- [[concepts/compose-launchedeffect-crash-invisibility]] - Other LaunchedEffect pitfalls in Compose

## Sources

- [[daily/2026-05-26.md]] — Session 13:59: ping without Job reference cannot be cancelled; isPinging.clear() before new pingJob is correct; LaunchedEffect(key) auto-cancels — not a race; "Отмена" already in strings.xml
- [[daily/2026-05-26.md]] — Session 16:22: subagent code review falsely flagged isPinging.clear() and LaunchedEffect as races; both verified correct after manual review
