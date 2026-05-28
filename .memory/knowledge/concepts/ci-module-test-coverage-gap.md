---
title: CI module test coverage gap
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---

# CI module test coverage gap

## Key Points
- Written tests are not useful as CI evidence unless their Gradle tasks actually run in CI.
- Ozero had module tests outside the main `CI` job, including sing-box related modules.
- Coverage strengthening should add missing module jobs and coverage verification rather than delete tests.
- Deleting tests requires proof that they are both useless and materially slow.

## Details

After the user asked whether test coverage was sufficient for URnetwork, ByeDPI, and sing-box, the review found a systemic CI gap: some tests existed but were not included in the primary dev CI path. The affected module set included sing-box configuration, engine, room, subscription, and formatting modules, plus other module-level tests.

The agreed direction was to strengthen CI rather than prune tests. Regression tests were added for URnetwork `awaitReady` edge cases and sing-box auto-chain behavior with entirely invalid server sets. This article refines the broader warning in [[connections/ci-false-green-vectors]]: a green run can be false green not only because assertions are weak, but because the runner never executes the relevant tasks.

## Related Concepts
- [[connections/ci-false-green-vectors]]
- [[concepts/ci-workflow-discipline]]
- [[concepts/junit-platform-silent-skip]]
- [[concepts/release-regression-evidence-checklist]]
- [[connections/release-regression-ci-vs-runtime-proof]]

## Sources
- [[daily/2026-05-28.md]] records the confirmed risk that tests can be written but not started by CI.
- [[daily/2026-05-28.md]] records affected modules including `singbox-config`, `engine-singbox`, `singbox-room`, `singbox-subscription`, and `singbox-fmt`.
- [[daily/2026-05-28.md]] records the decision not to delete tests without proof that they are useless and materially slow.
