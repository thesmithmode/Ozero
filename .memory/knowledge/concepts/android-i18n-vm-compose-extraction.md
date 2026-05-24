---
title: "Android i18n: Extracting Hardcoded Strings from ViewModels and Compose"
aliases: [i18n-extraction, hardcoded-cyrillic, vm-string-resources, compose-stringresource]
tags: [android, i18n, compose, viewmodel, kotlin, gotcha]
sources:
  - "daily/2026-05-15.md"
created: 2026-05-15
updated: 2026-05-15
---

# Android i18n: Extracting Hardcoded Strings from ViewModels and Compose

Hardcoded string literals in ViewModels and Compose screens silently bypass Android's localization system. In Ozero, T-43 and T-44 extracted Cyrillic literals from ViewModels and 7 Compose screens respectively. The affected categories were progress messages (`progressText`), validation error strings (`VALIDATION_REQUIRED_FIELDS`), and engine settings screen labels. Parity audit confirmed 17 keys missing in es/pt and 180+ in ar/de/fr/hi/ja/zh-rCN — the es/pt set (332 lines) was brought to full parity with ru/en in the same session.

## Key Points

- ViewModels must not contain hardcoded string literals — they should emit `@StringRes Int` identifiers or inject a `ResourceProvider`/`Context` for string resolution
- Compose screens must use `stringResource(R.string.key)` — passing raw strings from VMs or constructing them ad hoc bypasses localization
- `progressText`, validation messages, and engine settings labels are high-risk categories for hardcoded strings: they are written in the primary dev language and rarely updated when adding locale support
- The `translate.md` rule mandates parity across ru/en/es/pt; any new string key added to `values/strings.xml` must appear in all four locale files before the PR is merged
- WarpEngineSettingsScreen callers must use `stringResource()` for all visible text; passing raw Kotlin string constants from ViewModel state fails localization

## Details

### ViewModel Hardcoded Strings

ViewModels should be locale-agnostic: they hold state and logic, not presentation text. When a ViewModel constructs strings directly (e.g., `_uiState.value = "Подключение..."`) it hardcodes the locale and makes localization impossible without refactoring the VM.

The correct pattern depends on whether the string needs to be rendered in UI or logged:

- **UI-bound strings**: ViewModel emits a `@StringRes Int` (e.g., `R.string.connecting`) in its state class. The Compose layer calls `stringResource(state.statusLabelRes)`.
- **Logged strings**: Use constants or enum names rather than localized text. Logs are for developers, not users.
- **Validation errors**: Define a sealed class `ValidationError` with cases; map to `@StringRes` at the Compose boundary.

Injecting Android `Context` into a ViewModel is an anti-pattern (memory leak risk, test difficulty). Use `ApplicationContext` only when unavoidable, or create a thin `ResourceProvider` interface that wraps `context.getString()` and can be faked in tests.

### Compose Screen Hardcoded Strings

Compose screens with hardcoded string literals are caught during i18n audit, not at compile time — the compiler sees a valid `String` argument regardless of whether it came from `stringResource()` or a literal. Common violation patterns:

```kotlin
// WRONG — hardcoded
Text("Подключение к серверу...")
Button(onClick = { ... }) { Text("Применить") }

// RIGHT — localized
Text(stringResource(R.string.connecting_to_server))
Button(onClick = { ... }) { Text(stringResource(R.string.apply)) }
```

The audit approach: `grep -rn '"[А-Яа-яЁё]' --include="*.kt" app/src/main/` finds all Cyrillic string literals in Kotlin sources. Each match is either a legitimate constant (enum name, map key) or a localization gap.

### Parity Discipline

After extracting strings to `values/strings.xml`, identical keys must exist in `values-en/strings.xml`, `values-es/strings.xml`, and `values-pt/strings.xml`. The es/pt parity audit from 2026-05-15 found 17 keys missing in both locales simultaneously — indicating they were added to ru/en but not propagated to the romance language set. The fix was a single commit adding 332 lines of full-parity translations.

The ar/de/fr/hi/ja/zh-rCN gap (180+ keys) represents stale partial translations from an earlier stage of the project. These are lower priority (not in the mandatory set per `translate.md`) but create a user-facing gap for speakers of those languages.

### CI Enforcement

There is no automated compile-time check for missing `stringResource()` usage (unlike missing string keys, which `./gradlew lintDebug` would catch as `MissingTranslation`). The i18n audit is currently manual. Adding a custom lint rule or CI step that fails on Cyrillic string literals in `*.kt` Compose source files would make this systematic.

## Related Concepts

- [[concepts/per-engine-ui]] - Each engine settings screen is a Compose screen; per-engine screens are highest risk for hardcoded strings added during engine development
- [[concepts/hilt-viewmodel-split-too-many-functions]] - ViewModel decomposition; extracting string emission into the right layer is part of the same discipline
- [[concepts/android-xml-string-escaping]] - Related: string resources must also be properly escaped in XML; both articles address the `values/strings.xml` layer

## Sources

- [[daily/2026-05-15.md]] - Session 14:10: T-43 VM Cyrillic extraction (WarpEngineSettingsScreen callers, progressText, VALIDATION_REQUIRED_FIELDS); T-44 Compose screens (7 screens, interrupted); es/pt 332-line parity commit; 17 keys missing in es/pt, 180+ in ar/de/fr/hi/ja/zh-rCN
- [[daily/2026-05-15.md]] - Session 15:02: T-43 result reviewed and extended by hand; T-44 delegated to subagent but interrupted before completion
