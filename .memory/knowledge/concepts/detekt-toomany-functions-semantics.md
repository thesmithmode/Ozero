---
title: "Detekt TooManyFunctions: >= Semantics"
aliases: [detekt-threshold-semantics, toomany-functions-threshold]
tags: [detekt, android, kotlin, ci, static-analysis]
sources:
  - "daily/2026-05-26.md"
created: 2026-05-26
updated: 2026-05-26
---

# Detekt TooManyFunctions: >= Semantics

The `TooManyFunctions` rule in Detekt fires when the function count is **greater than or equal to** the configured threshold. A threshold of `20` means the rule fails at exactly 20 functions, not 21. The class must have at most 19 functions to pass.

## Key Points

- `TooManyFunctions: 20` → fails if `functions >= 20`; maximum safe count is 19
- Common mistake: assuming threshold is exclusive (`> 20`), leaving exactly 20 functions and seeing CI fail
- Fix: merge paired open/dismiss dialog functions into a single toggle function (e.g., `onAddGroupDialog(show: Boolean)`)
- Toggle pattern reduces count by 1 per dialog: `onAddGroupDialogOpen()` + `onAddGroupDialogDismiss()` → `onAddGroupDialog(show: Boolean)`
- See `detekt.yml` for the configured threshold values; Ozero uses `LongParameterList=14/16, TooManyFunctions=30/20`

## Details

### Off-by-One Failure Mode

The Detekt documentation states the threshold as `thresholdInFiles`, `thresholdInClasses`, etc. All thresholds use `>=` comparison internally. When a developer sees `threshold: 20` they intuitively read it as "fail if more than 20" but the actual behavior is "fail if 20 or more."

This causes CI failures after what appears to be a safe refactor: reducing from 21 functions to exactly 20 still triggers the rule.

### Toggle Pattern for Dialog Functions

ViewModel functions that open and dismiss a dialog are natural candidates for merging:

```kotlin
// Before: 2 functions → both counted
fun onAddGroupDialogOpen() { _showAddGroupDialog.value = true }
fun onAddGroupDialogDismiss() { _showAddGroupDialog.value = false }

// After: 1 function → saves one slot
fun onAddGroupDialog(show: Boolean) { _showAddGroupDialog.value = show }
```

This pattern is semantically equivalent, reduces the function count by 1, and makes the call site read more clearly (`onAddGroupDialog(true)` vs `onAddGroupDialogOpen()`).

### Relationship to Decomposition

If a ViewModel still exceeds the threshold after toggle-merging all dialog pairs, the correct response is to decompose the ViewModel into smaller units rather than raising the threshold. Raising thresholds to accommodate a single file is explicitly forbidden in Ozero CI rules.

## Related Concepts

- [[concepts/detekt-ratchet-desync-after-refactor]] — Detekt threshold drift after large refactors
- [[concepts/hilt-viewmodel-split-too-many-functions]] — Decomposition strategy for ViewModels that exceed function limits
- [[concepts/hiltviewmodel-toomanyfunctions-decomposition]] — HiltViewModel decomposition pattern

## Sources

- [[daily/2026-05-26.md]] — Session 16:52: detekt TooManyFunctions=20 means `>=20` → need ≤19; merged `onAddGroupDialogOpen`+`onAddGroupDialogDismiss` into `onAddGroupDialog(show: Boolean)`
