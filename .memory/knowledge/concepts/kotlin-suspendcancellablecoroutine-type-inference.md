---
title: "Kotlin 2.x suspendCancellableCoroutine Type Inference Trap"
aliases: [suspendcancellablecoroutine-generic, kotlin-resume-type-mismatch, coroutine-type-inference]
tags: [kotlin, coroutines, gotcha, compile-error]
sources:
  - "daily/2026-05-06.md"
created: 2026-05-06
updated: 2026-05-06
---

# Kotlin 2.x suspendCancellableCoroutine Type Inference Trap

In Kotlin 2.x, when `suspendCancellableCoroutine` is called without an explicit generic type parameter, the compiler infers `T` from the first `cont.resume(...)` call it encounters. If a second `cont.resume(...)` call passes a value of a different sealed subtype, the compiler rejects it with a type mismatch error. The fix is to always specify the explicit generic type: `suspendCancellableCoroutine<MyResultType> { ... }`.

## Key Points

- Kotlin 2.x tightened type inference for coroutine builders — `T` is now inferred from the first `resume` call, not from the union of all `resume` calls
- `cont.resume(Error(...))` as first call → `T` inferred as `Error` → second `cont.resume(Success(...))` fails to compile
- Applies to `suspendCancellableCoroutine`, `suspendCoroutine`, and similar builders whenever multiple `resume` calls pass different sealed class subtypes
- Fix: explicit generic parameter — `suspendCancellableCoroutine<GuestJwtResult> { cont -> ... }`
- Manifests as a CI compile failure, not a runtime error — easy to miss during local development if using an older Kotlin plugin

## Details

### The Inference Change

Kotlin 2.x introduced stricter type inference rules for lambda-returning functions. In `suspendCancellableCoroutine<T>`, the compiler must determine `T` from usage within the lambda. In Kotlin 1.x and early 2.x with older inference settings, the compiler could unify multiple `resume` argument types into their common supertype. In Kotlin 2.x strict mode, the compiler commits to `T` on the first observed `resume` call and requires subsequent calls to be the same type.

The affected pattern in Ozero's `RealUrnetworkAuthService`:

```kotlin
// BROKEN in Kotlin 2.x
suspendCancellableCoroutine { cont ->
    callback = { error ->
        cont.resume(GuestJwtResult.Error(error))  // T inferred as GuestJwtResult.Error
    }
    successCallback = { jwt ->
        cont.resume(GuestJwtResult.Success(jwt))  // Compile error: expected Error, got Success
    }
}

// CORRECT
suspendCancellableCoroutine<GuestJwtResult> { cont ->
    callback = { error ->
        cont.resume(GuestJwtResult.Error(error))  // OK: GuestJwtResult.Error is GuestJwtResult
    }
    successCallback = { jwt ->
        cont.resume(GuestJwtResult.Success(jwt))  // OK
    }
}
```

### Discovery Context

This error surfaced in Ozero's CI after a code review session added `suspendCancellableCoroutine` to `RealUrnetworkAuthService` for both `getGuestJwt` and `getClientJwt` methods. The error message from the Kotlin compiler was a type mismatch on the second `cont.resume` call — sufficient to identify the pattern once recognized, but initially confusing because the types (`Success` and `Error`) are both subtypes of the sealed class.

### Rule

Any `suspendCancellableCoroutine` block with more than one `cont.resume(...)` call for different sealed subtypes must have an explicit generic type parameter. This is a safe habit regardless of Kotlin version — explicit generics improve readability and eliminate future inference breakage on compiler upgrades.

## Related Concepts

- [[concepts/ci-workflow-discipline]] - The CI-only testing policy means this compile error was caught in CI, not locally
- [[concepts/viewmodel-stateflow-test-race]] - Another Kotlin concurrency trap in test code; similar pattern of non-obvious runtime/compile behavior in coroutine contexts

## Sources

- [[daily/2026-05-06.md]] - Session 09:30: Kotlin 2.x type mismatch in RealUrnetworkAuthService suspendCancellableCoroutine; fix = explicit `<GuestJwtResult>` / `<ClientJwtResult>` type params; CI fix commit 553331d
