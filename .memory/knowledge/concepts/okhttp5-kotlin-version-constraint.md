---
title: "OkHttp 5.x Kotlin Version Requirement"
aliases: [okhttp5-constraint, kotlin-compatibility-trap, version-constraint-coupling]
tags: [dependencies, okhttp, kotlin, version-constraint, gotcha]
sources:
  - "daily/2026-05-04.md"
created: 2026-05-04
updated: 2026-05-04
---

# OkHttp 5.x Kotlin Version Requirement

OkHttp version 5.x requires Kotlin 2.2.0 or higher due to use of Kotlin language features not available in earlier versions. This creates a hidden version constraint coupling: upgrading OkHttp beyond 5.0.0 implicitly requires upgrading the entire project's Kotlin compiler, which can trigger cascading compatibility issues in other dependencies and build infrastructure.

## Key Points

- OkHttp 5.0+ requires Kotlin 2.2.0+ due to language feature usage (likely coroutines/suspension, nullability features)
- Ozero project was at Kotlin 2.0.20 during v0.0.2 development, incompatible with OkHttp 5.x
- Attempting to use OkHttp 5.3.0 with Kotlin 2.0.20 produces compilation failure: `compileReleaseKotlin FAILED`
- This coupling is non-obvious — the version constraint is not explicitly documented in OkHttp's Maven metadata
- Major dependency version upgrades should be treated as multi-step operations: check version constraints, upgrade Kotlin if needed, then upgrade the target dependency

## Details

### The v0.0.2 Hotfix Failure

During v0.0.2 release preparation, a security hotfix upgraded OkHttp from 4.12.0 (stable for months) to 5.3.0. The upgrade appeared straightforward in `libs.versions.toml`. However, the CI build immediately failed during the Kotlin compilation phase with `compileReleaseKotlin FAILED`.

The root cause: OkHttp 5.3.0 ships compiled bytecode using Kotlin language features from the 2.2.0 release. When the Kotlin 2.0.20 compiler encounters these features during the AAR processing step, it cannot parse or validate them, causing compilation to abort.

The scope of the failure is wide: not only the direct OkHttp dependency fails to compile, but the entire release build is blocked because the Kotlin compiler cannot process the bytecode artifact. The error occurs at the library compilation phase, before application code is even touched.

### Hidden Coupling

This coupling is particularly insidious because:

1. OkHttp's documentation does not explicitly state the Kotlin version requirement
2. The version catalog system allows specifying independent versions for OkHttp and Kotlin, creating a false sense that they are independent
3. The build succeeds until the actual compilation phase (which is comparatively late in the build)
4. No error message explicitly says "OkHttp 5.3 requires Kotlin 2.2.0+" — the message is buried in compilation output

### Migration Path

Fixing the issue requires coordinated updates:

1. Identify the minimum Kotlin version required by the target OkHttp version
2. Check if that Kotlin version is compatible with other dependencies in the project
3. Upgrade Kotlin compiler and kotlinx-coroutines (often coupled)
4. Re-run CI to validate no other dependencies are broken
5. Only then upgrade the target dependency (OkHttp)

In Ozero's case, the decision was to revert to OkHttp 4.12.0 (stable and compatible) rather than trigger a Kotlin compiler upgrade, which would require broader testing.

## Related Concepts

- [[concepts/gradle-force-vs-catalog]] - The recovery from this issue required careful dependency management
- [[concepts/ci-workflow-discipline]] - CI failure mechanisms and the importance of checking full error output
- [[concepts/dependency-bumps-require-ci-verification]] - General pattern from memory that bumps need verification

## Sources

- [[daily/2026-05-04.md]] - Session 15:22: OkHttp 5.3.0 hotfix required Kotlin 2.2.0+; Ozero at 2.0.20 caused compileReleaseKotlin FAILED; reverted to 4.12.0; lesson = major dependency upgrades check version constraint coupling

