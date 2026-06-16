---
title: "URnetwork Location Empty String Fallback"
sources:
  - "daily/2026-05-10.md"
created: 2026-06-09
updated: 2026-06-09
---

# URnetwork Location Empty String Fallback

URnetwork location display logic must treat blank strings as missing values. Kotlin Elvis fallback only handles `null`, so `"" ?: fallback` preserves the empty string and can hide the fallback country or location label.

## Key Points

- `selectedLocationInfo()` can return `country = ""`, which is not the same as `null`.
- Elvis fallback does not run for blank strings.
- Display and model normalization should use `takeIf { it.isNotBlank() } ?: fallback`.
- This bug affected URnetwork country display after GROUP B fixes.
- The same normalization discipline applies to other SDK wrapper fields from [[concepts/mockk-aar-native-initializer-trap]].

## Details

The 2026-05-10 URnetwork fix found that location UI did not show the expected country because `selectedLocationInfo()` returned an empty `country`. The fallback expression was null-based, so the empty string survived and the UI rendered no country name.

The durable rule is to normalize blank SDK strings at the bridge or view-model boundary. Native or SDK wrappers frequently expose optional text as empty strings, while Kotlin nullable fallback only models absence as `null`. If the product behavior expects a fallback for visually missing text, the code must check `isNotBlank()` explicitly.

This is especially important after introducing wrapper data classes such as `LocationInfo`: the wrapper protects tests from native-backed AAR classes, but it should also encode clean app-level semantics for optional fields.

## Related Concepts

- [[concepts/mockk-aar-native-initializer-trap]] - `LocationInfo` wrapper isolates URnetwork tests from native-backed `ConnectLocation`.
- [[concepts/urnetwork-sdk-integration]] - URnetwork SDK integration owns location and native bridge behavior.
- [[concepts/kotlin-empty-string-null-coalesce-trap]] - Existing Kotlin trap: empty string and null are distinct fallback cases.
- [[concepts/urnetwork-explicit-bestavailable-location]] - URnetwork location semantics must avoid pretending a missing value is concrete.

## Sources

- [[daily/2026-05-10.md]] - Session 17:46: B1 root cause was `selectedLocationInfo()` returning `country=""`; `"" ?: fallback` did not trigger fallback, fixed with `takeIf { it.isNotBlank() }`.
