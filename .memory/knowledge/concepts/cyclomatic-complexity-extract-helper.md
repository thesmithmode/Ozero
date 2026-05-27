---
title: "Cyclomatic Complexity Threshold: Extract Helper Over Bump"
aliases: [cc-extract-helper, cyclomatic-complexity-fix, detekt-cc-threshold]
tags: [kotlin, detekt, ci, architecture, gotcha]
sources:
  - "daily/2026-05-15 (1).md"
created: 2026-05-15
updated: 2026-05-15
---

# Cyclomatic Complexity Threshold: Extract Helper Over Bump

Detekt's `CyclomaticComplexMethod` threshold (default 15, Ozero project: 25) fails the CI gate when any single function's cyclomatic complexity reaches or exceeds the configured value. Adding a single `if` branch to a large function already near the threshold is enough to trigger a failure. The correct fix is to extract a private helper function, not to raise the threshold.

## Key Points

- Detekt CC threshold of 25 means: fail at exactly 25. A function at CC=24 fails if one branch is added
- Adding a `no-plugins-fallback` path to `OzeroVpnService.runStartSequence` (CC 24 → 26) triggered the CI failure in session 15:02
- Fix: extract `targetForUi` as a `private fun` from `runStartSequence` — reduces CC of the parent, isolated logic is now testable independently
- NEVER bump the threshold to accommodate a single function — forces global threshold rise affecting all other functions
- Pattern applies to all static analysis threshold violations: LongParameterList, TooManyFunctions, etc. — decompose the SUT, never widen the gate

## Details

### Why Threshold Bumps Are Wrong

Detekt thresholds are project-wide constants in `detekt.yml`. Bumping `CyclomaticComplexMethod.threshold` from 25 to 27 to accommodate `runStartSequence` removes the safety net for every other function in the project. A function at CC=26 that was previously caught by the gate now passes silently.

The threshold exists precisely to force extraction. When a function is complex enough to hit the threshold, it is complex enough to decompose. The complexity is usually the signal — the function has too many concerns.

### The Extraction Pattern

When a function hits the CC threshold, identify a coherent sub-computation inside it that can stand alone:

```kotlin
// BEFORE: runStartSequence has CC=26 because it computes targetForUi inline
fun runStartSequence(engine: Engine): Result {
    // ... 20 lines ...
    val target = when {
        engine is SocksEngine && upstream != null -> UiTarget.Socks(upstream)
        engine is DirectEngine -> UiTarget.Direct
        else -> UiTarget.Default
    }
    // ... 10 more lines using target ...
}

// AFTER: extracted helper, runStartSequence CC reduced by 3
private fun targetForUi(engine: Engine, upstream: Upstream?): UiTarget = when {
    engine is SocksEngine && upstream != null -> UiTarget.Socks(upstream)
    engine is DirectEngine -> UiTarget.Direct
    else -> UiTarget.Default
}
```

The extracted function is:
1. Private — no API surface added
2. Independently testable — unit tests can call `targetForUi` directly
3. Named — the `when` block's intent is now documented by the function name

### coroutineContext Import Regression

In the same CI failure session (15:02), `kotlin.coroutines.coroutineContext` was lost as an import after T-56 refactored a suspend function. This import is required in suspend contexts that reference `coroutineContext` directly (e.g., for `Job` introspection). The import is non-obvious because `coroutineContext` reads as a property access but is actually an extension on `CoroutineScope` from `kotlin.coroutines`. Refactoring suspend functions into or out of `suspend fun` vs lambda contexts can silently drop this import.

Detection: `Unresolved reference: coroutineContext` compile error, but only in suspend contexts that use it directly.

## Related Concepts

- [[concepts/detekt-ratchet-desync-after-refactor]] — adjacent pattern: threshold drift after refactor; this article covers the active trigger (new code hits threshold), that article covers the passive drift (refactor shifts existing metrics)
- [[concepts/detekt-toomany-functions-semantics]] — another detekt threshold interpretation trap: `>=N` semantics; same principle applies (decompose vs bump)
- [[concepts/ci-workflow-discipline]] — CI gate failures including static analysis; CC failure is a gate failure that blocks merge

## Sources

- [[daily/2026-05-15 (1).md]] - Session 15:02: `CyclomaticComplexMethod 26` in `OzeroVpnService.runStartSequence` after no-plugins-fallback addition; fix = extract `targetForUi` private fun; also: `kotlin.coroutines.coroutineContext` import lost in T-56 suspend refactor → CI compile error
