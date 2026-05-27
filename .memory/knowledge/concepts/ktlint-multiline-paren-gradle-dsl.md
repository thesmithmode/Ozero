---
title: "ktlint: Multi-line Grouping Paren in Kotlin DSL"
aliases: [ktlint-paren-newline, gradle-dsl-ktlint-paren, multiline-maxof-ktlint]
tags: [kotlin, ktlint, gradle, build]
sources:
  - "daily/2026-05-25.md"
created: 2026-05-25
updated: 2026-05-25
---

# ktlint: Multi-line Grouping Paren in Kotlin DSL

ktlint enforces that multi-line expressions wrapped in grouping parentheses must have a newline immediately after `(` and immediately before `)`. Violations occur in Gradle Kotlin DSL when a function call argument spans multiple lines inside `maxOf()` or similar wrappers. The fix is to extract the multi-line sub-expression into a separate `val`.

## Key Points

- Error: `Missing newline after "("` at the opening paren of a multi-line expression
- Error: `Missing newline before ")"` at the closing paren
- Triggered when `providers.exec { ... }.standardOutput.asText.get()` is inlined inside `maxOf()`
- Fix: extract the multi-line part to `val gitCommitCount: Int = providers.exec { ... }...toIntOrNull() ?: 1` — then `maxOf(gitCommitCount + offset, floor)` is single-line and valid
- Applies to Gradle `build.gradle.kts` files as well as regular Kotlin source

## Details

### The Failing Pattern

```kotlin
// app/build.gradle.kts (broken — lines 11 and 14 flagged)
val gitVersionCode: Int = maxOf(
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
    }.standardOutput.asText.get().trim().toIntOrNull() ?: 1 + 1500,
    2000
)
```

ktlint reports:
```
app/build.gradle.kts:11:6 Missing newline after "("
app/build.gradle.kts:14:59 Missing newline before ")"
```

The root violation is that the opening `(` of the outer `maxOf(` call is followed by a block literal (the `providers.exec { }` lambda), and ktlint requires a newline before the first argument in this case.

### The Fix Pattern

```kotlin
// app/build.gradle.kts (fixed)
val versionCodeOffset = 1500
val versionCodeFloor = 2000
val gitCommitCount: Int = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
}.standardOutput.asText.get().trim().toIntOrNull() ?: 1
val gitVersionCode: Int = maxOf(gitCommitCount + versionCodeOffset, versionCodeFloor)
```

By extracting `providers.exec { ... }` into its own `val`, the `maxOf(...)` call becomes a simple single-line expression that needs no special newline handling.

### When This Surfaces

This pattern surfaces most often when a versionCode calculation is made more defensive (adding `max` or a floor), and the exec block is inlined rather than extracted. The ktlint check `:app:ktlintKotlinScriptCheck` (not `:app:ktlintCheck`) covers `build.gradle.kts` files.

## Related Concepts

- [[concepts/versioncode-git-history-rewrite-regression]] - The versionCode defense that triggered this ktlint trap
- [[concepts/ci-workflow-discipline]] - ktlint gates in CI pipeline
- [[concepts/ktlint-volatile-annotation-spacing]] - Another non-obvious ktlint rule

## Sources

- [[daily/2026-05-25.md]] — CI ktlintKotlinScriptCheck failure on `app/build.gradle.kts:11:6 Missing newline after "("` and `:14:59 Missing newline before ")"` when adding `maxOf(providers.exec { ... }, floor)` for versionCode defense; fix: extract exec block to separate `val gitCommitCount`
