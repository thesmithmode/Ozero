---
title: "Connection: Dependency Override Masking True Version State"
connects:
  - "concepts/gradle-force-vs-catalog"
  - "concepts/okhttp5-kotlin-version-constraint"
sources:
  - "daily/2026-05-04.md"
created: 2026-05-04
updated: 2026-05-04
---

# Connection: Dependency Override Masking True Version State

## The Connection

Gradle's `force()` dependency override mechanism, combined with version catalogs, creates a situation where the stated version (in `libs.versions.toml`) can diverge from the actual version in use (from `force()` call). This masking of true version state is particularly dangerous during dependency downgrades, where partial updates leave the old version active without any indication that the downgrade failed.

## Key Insight

The non-obvious relationship is that `force()` exists to resolve version conflicts and enforce consistent versions across transitive dependencies, but its use creates a secondary source of truth that can contradict the primary version specification. Developers assume that updating `libs.versions.toml` is sufficient to change a dependency version, but `force()` overrides this assumption silently.

During the OkHttp 5.3.0 rollback, the developer updated `libs.versions.toml` to 4.12.0 expecting a downgrade. The build still failed with OkHttp 5.x errors because the `force("okhttp:5.3.0")` call in `build.gradle.kts` was still active. The version catalog change was completely ineffective — a form of override masking where the true version state (5.3.0 via `force()`) was hidden by the false appearance of version control (4.12.0 in the catalog).

This masking effect extends beyond simple downgrades. It can hide broader version constraint incompatibilities: if `force()` is used to override a transitive dependency to work around a conflict, and that conflict goes unresolved in the override, the system operates in a degraded state without warning.

## Evidence

From the v0.0.2 release cycle:

1. OkHttp 5.3.0 introduced (via both catalog and `force()`)
2. Kotlin incompatibility discovered at compile time
3. **First attempt**: Update catalog to 4.12.0 — build still fails with OkHttp 5.x errors
4. **Investigation**: Discover `force("okhttp:5.3.0")` in `build.gradle.kts`
5. **Second attempt**: Remove `force()` call + update catalog — downgrade succeeds
6. **Lesson**: Two sources of truth created a masking effect

## Related Concepts

- [[concepts/gradle-force-vs-catalog]] - The mechanism that creates the masking condition
- [[concepts/okhttp5-kotlin-version-constraint]] - The version constraint that exposed the masking problem
- [[concepts/ci-workflow-discipline]] - Why CI should verify the actual dependency versions in the built artifact, not just rely on source declarations
