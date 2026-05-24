---
title: "Kotlin Coroutine Return Semantics"
aliases: [kotlin-return-in-lambda, coroutine-return-trap, non-local-return]
tags: [kotlin, coroutines, compiler]
sources:
  - "daily/2026-05-24.md"
created: 2026-05-24
updated: 2026-05-24
---

# Kotlin Coroutine Return Semantics

Return statements in Kotlin coroutine builders and higher-order functions follow strict rules that differ from regular function returns. Violations cause compile-time errors that are sometimes disguised as unrelated errors.

## Key Points

- `return` in expression body (`fun foo() = try { return X }`) is forbidden — must use block body
- `return@label` is valid only in lambdas, not in regular function bodies
- Non-local return in non-inline lambda (e.g., `coroutineScope { }`, `async { }`, `withContext { }`) is a compile error
- In `map { }`, `mapNotNull { }` — `return X` is non-local (returns from enclosing function); use expression form instead
- `coroutineScope { }` and `async { }` are NOT inline — `return@coroutineScope` is illegal

## Details

Kotlin distinguishes between local and non-local returns based on whether the lambda is `inline`. Standard library functions like `map`, `filter`, `mapNotNull` are inline, so `return` inside them is a non-local return from the enclosing function — often unintentional. Coroutine builders (`coroutineScope`, `async`, `withContext`, `launch`) are NOT inline, making any `return` inside them a compile error.

Expression body functions (`fun foo() = expression`) cannot contain `return` statements because there is no "return target" — the function returns the value of the expression directly. Converting to block body (`fun foo() { return expression }`) resolves this. The same applies to `= try { ... }` expression bodies.

The `return@label` labeled return syntax is exclusive to lambdas. Using `return@functionName` inside a regular function body (even inside a try-catch inside that function) is a syntax error.

## Common Patterns and Fixes

| Wrong | Right |
|-------|-------|
| `fun foo() = try { return null }` | `fun foo(): T? { return try { null } }` |
| `coroutineScope { return@coroutineScope x }` | `coroutineScope { x }` (last expression) |
| `async { return@async null }` | `async { null }` |
| `map { url -> return SingboxServer(...) }` | `map { url -> SingboxServer(...) }` |

## Related Concepts

- [[concepts/kotlin-expression-body-return-trap]] - Expression body specific trap
- [[concepts/runtest-uncompleted-coroutines-trap]] - Related coroutine test traps
- [[concepts/cascade-unresolved-import-masking]] - How these errors cascade

## Sources

- [[daily/2026-05-24.md]] - Discovered during singbox subscription parser implementation (parseVlessUrl, parseSingboxJson, SingboxPresetRepository.fetchAll)
