---
title: "Kotlin Action Lambda Unused Parameter Compile Error"
aliases: [kotlin-action-unused-param, action-lambda-shadowed, gradle-action-lambda]
tags: [kotlin, gotcha, testing, gradle, compile]
sources:
  - "daily/2026-05-16 (1).md"
created: 2026-05-16
updated: 2026-05-16
---

# Kotlin Action Lambda Unused Parameter Compile Error

In Kotlin, `Action<T> { t -> ... }` where `t` is declared but not used inside the lambda body produces a compile error (or warning-as-error in strict mode): "Parameter 't' is never used." This commonly appears in Gradle test configurations using `Action<Task>` lambdas and in MockK `answers {}` blocks. The fix is either `Action { }` (omit the parameter entirely) or `Action { _ -> }` (explicit unused marker).

## Key Points

- `Action { t -> doSomething() }` without using `t` → compile error: "Parameter 't' is never used"
- Kotlin SAM (Single Abstract Method) lambda parameters must either be used or explicitly discarded with `_`
- `Action { }` — correct when the parameter is truly not needed
- `Action { _ -> }` — correct when the type must be explicit for overload resolution but value unused
- Applies to: Gradle `Action<Task>`, `Action<Configuration>`, MockK `answers { it -> }`, any SAM interface

## Details

### The Trap

Kotlin enforces that lambda parameters are used. When adapting Java functional interfaces (`Action<T>`, `Consumer<T>`, `Function<T,R>`), the parameter appears in the lambda signature:

```kotlin
// BROKEN: t declared but unused
tasks.register("myTask") { t ->
    t.doFirst { }  // if t is not used, this is fine — but if t IS declared without use:
}

// Also BROKEN in tests:
val slot = slot<String>()
every { mock.call(capture(slot)) } answers { answer ->
    "result"  // 'answer' is declared but never used
}
```

In Gradle's Kotlin DSL, task registration frequently uses `Action<Task>` where the task object itself isn't needed inside the configuration block:

```kotlin
// BROKEN in DownloadBinaryTaskTest:
tasks.register("test", DownloadBinaryTask::class.java, Action { t ->
    // t never used — compile error
})

// CORRECT:
tasks.register("test", DownloadBinaryTask::class.java, Action {
    // parameter omitted entirely
})
```

### Discovery Context

Found in `DownloadBinaryTaskTest` during CI fix session 11:45. The test registered a Gradle task with `Action { t -> }` where `t` was never referenced. Kotlin strict compilation rejected this with an unused parameter error. The fix was `Action { }` — removing the parameter name.

### Relationship to Shadowing

The error message may say "shadowed" rather than "unused" when the lambda parameter name matches an outer scope variable:

```kotlin
val moduleDir = File("...")  // outer scope
tasks.register("build") { moduleDir ->  // shadows outer moduleDir
    // if inner moduleDir is unused, error: "Parameter 'moduleDir' shadows outer variable"
}
```

In Gradle `register` lambdas, the `Task` receiver parameter name may shadow test fixture variables. Rename the outer variable or use `_` for the lambda parameter to resolve.

## Related Concepts

- [[concepts/kotlin-trailing-lambda-parameter-trap]] - Related Kotlin lambda resolution trap with SAM interfaces
- [[concepts/junit5-trailing-lambda-assertion-trap]] - Another lambda parameter misunderstanding in test code

## Sources

- [[daily/2026-05-16 (1).md]] - Session 11:45: `DownloadBinaryTaskTest` used `Action { t -> }` with `t` unused → compile error; fix: `Action { }`; also: `moduleDir` (Task property) shadowed local test variable → rename
