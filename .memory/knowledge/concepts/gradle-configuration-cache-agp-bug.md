---
title: "Gradle Configuration Cache + AGP 8.7.x: MapSourceSetPathsTask Non-Serializable Bug"
aliases: [configuration-cache-agp-bug, gradle-config-cache, agp-serializable-bug]
tags: [gradle, agp, configuration-cache, build, ci]
sources:
  - "daily/2026-05-23.md"
created: 2026-05-23
updated: 2026-05-23
---

# Gradle Configuration Cache + AGP 8.7.x: MapSourceSetPathsTask Non-Serializable Bug

AGP 8.7.3 has a bug where `MapSourceSetPathsTask.__librarySourceSets__` uses `DefaultConfigurableFileCollection`, which is not serializable by Gradle's configuration cache. Adding any new XML file to the `:app` module invalidates the cache and triggers cache serialization — which fails with `BUILD FAILED`. This can manifest as a CI failure not obviously related to the change (e.g., adding `strings_fptn.xml` triggers the build failure).

## Key Points

- Bug: `MapSourceSetPathsTask.__librarySourceSets__` = `DefaultConfigurableFileCollection` (non-serializable in Gradle config cache)
- Trigger: any cache invalidation event in `:app` module (adding files, changing dependencies, etc.)
- Symptom: `BUILD FAILED` with serialization error, not a code error
- Workaround: add `org.gradle.configuration-cache.problems=warn` to `gradle.properties` → serialization failures become warnings, build continues
- Root cause is AGP 8.7.x bug, fixed in AGP 9.x
- `Tests — core + common modules` CI failures alongside this may be transient GitHub auth/checkout errors — check actual error message before assuming code regression

## Details

### Failure Scenario

1. Developer adds new XML resource to `:app` (e.g., `values/strings_fptn.xml`)
2. Gradle invalidates configuration cache for `:app` module
3. Cache serialization runs `MapSourceSetPathsTask.__librarySourceSets__` serialization
4. `DefaultConfigurableFileCollection` is not `Serializable` → exception
5. `BUILD FAILED` at configuration phase, before compilation even starts

The failure looks completely unrelated to the actual change. Without knowing this AGP bug, developers spend time debugging their code changes or Gradle setup when the issue is a framework bug.

### Workaround

In `gradle.properties`:
```properties
org.gradle.configuration-cache=true
org.gradle.configuration-cache.problems=warn   # add this line
```

With `problems=warn`, non-serializable types log a warning instead of failing the build. The configuration cache may not be fully populated (degraded caching), but the build succeeds. This is the standard pattern for working around configuration cache compatibility issues in AGP 8.x.

### AGP Version Context

- AGP 8.7.3: affected (bug present)
- AGP 9.x: fixed (field changed to a serializable type)
- If upgrading to AGP 9.x, the `problems=warn` line can be removed

## Related Concepts

- [[concepts/ci-workflow-discipline]] — CI reliability; transient checkout failures vs configuration cache failures look similar in CI output
- [[concepts/gradle-force-vs-catalog]] — AGP version management in version catalog; bumping AGP to 9.x would fix this
- [[connections/release-checks-beyond-ci]] — Green CI ≠ release success; configuration cache failures can appear only in CI but not local builds depending on cache state

## Sources

- [[daily/2026-05-23.md]] — Session 15:00: CI red on `build_job`; root cause: `org.gradle.configuration-cache=true` + AGP 8.7.3 bug `MapSourceSetPathsTask.__librarySourceSets__` non-serializable; workaround `problems=warn`; separate `Tests — core + common modules` fail was transient GitHub auth error
