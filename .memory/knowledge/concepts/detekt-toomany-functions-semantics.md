---
title: "detekt TooManyFunctions Threshold Semantics"
aliases: [detekt-threshold-semantics, toomany-functions-off-by-one, detekt-geq]
tags: [detekt, android, kotlin, ci, code-quality]
sources:
  - "daily/2026-05-26.md"
created: 2026-05-26
updated: 2026-05-26
---

# detekt TooManyFunctions Threshold Semantics

detekt's `TooManyFunctions` rule uses `>=` (greater-than-or-equal) semantics for its threshold. A configured value of `N` means the check **fails at exactly N functions** — the maximum safe function count is `N - 1`. This is a common off-by-one trap: developers read `max: 20` as "20 functions is OK" when it actually means "20 functions triggers a violation."

## Key Points

- `TooManyFunctions: thresholdInFiles: 20` → fails when function count `>= 20`; maximum safe count is 19
- The fix for a file at exactly N functions is to reduce to N-1, not N
- Merge pattern: combine `onFooOpen` + `onFooDismiss` → `onFoo(show: Boolean)` toggle reduces count by 1
- Ozero global thresholds: `TooManyFunctions=30/20` (files/classes) — set globally in `detekt.yml`, not per-file suppress
- See [[concepts/feedback_detekt_thresholds.md]] for the configured values

## Details

### The Off-By-One Trap

detekt's rule engine evaluates `functionCount >= threshold` for `TooManyFunctions`. The YAML configuration field is named `thresholdInFiles` or `thresholdInClasses` — it reads as a limit but is implemented as a minimum-to-fail.

```yaml
# detekt.yml — this FAILS at exactly 20 functions
complexity:
  TooManyFunctions:
    thresholdInFiles: 30
    thresholdInClasses: 20
    thresholdInInterfaces: 10
    thresholdInObjects: 10
    thresholdInEnums: 10
```

A ViewModel with exactly 20 public/internal/private functions will fail `thresholdInClasses`. The developer must reach 19 or fewer.

### Toggle Pattern for Count Reduction

When a class is at exactly `N` functions and must reach `N-1`, the most semantically correct reduction is to merge open/dismiss pairs into a single toggle function:

```kotlin
// Before (2 functions):
fun onAddGroupDialogOpen() { _showAddDialog.value = true }
fun onAddGroupDialogDismiss() { _showAddDialog.value = false }

// After (1 function, net -1):
fun onAddGroupDialog(show: Boolean) { _showAddDialog.value = show }
```

Call sites update from `viewModel.onAddGroupDialogOpen()` / `viewModel.onAddGroupDialogDismiss()` to `viewModel.onAddGroupDialog(true)` / `viewModel.onAddGroupDialog(false)`. This is a strictly better API: the caller is explicit about the intended state.

### Why Not Per-File Suppress?

The Ozero project rule (from feedback) prohibits raising detekt thresholds or adding per-file suppression annotations just to accommodate a single oversized class. The correct fix is decomposition or toggling. The global thresholds (`30/20`) were set after deliberate audit — they should not drift upward for individual cases.

See [[concepts/hilt-viewmodel-split-too-many-functions]] for the decomposition pattern when a ViewModel genuinely has too many responsibilities.

## Related Concepts

- [[concepts/hilt-viewmodel-split-too-many-functions]] - When decomposition (not toggle) is the right fix
- [[concepts/hiltviewmodel-toomanyfunctions-decomposition]] - Decomposition pattern for oversized ViewModels
- [[concepts/ci-workflow-discipline]] - CI enforcement context: detekt failure blocks merge
- [[concepts/detekt-ratchet-desync-after-refactor]] - Related: detekt thresholds drifting after refactors

## Sources

- [[daily/2026-05-26.md]] - Session 16:52: detekt `TooManyFunctions=20` semantics confirmed as `>=20` = FAIL; needed 19 functions; merged `onAddGroupDialogOpen` + `onAddGroupDialogDismiss` → `onAddGroupDialog(show: Boolean)` toggle; CI run 26451747627 failed on ktlint separately
