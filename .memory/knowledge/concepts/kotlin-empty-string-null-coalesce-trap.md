---
title: "Kotlin Empty String Null-Coalescing Trap"
aliases: [kotlin-empty-string-elvis, empty-string-null-safety, isnotblank-vs-null]
tags: [kotlin, gotcha, android, string-handling]
sources:
  - "daily/2026-05-10.md"
created: 2026-05-10
updated: 2026-05-10
---

# Kotlin Empty String Null-Coalescing Trap

The Kotlin Elvis operator `?:` (null-coalescing) returns the right-hand value only when the left-hand value is `null`. An empty string `""` is not null — `"" ?: fallback` evaluates to `""`, not `fallback`. This trips up code that expects SDK or API methods to return `null` when a value is absent, but instead they return an empty string `""`. The correct guard is `str.takeIf { it.isNotBlank() } ?: fallback`.

## Key Points

- `"" ?: fallback` → `""` (not `fallback`) — empty string is not null in Kotlin's type system
- Common when Android SDK/Java interop returns empty string instead of null for absent values
- `str.takeIf { it.isNotBlank() } ?: fallback` is the correct idiomatic pattern
- `takeIf { it.isNotEmpty() }` if only length matters; `takeIf { it.isNotBlank() }` if whitespace-only strings should also fall back
- Go SDK via gomobile often returns `""` for missing fields rather than `null` (Go strings are never nil)

## Details

### The Mechanism

Kotlin's null-safety system distinguishes between `null` (absence of value) and `""` (an empty string value). The Elvis operator `?:` is purely null-checking: it returns the right operand only when the left operand is `null`. This is semantically correct but creates a mismatch when APIs represent "no value" as an empty string rather than null.

The pattern appears frequently at language-boundary crossings:
- **Go via gomobile**: Go strings cannot be nil. When a Go SDK field has no value (zero value = `""`), the JNI binding returns `""` to Kotlin. `val country: String? = loc.country` receives `""`, not `null`.
- **Java APIs**: Many Java methods return `""` to indicate absence rather than throwing or returning `null`
- **DataStore defaults**: `stringPreferencesKey` reads return `""` when a key has never been written (depending on default)

### The Ozero Discovery

In Ozero v0.0.9, `UrnetworkSdkBridge.selectedLocationInfo()` returned `LocationInfo(country="", ...)` when the URnetwork SDK had not yet resolved a location. The `MainViewModel` code attempted:

```kotlin
// BROKEN: "" is not null
val displayCountry = locationInfo?.country ?: "Неизвестно"
// Result: "", not "Неизвестно"
```

The UI displayed an empty string where "Неизвестно" (Unknown) was expected, and the country name never appeared even after connection.

The fix:

```kotlin
// CORRECT: guards against both null and empty string
val displayCountry = locationInfo?.country
    ?.takeIf { it.isNotBlank() }
    ?: "Неизвестно"
```

The full chain: `locationInfo` may be `null` (safe call `?.country`) → `country` may be `""` (`takeIf { isNotBlank() }` returns `null` for blank strings) → `?: "Неизвестно"` handles both `null` cases.

### When `""` vs `null` Matters for Display

| Source value | `?: fallback` | `takeIf { isNotBlank() } ?: fallback` |
|-------------|--------------|--------------------------------------|
| `null` | `fallback` ✓ | `fallback` ✓ |
| `""` | `""` ✗ | `fallback` ✓ |
| `"value"` | `"value"` ✓ | `"value"` ✓ |
| `"  "` (spaces) | `"  "` ✗ | `fallback` ✓ |

Use `takeIf { isNotBlank() } ?: fallback` whenever the source may return empty or whitespace-only strings for "no value."

### Related Kotlin Patterns

The same issue appears with:
- `listOf<T>()` vs `null`: `emptyList<T>()` is not null; use `list.takeIf { it.isNotEmpty() } ?: fallback`
- `0` vs `null`: zero is not null for numeric types; use `value.takeIf { it != 0 } ?: fallback`

## Related Concepts

- [[concepts/urnetwork-sdk-integration]] — URnetwork SDK `ConnectLocation.country` returns `""` for unresolved locations; this trap discovered during GROUP B fix
- [[concepts/kotlin-suspendcancellablecoroutine-type-inference]] — Another Kotlin type-system gotcha at language boundaries with non-obvious defaults
- [[concepts/vpn-ip-detection-contract]] — IP and location display code where this guard is critical for correct UX

## Sources

- [[daily/2026-05-10.md]] — Session 17:46 GROUP B fix: `selectedLocationInfo().country == ""` not null; `"" ?: "Неизвестно"` returned `""`; fix = `.takeIf { isNotBlank() } ?: fallback`
