---
title: "ktlint Brace Symmetry: if/else Must Match"
aliases: [ktlint-brace-asymmetry, if-else-brace-rule, ktlint-braces]
tags: [kotlin, ktlint, style, gotcha]
sources:
  - "daily/2026-05-14.md"
created: 2026-05-14
updated: 2026-05-14
---

# ktlint Brace Symmetry: if/else Must Match

ktlint enforces brace symmetry in `if/else` expressions: if the `if`-branch uses curly braces `{ }`, the `else`-branch must also use curly braces. A braceless `else` after a braced `if` is a style error. The rule applies in both directions — single-line and multi-line branches must match.

## Key Points

- ktlint rule: if `if`-branch has `{}`, `else`-branch must also have `{}`; asymmetric bracing fails lint
- Common pattern that fails: `if (cond) { expr } else null` — the `else null` needs braces: `else { null }`
- Same applies to `else if` chains: any branch with `{}` forces braces on all other branches
- Violation appears as a CI lint error, not a compile error — tests may still pass locally until lint runs
- Also applies to single-expression suspend functions: `suspend fun foo() = bar()` is fine, but `suspend fun foo() { return bar() }` requires full block style consistently

## Details

### The Symmetry Rule

ktlint enforces that `if/else` branches have consistent brace usage. The rule is asymmetric in its origin: braces are required if ANY branch uses them. Once one branch needs braces (e.g., because it contains multiple statements), all other branches must also wrap their expression in `{ }`.

```kotlin
// BROKEN: asymmetric braces
if (condition) {
    doSomething()
} else null

// FIXED: symmetric braces
if (condition) {
    doSomething()
} else {
    null
}
```

### Ozero feat/mtg Incident (2026-05-14)

After merging `feat/mtg` (engine-telegram), CI failed repeatedly on ktlint with multiple violations in the new engine code. Among the violations was the `} else null` pattern used in several places where the `if`-branch performed an operation in a block but the `else` case returned a simple value.

The fix required wrapping every such `else` in braces. Multiple round-trips with CI were needed because the violations were in different files and not all caught in a single lint run.

### Other ktlint Rules from the Same Incident

The same ktlint run also flagged:
- Semicolons inside single-line lambda blocks (separate rule; see [[concepts/ktlint-semicolon-lambda-rule]])
- Single-line bodies for suspend functions where the rule expected block style
- Lines exceeding 120 characters

These violations compound: fixing one exposes the next, requiring multiple CI iterations if not caught locally before push.

### Prevention

Run ktlint locally before push:
```bash
./gradlew ktlintCheck
```

Or use the pre-commit hook if configured. The brace symmetry violation is particularly easy to miss in code review because the asymmetric form looks natural in Kotlin (single-expression branches are common).

## Related Concepts

- [[concepts/ktlint-semicolon-lambda-rule]] - Related ktlint rule: semicolons in single-line lambdas; both are brace/punctuation style rules caught at CI lint stage
- [[concepts/ci-workflow-discipline]] - ktlint failures masking compile errors; lint job should not be in `needs:` chain that blocks test jobs
- [[concepts/ci-engine-module-missing-tests]] - Same feat/mtg incident; CI failures from multiple sources required multiple fix iterations

## Sources

- [[daily/2026-05-14.md]] — Session 13:56: feat/mtg CI failed on ktlint multiple times; `} else null` without braces was one violation; fixed by `} else { null }`; also semi in lambda and single-line suspend fun violations
