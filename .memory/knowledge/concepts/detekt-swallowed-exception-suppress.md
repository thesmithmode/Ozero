---
title: "detekt SwallowedException: By-Design Catch Pattern"
aliases: [swallowed-exception-suppress, detekt-crypto-catch, by-design-exception-swallow]
tags: [kotlin, detekt, testing, security, gotcha]
sources:
  - "daily/2026-05-16 (1).md"
created: 2026-05-16
updated: 2026-05-16
---

# detekt SwallowedException: By-Design Catch Pattern

detekt's `SwallowedException` rule fires when a `catch (e: Exception)` block does not reference `e` in its body. This is often a legitimate smell (lost stack trace), but some patterns intentionally swallow exceptions by design — particularly crypto verifiers and boolean-returning validators where the exception itself is not the signal, only the outcome. The correct response is `@Suppress("SwallowedException")` with a comment, not inventing artificial consumers of `e`.

## Key Points

- `catch (e: Exception) { return false }` triggers detekt `SwallowedException` because `e` is never used
- For crypto verification, this is by design: signature failure IS the return value; the exception type doesn't matter
- Do NOT create artificial consumers (`logger.d(e.message)`, `VerifyFailureMarker.touch(e)`) — adds noise and fake dependencies
- Use `@Suppress("SwallowedException")` with a brief inline comment explaining the design intent
- Never catch `Throwable` even to suppress — `OOM`, `StackOverflowError` must propagate; only `catch (e: Exception)` is safe to suppress

## Details

### The Design Pattern

Boolean-returning verifiers are a canonical case where exception swallowing is correct:

```kotlin
fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
    return try {
        val sig = Signature.getInstance("Ed25519")
        sig.initVerify(parseKey(publicKey))
        sig.update(data)
        sig.verify(signature)  // returns Boolean
    } catch (e: SignatureException) {
        false
    } catch (e: InvalidKeyException) {
        false
    } catch (e: Exception) {  // detekt: SwallowedException
        false
    }
}
```

The function contract is clear: `true` = valid, `false` = invalid or error. The caller does not need to distinguish "signature mismatch" from "corrupted key bytes" — both mean "reject." Logging `e` at this level creates noise (every attempted-forge triggers a stack trace).

### The Wrong Fix

A common mistake is inventing an artificial consumer to silence detekt:

```kotlin
// WRONG: fake consumer, adds noise
} catch (e: Exception) {
    VerifyFailureMarker.touch(e)  // non-existent class!
    return false
}

// ALSO WRONG: unexpected logging in security layer
} catch (e: Exception) {
    Log.d(TAG, "verify failed: ${e.message}")  // leaks internal crypto details
    return false
}
```

The `VerifyFailureMarker` approach creates a dependency on a non-existent class. The logging approach exposes internal state.

### The Correct Fix

```kotlin
@Suppress("SwallowedException")  // by design: any exception = verification failure
} catch (e: Exception) {
    false
}
```

The suppress annotation documents the intentionality. Code reviewers see the annotation and understand the design, rather than seeing `e` unused and questioning whether it was an accident.

### Catching Throwable

Never extend the catch to `Throwable` even with suppression:

```kotlin
// WRONG: catches OOM, StackOverflow, etc.
} catch (t: Throwable) {
    false
}
```

`Throwable` includes `Error` subclasses (`OutOfMemoryError`, `StackOverflowError`, `VirtualMachineError`) that represent unrecoverable JVM states. These must propagate so the runtime can handle them (abort, GC, etc.). `Exception` is safe to catch-and-suppress; `Throwable` is not.

## Related Concepts

- [[concepts/singbox-aidl-async-error-swallow]] - Problematic exception swallowing in AIDL stubs where errors ARE meaningful and must not be dropped
- [[concepts/sentinel-protecting-bug-trap]] - Suppressing warnings/errors incorrectly; similar category of "making CI green the wrong way"
- [[concepts/detekt-ratchet-desync-after-refactor]] - Other detekt suppressions that should be structural fixes rather than threshold bumps

## Sources

- [[daily/2026-05-16 (1).md]] - Session 16:08: `Ed25519Verifier.verify` caught exceptions without using `e` → detekt `SwallowedException`; wrong fix attempted (`VerifyFailureMarker.touch(e)` — non-existent class); correct fix: `@Suppress("SwallowedException")` on by-design crypto catch
