---
title: "Connection: CI Discipline and Release Gating"
connects:
  - "concepts/ci-workflow-discipline"
  - "concepts/release-process"
sources:
  - "daily/2026-04-29.md"
created: 2026-04-29
updated: 2026-04-29
---

# Connection: CI Discipline and Release Gating

## The Connection

The CI workflow discipline and the release process are tightly coupled through a single gate: a green CI on `dev` is both the exit criterion for feature work and the entry criterion for release tagging. There is no separate release qualification step — CI passing *is* the release gate.

## Key Insight

The absence of a separate release branch or release CI pipeline is a deliberate simplification. Because CI only runs on `dev` and releases are tagged on `dev`, the same CI run serves double duty. This eliminates the common pattern of "it worked on dev but broke on release" at the cost of making `dev` the de facto production branch. The `main` branch exists but lags behind, serving as a historical marker rather than a release gate.

## Evidence

During v1.0.5, the team pushed D1-D6 features to `dev`, waited for CI to go green (after two failures and fixes), and immediately tagged `v1.0.5` on `dev`. There was no staging step, no release branch, no additional verification. The CI run that validated the code was the same signal that authorized the release tag. The release watcher was monitoring the APK build but was terminated before confirmation — illustrating that the tag-and-build step is considered low-risk once CI passes.

## Related Concepts

- [[concepts/ci-workflow-discipline]] - The CI rules that feed into release gating
- [[concepts/release-process]] - The release flow that depends on CI status
