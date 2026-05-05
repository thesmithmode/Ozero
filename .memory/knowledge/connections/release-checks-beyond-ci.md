---
title: "Connection: Release Validation Gates Beyond CI"
connects:
  - "concepts/release-stub-gate"
  - "concepts/ci-workflow-discipline"
  - "concepts/release-process"
sources:
  - "daily/2026-05-04.md"
created: 2026-05-04
updated: 2026-05-04
---

# Connection: Release Validation Gates Beyond CI

## The Connection

Ozero's CI workflow and release process are not perfectly aligned. CI validates that code compiles, tests pass, and style is correct. But `release.yml` adds an independent validation layer that checks for artifacts that CI never examined — specifically, stub classes in the final DEX. A green CI does not guarantee a successful release.

## Key Insight

The non-obvious relationship is that CI and release workflows validate different surfaces of the artifact. CI operates at the source and early build level (does it compile? do tests pass?). The release workflow operates at the final artifact level (is the DEX acceptable for distribution?). These are not redundant checks — they catch different classes of defects.

The stub class gate exists to prevent a specific failure mode: a source file that compiled to bytecode but serves no purpose in the final product. This is not something a normal CI test would catch, because:

- The code compiles successfully (CI passes)
- The stub is not referenced in tests (tests don't fail)
- The DI graph uses the real implementation (runtime works)
- But the stub class bytecode is still present in the DEX

The release gate catches this because it inspects the final APK's bytecode, a level of validation that CI never performs.

## Evidence

During the v0.0.2 release on 2026-05-04:

1. **CI passes** — all tests green, style checks pass, compilation succeeds
2. **Tag v0.0.2 created** on dev, release.yml triggered
3. **Release gate fails** — DEX inspection finds `StubUrnetworkSdkBridge` class
4. **Tag must be recreated** — stub file physically deleted, new tag pushed, `release.yml` run succeeds

The problem was invisible to CI but immediately visible to release validation. This demonstrates that the release gate provides distinct value beyond what CI provides.

## Related Concepts

- [[concepts/release-stub-gate]] - The specific gate that validated beyond CI
- [[concepts/ci-workflow-discipline]] - CI discipline is necessary but not sufficient for release quality
- [[concepts/release-process]] - The release workflow that includes these independent checks
