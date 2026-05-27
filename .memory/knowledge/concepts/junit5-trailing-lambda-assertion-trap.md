---
title: "JUnit5 assertTrue Trailing Lambda Message Trap"
aliases: [junit5-trailing-lambda, asserttrue-message-slot, kotlin-junit5-lambda]
tags: [kotlin, testing, gotcha, junit5, ci]
sources:
  - "daily/2026-05-16 (1).md"
created: 2026-05-16
updated: 2026-05-16
---

# JUnit5 assertTrue Trailing Lambda Message Trap

In JUnit5 with Kotlin, `assertTrue(condition) { "message" }` compiles but places the trailing lambda into the **failure message supplier** slot, not as an assertion. The assertion itself is the `condition` expression. This means `assertTrue(false) { "error context" }` fails with the supplied message, but `assertTrue(true) { "never checked inner logic" }` passes regardless of whatever is inside the lambda. The correct idiom for a plain message is `assertTrue(condition, "message")`.

## Key Points

- JUnit5 `assertTrue` has overloads: `assertTrue(Boolean)`, `assertTrue(Boolean, String)`, `assertTrue(Boolean, () -> String)`
- The third overload accepts a trailing lambda as the **message supplier** — not an assertion body
- Kotlin's trailing-lambda syntax makes `assertTrue(expr) { "msg" }` syntactically identical to passing a lambda, not a nested assertion
- Silent correctness risk: `assertTrue(calculateResult()) { complexAssertion() }` — `complexAssertion()` is only called on failure, not evaluated as a test
- Fix: use `assertTrue(condition, "static message")` for messages; for multi-step assertions use separate `assert*` calls

## Details

### The Ambiguity

JUnit5 in Kotlin exposes a common overload resolution trap. Consider:

```kotlin
// INTENT: assert condition, with "msg" as context
assertTrue(myCondition) { "msg" }

// ACTUAL: Kotlin resolves to assertTrue(Boolean, () -> String)
// The lambda is the lazy message supplier — called only if condition is false
// This is equivalent to:
assertTrue(myCondition, supplier = { "msg" })
```

The behavior is semantically correct when the lambda contains just a string literal — the test passes/fails on `myCondition`. The trap activates when a developer mistakenly puts assertion logic inside the lambda body:

```kotlin
// BROKEN: stateFlow.value check never runs during normal execution
assertTrue(someFlag) {
    assertEquals(42, stateFlow.value)  // only called on failure!
    "expected 42"
}

// CORRECT: two explicit assertions
assertTrue(someFlag)
assertEquals(42, stateFlow.value)
```

### The Ozero Discovery

In session 11:45, `RealUrnetworkSdkBridgeContractTest` used `assertTrue(expr) { "msg" }` expecting the lambda to contain assertion logic. The test always passed because the lambda body was only evaluated as a message formatter when the outer `assertTrue` failed, not as additional assertions. The fix: split into separate `assert*` calls or use `assertTrue(expr, "static message")`.

### Detection

This trap is hard to spot in code review because the syntax looks natural to Kotlin developers familiar with lambda-last conventions. The tell: if the lambda body inside `assertTrue { ... }` contains `assertEquals`, `assertNotNull`, or other assertion calls, those assertions are lazy message suppliers, not actual test assertions. They only execute when the outer condition is already false.

## Related Concepts

- [[concepts/test-tautology-always-green]] - Broader category of tests that pass regardless of implementation correctness
- [[concepts/sentinel-anchor-substringafter-trap]] - Another silent-pass trap: code compiles and runs but doesn't verify what was intended

## Sources

- [[daily/2026-05-16 (1).md]] - Session 11:45: `assertTrue(expr) { "msg" }` — trailing lambda goes to message supplier slot, not assertion body; fix: `assertTrue(expr, "msg")`
