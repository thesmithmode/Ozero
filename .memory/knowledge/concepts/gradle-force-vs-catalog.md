---
title: "Gradle force() vs Version Catalog Priority"
aliases: [gradle-dependency-override, version-catalog-precedence, force-priority-trap]
tags: [gradle, dependencies, gotcha, build]
sources:
  - "daily/2026-05-04.md"
created: 2026-05-04
updated: 2026-05-04
---

# Gradle force() vs Version Catalog Priority

When a Gradle project uses both a version catalog (`libs.versions.toml`) and explicit `force()` overrides in `build.gradle.kts`, the `force()` call takes precedence. This creates a subtle trap during dependency downgrades: updating only the version catalog entry leaves the `force()` override untouched, causing the downgrade attempt to fail silently. The build uses the `force()`-specified version despite the catalog specifying a different one.

## Key Points

- `build.gradle.kts force(dependency)` calls have higher priority than `libs.versions.toml` entries
- When revoking a dependency upgrade, both the catalog AND any `force()` calls must be updated
- Failing to update `force()` leaves the override active — no build error, but no downgrade either
- This is particularly dangerous when rolling back hotfixes that introduced version constraints
- General rule: always check both files when modifying dependency versions

## Details

### The OkHttp 5.3.0 Incident

During v0.0.2 release, a hotfix upgraded OkHttp from 4.12.0 to 5.3.0 to address a security issue. The upgrade brought an unexpected constraint: OkHttp 5.x requires Kotlin 2.2.0+, but Ozero's project used Kotlin 2.0.20, causing compilation failure.

The initial rollback attempt touched only `libs.versions.toml`, changing the version back to 4.12.0. The CI build still failed with the same OkHttp 5.x error. Investigation revealed that `build.gradle.kts` contained:

```kotlin
dependencies {
    // ... other dependencies
    force("com.squareup.okhttp3:okhttp:5.3.0")  // ← explicit override
}
```

The `force()` call, which was added to the Gradle file during the hotfix, still forced okhttp 5.3.0 despite the catalog downgrade. The second attempt required updating both locations:

1. `libs.versions.toml`: `okhttp = "4.12.0"` (downgrade)
2. `build.gradle.kts`: Remove the `force("okhttp:5.3.0")` line entirely

This pattern is particularly insidious because:
- The version catalog change appears to be the "official" version source
- The `force()` call is often added temporarily during hotfixes and forgotten
- No build error occurs — the wrong version is silently used
- The mismatch between what the catalog says and what actually builds is invisible unless you read both files

## Related Concepts

- [[concepts/okhttp5-kotlin-version-constraint]] - The specific version constraint that triggered this discovery
- [[concepts/ci-workflow-discipline]] - CI should fail fast, but silent version override failures evade quick detection
- [[connections/dependency-override-masking]] - This phenomenon can mask broader dependency conflict issues

## Sources

- [[daily/2026-05-04.md]] - Session 15:22: OkHttp 5.3.0 hotfix caused Kotlin 2.0.20 incompatibility; first rollback (catalog only) failed; second rollback (catalog + force() removal) succeeded; lesson = check BOTH files on dependency changes
