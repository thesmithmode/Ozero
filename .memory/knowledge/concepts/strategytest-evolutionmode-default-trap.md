---
title: "StrategyTestSettings evolutionMode=true Default Trap"
aliases: [evolutionmode-default-trap, strategytest-default-evolution, runloop-runs-evolution]
tags: [byedpi, testing, gotcha, genetic-algorithm]
sources:
  - "daily/2026-05-13.md"
created: 2026-05-13
updated: 2026-05-13
---

# StrategyTestSettings evolutionMode=true Default Trap

`StrategyTestSettings` has `evolutionMode=true` as its default value. Tests that call `runLoop*` scenarios without explicitly setting `evolutionMode=false` in their settings object will inadvertently call `runEvolution` instead of `runLoop`. The result: `callCount` assertions on the loop path will fail because the code path taken is the evolution path, which has different invocation counts and behavior.

## Key Points

- `StrategyTestSettings.evolutionMode` defaults to `true`
- Any test constructing `StrategyTestSettings()` (or using the default) will route to `runEvolution`, not `runLoop`
- Tests asserting `callCount` on loop-specific behavior see wrong counts — the loop was never called
- Fix: all `runLoop` test scenarios must explicitly set `evolutionMode=false` in `StrategyTestSettings`
- Tests for evolution mode are correct as-is (they rely on the default)

## Details

### The Default Trap

The `StrategyTestSettings` data class was designed to represent the current production defaults, where evolution mode is the preferred strategy testing approach. The default `evolutionMode=true` is intentional for production but creates a trap for tests:

```kotlin
data class StrategyTestSettings(
    val populationSize: Int = 25,
    val maxGenerations: Int = 10,
    val evolutionMode: Boolean = true,  // default = evolution, not loop
    // ...
)
```

A test that wants to test the fixed-list loop strategy:

```kotlin
@Test fun `runLoop calls each strategy once`() = runTest {
    val settings = StrategyTestSettings()  // evolutionMode=true — BUG
    vm.startTest(settings)
    assertEquals(75, callCount)  // FAILS — runEvolution was called, not runLoop
}
```

The test asserts `callCount == 75` (one call per strategy in the fixed list), but `runEvolution` was called instead, which calls `byeDpiEngine.start()` a different number of times (population × generations × evaluation passes). The assertion fails with a count far from 75.

### The Fix

Every test targeting `runLoop` behavior must explicitly opt out of evolution mode:

```kotlin
@Test fun `runLoop calls each strategy once`() = runTest {
    val settings = StrategyTestSettings(evolutionMode = false)  // explicit
    vm.startTest(settings)
    assertEquals(75, callCount)  // now correct
}
```

Tests targeting `runEvolution` behavior can rely on the default:

```kotlin
@Test fun `runEvolution respects targetFitness`() = runTest {
    val settings = StrategyTestSettings()  // evolutionMode=true by default — correct
    // ...
}
```

### Why the Default Is Right for Production

The default `evolutionMode=true` reflects the product decision that genetic evolution is the primary strategy testing mode. Users who open the strategy test screen get evolution by default. The fixed-list loop mode is a secondary/fallback option, requiring explicit user selection. Having the production default in `StrategyTestSettings` means the ViewModel correctly represents the default UI state. Tests are the callsite that must be explicit.

### Detection

If a `runLoop` test passes with an unexpected `callCount`, suspect this trap. Add a log assertion or a sentinel test that verifies `evolutionMode=false` in all settings objects used by loop tests. Alternatively, add a `@Suppress` comment to each loop test noting the explicit override — this makes the intent visible in code review.

## Related Concepts

- [[concepts/genetic-strategy-evolution]] - `runEvolution` implementation and `StrategyTestSettings` parameter context
- [[concepts/byedpi-strategy-runtime-disconnect]] - Fixed-list `runLoop` architecture
- [[concepts/ci-workflow-discipline]] - CI is the first place this manifests as a failing test

## Sources

- [[daily/2026-05-13.md]] - `StrategyTestSettings.evolutionMode=true` default; `runLoop` tests without `evolutionMode=false` routed to `runEvolution`; wrong `callCount`; fix = explicit `evolutionMode=false` in all loop test scenarios
