---
title: "Release Stub Class Gate"
aliases: [release-forbidden-stubs, release-dex-check, stub-removal-requirement]
tags: [release, gradle, ci-gates, apk-validation]
sources:
  - "daily/2026-05-04.md"
created: 2026-05-04
updated: 2026-05-04
---

# Release Stub Class Gate

Ozero's `release.yml` workflow includes a validation step that forbids stub classes from appearing in the final APK's DEX. This is an independent gate from CI: a green CI does not guarantee successful release if stub classes are present. During the v0.0.2 release, `StubUrnetworkSdkBridge` was discovered in the DEX despite not being referenced by the DI graph, causing release failure and requiring tag recreation.

## Key Points

- `release.yml` contains a forbidden-stubs check that validates DEX contents before publishing the APK
- Stub classes in DEX cause release failure even when CI passes and DI graph uses real implementations
- Stub class discovery mechanism: likely inspection of bytecode class names matching `Stub*` pattern
- `StubUrnetworkSdkBridge` was compiled into the APK classpath but never instantiated — still blocked release
- General rule: removing a stub class requires not just changing DI wiring, but deleting the source file entirely

## Details

### Stub Class Artifact Problem

In Ozero's architecture, temporary stub implementations are used during development to stand up the DI graph before real implementations are ready. For example, `StubUrnetworkSdkBridge` was a placeholder that inherited from `UrnetworkSdkBridge` interface but only logged "not implemented."

When the real `RealUrnetworkSdkBridge` was implemented and wired into the DI graph via `UrnetworkModule.provideUrnetworkSdkBridge()`, the stub became unused. However, the source file `StubUrnetworkSdkBridge.kt` remained in the `main` source set. The Kotlin compiler included it in the APK's DEX because it's part of the compilation unit, even though the DI graph never instantiates it.

The `release.yml` stub gate exists precisely to catch this scenario: a source file that compiled to bytecode but serves no purpose in production. The gate prevents shipping stub classes that could confuse maintainers, create false debugging leads, or indicate incomplete refactoring.

### Release Gate Independent from CI

This is a critical architectural point: the release workflow has validation steps that are independent from CI. CI can be green because:

1. Code compiles successfully
2. Tests pass
3. Style checks pass
4. No obvious runtime errors

But the DEX can still contain unwanted artifacts that CI never validated. The `release.yml` gate performs additional checks beyond CI, acting as a second validation layer before the APK reaches users.

### v0.0.2 Tag Recreation

During the v0.0.2 release on 2026-05-04:

1. Tag `v0.0.2` was created on `dev` with green CI
2. `release.yml` triggered and immediately failed: "StubUrnetworkSdkBridge found in DEX"
3. Fix required: delete `StubUrnetworkSdkBridge.kt` entirely from `src/main/java/`
4. Tag was deleted and recreated with the corrected source tree
5. Second `release.yml` run succeeded

This forced the team to recognize that "unused in DI graph" is not equivalent to "removed from codebase." The stub file had to be physically deleted.

## Related Concepts

- [[concepts/release-process]] - Release gate that includes this stub validation
- [[concepts/ci-workflow-discipline]] - CI validates different surfaces than release gates
- [[concepts/urnetwork-sdk-integration]] - The engine where stub removal was required

## Sources

- [[daily/2026-05-04.md]] - Session 15:22: `StubUrnetworkSdkBridge` found in DEX during release, release.yml gate blocked APK publication; required tag deletion and recreation; lesson = release checks exist independent of CI green

