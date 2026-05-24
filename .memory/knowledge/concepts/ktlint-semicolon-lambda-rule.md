---
title: "ktlint Semicolon in Single-Line Lambda Rule"
aliases: [ktlint-semicolon-lambda, lambda-semicolon-violation, single-line-lambda-multiline]
tags: [kotlin, ktlint, ci, process, gotcha]
sources:
  - "daily/2026-05-13.md"
created: 2026-05-13
updated: 2026-05-13
---

# ktlint Semicolon in Single-Line Lambda Rule

ktlint forbids semicolons inside single-line lambda blocks. The pattern `also { a; b }` is a ktlint violation — the block must be split into multiple lines. This is not merely a style preference: ktlint treats it as an error that fails CI. The rule was discovered when `OzeroVpnService.kt:771` was flagged during a WARP DoH implementation task.

## Key Points

- `also { a; b }` → ktlint error; must become `also { \n a \n b \n }`
- Applies to all single-line lambda bodies containing semicolons: `let`, `run`, `apply`, `also`, `with`, custom HOFs
- Rule: run `./gradlew ktlintCheck --continue` locally before every `.kt` git commit — no exceptions
- Failing to run ktlint locally causes avoidable CI failures and wastes CI minutes
- The violation discovered in `OzeroVpnService.kt:771` was part of four consecutive FIX commits on the WARP DoH task — all from skipping local lint checks

## Details

### The Violation Pattern

Kotlin allows multiple statements separated by semicolons in a single-line block:

```kotlin
resource.also { it.open(); it.configure() }  // ktlint ERROR
```

ktlint's `statement-wrapping` (or equivalent) rule requires multi-line form when a lambda body contains more than one statement:

```kotlin
resource.also {
    it.open()
    it.configure()
}
```

The error message from ktlint identifies the file and line but does not always make the root cause obvious on first read. The fix is always the same: split the block into multiple lines.

### Why This Matters in Ozero

Ozero's CI workflow runs `./gradlew ktlintCheck --continue` on the `dev` branch. There are no pre-push hooks configured. This means a developer who skips local ktlint runs will only discover violations after the squash-merge to `dev` and a full CI run — typically 3-5 minutes later. Each such violation costs one CI run.

The 2026-05-13 WARP DoH task accumulated four consecutive FIX commits:
1. Trailing whitespace
2. ktlint anonymous object violation
3. Unused `Dispatchers` import left after a refactor
4. `WhileSubscribed` → `Eagerly` StateFlow test fix

The semicolon-in-lambda violation was the ktlint anonymous object entry in this chain. All four were avoidable with a single local `./gradlew ktlintCheck --continue` before the first commit.

### Enforcement Rule

The rule reinforced in CLAUDE.md: **`./gradlew ktlintCheck --continue` before every `.kt` commit — no exceptions.** This is not optional for "quick fixes" or "obvious changes" — ktlint violations are often invisible to the eye and only caught by the linter.

## Related Concepts

- [[concepts/ci-workflow-discipline]] - CI discipline: local lint before push; four consecutive FIX commits from WARP DoH task
- [[concepts/warp-doh-per-slot-config]] - The task where this violation was discovered

## Sources

- [[daily/2026-05-13.md]] - `OzeroVpnService.kt:771` semicolon-in-lambda ktlint violation discovered during WARP DoH implementation; four consecutive FIX commits triggered; rule reinforced: ktlintCheck before every .kt commit
