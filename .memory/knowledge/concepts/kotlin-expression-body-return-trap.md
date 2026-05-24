---
title: "Kotlin Expression Body Return Trap"
aliases: [expression-body-return, return-in-expression-function, return-label-lambda]
tags: [kotlin, syntax, android]
sources:
  - "daily/2026-05-24.md"
created: 2026-05-24
updated: 2026-05-24
---

# Kotlin Expression Body Return Trap

In Kotlin, a function declared with expression body syntax (`fun foo() = expr`) prohibits `return` statements inside the expression. Attempting to use `return null` or `return@label` inside such a function body causes a compile error: `'return' is not allowed here`. The same constraint applies to `return` inside a non-inline lambda — `return@label` works only in inline function lambdas.

## Key Points

- `fun foo(): T? = try { ... return null ... }` → compile error; must convert to block body `fun foo(): T? { return try { ... null ... } }`
- `return@functionName` syntax in a regular function (not a lambda) is illegal — causes `'return' is not allowed here` compile error; only valid inside lambdas
- `return` inside a `map { }` lambda (which is inline) is a non-local return — exits the enclosing function, not just the lambda
- `return@map null` is the labeled return to exit only the lambda, keeping the surrounding function running
- `return@async null` inside an `async { }` block is valid because it labels the async coroutine builder's lambda
- Expression body functions defined as `fun foo() = try { ... }` cannot have any `return` or `return@label` inside — must rewrite as block body `fun foo() { return try { ... } }` or use `null` directly as the last expression

## Details

### Expression Body Trap

```kotlin
// WRONG — 'return' is not allowed here
private fun parseVlessUrl(url: String): SingboxServer? = try {
    val host = uri.host ?: return null   // compile error
    SingboxServer.Vless(host)
} catch (e: Exception) { null }

// CORRECT — block body allows return
private fun parseVlessUrl(url: String): SingboxServer? {
    return try {
        val host = uri.host ?: return null   // OK
        SingboxServer.Vless(host)
    } catch (e: Exception) { null }
}
```

### Lambda Non-Local Return Trap

```kotlin
// WRONG — non-local return exits the outer suspend function, not map
suspend fun fetchAll(): List<X> {
    return urls.map { url ->
        return fetchOne(url)   // exits fetchAll() entirely on first item!
    }
}

// CORRECT — labeled return exits only the lambda
suspend fun fetchAll(): List<X> {
    return urls.map { url ->
        fetchOne(url)          // expression, no return needed
    }
}
```

### `return@coroutineScope` Trap

`coroutineScope { }` is not an inline function. `return@coroutineScope` inside it is a non-local return into the calling function, which may not have a matching label. The fix is to not use `return@coroutineScope` and instead structure the logic to return the value naturally through the last expression.

### Discovery

This trap was encountered massively during the `engine-singbox` P4 implementation (2026-05-24): the `SingboxSubscriptionParser` and `SingboxPresetRepository` had dozens of expression body functions with `return` inside — all caused CI compile failures. The pattern was hard to catch through static reading because the syntax looks plausible in isolation.

## Related Concepts

- [[concepts/runtest-uncompleted-coroutines-trap]] - Other Kotlin coroutine/suspend traps
- [[concepts/collect-vs-collectlatest-restart-semantics]] - Kotlin Flow semantics adjacent
- [[concepts/ci-gradle-log-reading]] - How to diagnose these errors in CI

## Sources

- [[daily/2026-05-24.md]] — Session 19:13: multiple CI failures in engine-singbox due to `return` in expression body functions (`parseVlessUrl`, `parseShadowsocksUrl`, etc.) and `return@coroutineScope` in `SingboxPresetRepository.fetchAll()`; fixed by converting to block body and removing non-local returns; `return@label` in regular function bodies also caused `'return' is not allowed here` — only valid inside lambdas
