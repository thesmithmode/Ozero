---
title: "ViewModel StateFlow Test Race Condition"
aliases: [stateflow-test-race, viewmodel-beforeeach-race, compose-test-ordering]
tags: [android, testing, compose, gotcha, viewmodel]
sources:
  - "daily/2026-05-02.md"
created: 2026-05-02
updated: 2026-05-02
---

# ViewModel StateFlow Test Race Condition

When a ViewModel is instantiated in `@BeforeEach` and its constructor launches a coroutine that collects a `StateFlow` from a repository/store, the coroutine may execute before the test body has a chance to call `store.setRaw(...)` to set up the desired initial state. This produces a race condition where the ViewModel observes `null` or a default value, triggers auto-behavior (like auto-selection), and the test sees unexpected state.

## Key Points

- ViewModel created in `@BeforeEach` → constructor coroutine fires before test body → reads uninitialized store state
- Symptom: test expects specific initial state but ViewModel has already triggered auto-logic based on null/default
- Fix: create the ViewModel **inside the test** AFTER calling `store.setRaw(...)` to set the desired state
- This affects any ViewModel that collects `StateFlow`/`Flow` in `init {}` block or constructor-launched coroutine
- Particularly insidious with `WarpEngineSettingsViewModel` and similar ViewModels that auto-trigger config selection on first emission

## Details

### The Race Mechanism

JUnit 5 lifecycle:

```
@BeforeEach setUp() {
    store = FakeSettingsStore()        // empty store
    viewModel = MyViewModel(store)     // constructor launches: store.settings.collect { ... }
    // coroutine is now scheduled on test dispatcher
}

@Test fun testSomething() {
    store.setRaw(specificSettings)     // TOO LATE — VM already collected null/default
    // viewModel has already reacted to null state
}
```

The `collect` coroutine in the ViewModel's `init` block is scheduled immediately when the ViewModel is constructed. On the `UnconfinedTestDispatcher` (common in Compose/ViewModel tests), it executes eagerly — even before `setUp()` returns. By the time the test body calls `store.setRaw()`, the ViewModel has already seen the initial `null` emission and acted on it.

### The WarpEngineSettingsViewModel Case

In the Ozero v0.0.2-5 CI cycle, `WarpEngineSettingsViewModelTest` failed because the ViewModel's constructor collected the WARP config store. On the first emission (before test setup), the store contained no config, so the ViewModel auto-triggered mirror selection logic. When the test then set a specific config, the ViewModel was in an inconsistent state — it had already started an async operation based on the null initial state.

The fix: move ViewModel creation inside each test, after store state is configured:

```
@Test fun testWithSpecificConfig() {
    store.setRaw(WarpConfig(publicKey = "test-key"))
    val viewModel = WarpEngineSettingsViewModel(store)  // now sees correct initial state
    // assertions work correctly
}
```

### General Rule

For any ViewModel that observes external state in its constructor:

1. **Never** create the ViewModel in `@BeforeEach` if tests need different initial states
2. **Always** set up store/repository state BEFORE constructing the ViewModel
3. If sharing a ViewModel across tests is necessary (e.g., expensive construction), use a lazy init pattern or a test-specific factory method

This pattern was added to Ozero's `CLAUDE.md` as a permanent testing rule after the CI failure.

## Related Concepts

- [[concepts/junit-platform-silent-skip]] - Another testing gotcha where infrastructure issues produce misleading results
- [[concepts/ci-workflow-discipline]] - The CI-only testing workflow where this race was discovered and fixed

## Sources

- [[daily/2026-05-02.md]] - Session 10:15: `WarpEngineSettingsViewModelTest` race found during CI fix cycle; VM created in setUp before store.setRaw → auto-trigger; fix = create VM inside test after state setup
