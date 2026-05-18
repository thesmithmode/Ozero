---
title: "Kotlin by-lazy Cross-Reference Recursive Type Inference"
aliases: [lazy-cross-reference, lazy-type-inference, lazy-explicit-type]
tags: [kotlin, gotcha, compile-error, android]
sources:
  - "daily/2026-05-16.md"
created: 2026-05-16
updated: 2026-05-16
---

# Kotlin by-lazy Cross-Reference Recursive Type Inference

When multiple `by lazy` properties in the same class reference each other (directly or transitively), the Kotlin compiler's type inference may fail with a recursive type resolution error. Each lazy property's inferred type depends on another lazy property whose type is not yet resolved. The fix is explicit type annotations on all `by lazy` properties in the cross-referencing chain.

## Key Points

- `by lazy` without explicit type relies on compiler inferring type from the lambda body
- When lazy A references lazy B and lazy B references lazy A (or transitively), inference enters a cycle
- Compile error is cryptic: often reported as "type checking has run into a recursive problem" or similar
- Fix: add explicit type annotation to every `by lazy` in the cycle: `val foo: FooType by lazy { ... }`
- In Ozero: 6 `by lazy` properties in `OzeroVpnService` cross-referenced each other after coordinator extraction — all required explicit types

## Details

### The Inference Cycle

Kotlin's `by lazy { }` delegate infers the property type from the return type of the lambda. When the lambda body references another `by lazy` property, the compiler must first resolve that property's type. If there's a circular dependency:

```kotlin
// BROKEN: recursive type inference
val startSequence by lazy { StartSequenceCoordinator(watchdog) }
val watchdog by lazy { EngineWatchdogCoordinator(startSequence) }
```

The compiler tries to infer `startSequence`'s type → needs `StartSequenceCoordinator` constructor → sees parameter `watchdog` → needs `watchdog`'s type → needs `EngineWatchdogCoordinator` constructor → sees parameter `startSequence` → cycle.

### The Fix

```kotlin
// CORRECT: explicit types break the inference cycle
val startSequence: StartSequenceCoordinator by lazy { StartSequenceCoordinator(watchdog) }
val watchdog: EngineWatchdogCoordinator by lazy { EngineWatchdogCoordinator(startSequence) }
```

With explicit types, the compiler doesn't need to infer anything — it knows the types statically and only evaluates the lambda bodies at runtime (when `by lazy` triggers first access).

### The Ozero Discovery

During the `OzeroVpnService` decomposition, 5 coordinator properties were added as `by lazy`:
- `notificationHelper`
- `tunBuilderHelper`
- `engineWatchdog`
- `startSequence`
- `shutdownCoordinator`

Several coordinators referenced each other (e.g., `startSequence` needed `engineWatchdog` for failure handling, `shutdownCoordinator` needed the TUN fd from `startSequence`). Without explicit types, CI failed with a compile error on the cross-referencing chain. Adding explicit type annotations to all 6 `by lazy` properties resolved the cycle.

### Prevention Rule

When a class has 3+ `by lazy` properties, add explicit types proactively. The cost is a few characters per declaration; the benefit is avoiding a cryptic compile error that appears only when a new cross-reference is added. This is especially important in Android service classes where lazy initialization is used to defer heavy object creation past `onCreate`.

## Related Concepts

- [[concepts/kotlin-suspendcancellablecoroutine-type-inference]] - Another Kotlin type inference gotcha where explicit generics are required to break inference ambiguity
- [[concepts/vpnservice-god-object-decomposition]] - The refactoring context where this cross-reference cycle was discovered

## Sources

- [[daily/2026-05-16.md]] - Session 15:46: 6 `by lazy` properties in OzeroVpnService cross-referenced each other → recursive type inference compile fail; fix = explicit type on all lazy props
