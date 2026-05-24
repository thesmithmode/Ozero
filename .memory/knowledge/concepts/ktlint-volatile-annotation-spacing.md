---
title: "ktlint @Volatile Annotation Spacing Requirement"
aliases: [ktlint-annotation-spacing, volatile-annotation-blank-line, ktlint-declarations-spacing]
tags: [ktlint, kotlin, android, ci, annotations]
sources:
  - "daily/2026-05-24.md"
created: 2026-05-24
updated: 2026-05-24
---

# ktlint @Volatile Annotation Spacing Requirement

`@Volatile` (and other annotation-only lines such as `@GuardedBy`) on a field declaration require an empty line before them when they follow another declaration. Without the blank line, ktlint fails with: `Declarations and declarations with annotations should have an empty space between`.

## Key Points

- Any field annotated with a standalone annotation line (`@Volatile`, `@GuardedBy`, `@JvmField`, etc.) needs a blank line separating it from the preceding member
- ktlint rule: `spacing-between-declarations-with-annotations`
- The error fires even if the preceding member is also annotated — blank line is required between each annotated declaration
- CI fails in the `ktlintCheck` task; error is visible via `gh run view --log-failed`

## Details

### Pattern That Fails

```kotlin
class EngineWarp {
    private var handle: Int = 0
    @Volatile private var running: Boolean = false   // ktlint error here
}
```

### Correct Form

```kotlin
class EngineWarp {
    private var handle: Int = 0

    @Volatile private var running: Boolean = false   // blank line before annotation
}
```

### Discovery Context

During the `engine-warp` + `RealWarpSdkBridge` implementation (2026-05-24), several `@Volatile` fields were declared without blank lines separating them from preceding fields. The CI `ktlintCheck` task failed. Adding a blank line before each `@Volatile` (and other standalone annotation lines) resolved the issue.

### Scope

Applies to all annotation-prefixed declarations: fields, functions, classes. The rule is consistent — any annotated member that follows another member needs the blank line.

## Related Concepts

- [[concepts/ci-gradle-log-reading]] - How to read ktlint errors from CI
- [[concepts/ktlint-traps-ozero]] - Other ktlint traps in the Ozero project

## Sources

- [[daily/2026-05-24.md]] — EngineWarp + RealWarpSdkBridge had `@Volatile` fields without blank lines; CI `ktlintCheck` failed with annotation spacing error; fixed by adding blank lines before each annotated field
