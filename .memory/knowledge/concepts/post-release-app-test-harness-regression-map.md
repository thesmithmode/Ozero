---
title: Post-release app test harness regression map
sources:
  - daily/2026-06-01.md
created: 2026-06-02
updated: 2026-06-02
---

# Post-release app test harness regression map

## Key Points
- A green release baseline such as `v1.0.13` does not prove later strengthened app sentinels are valid.
- When `Tests - app` stays red after multiple local fixes, build a map of changed tests, workflows, coverage config, and current failures before pushing more patches.
- Failure migration from one app test to another indicates multiple independent test-harness or contract failures, not necessarily one runtime regression.
- Current failures should be classified as runtime bug, test-harness race, or changed contract before applying fixes.

## Details

The 2026-06-01 CI work showed that `dev` could be much redder than release `v1.0.13` because new or strengthened CI gates and sentinel tests appeared after the release. The app job first failed around `EngineSettingsRestartObserverTest`, then moved to `UnifiedLoggerRotationVisibilityTest` after targeted fixes. That shift made a single product-regression explanation unlikely and required a broader map of `v1.0.13..dev`.

The correct recovery order is to anchor on the current CI run, identify the exact red job and failing tests, compare changed app tests and workflow/coverage config against the release baseline, and only then patch. This avoids reactive loops where each push uncovers another independent failure without improving the understanding of why the release baseline was green.

## Related Concepts
- [[connections/post-release-ci-gate-test-harness-feedback-loop]]
- [[concepts/release-last-good-baseline-audit]]
- [[concepts/ci-artifact-report-driven-debugging]]
- [[concepts/github-actions-run-id-monitoring]]

## Sources
- [[daily/2026-06-01]]: Sessions at 21:53 and 21:56 record the stop in reactive CI fixes, the need to compare `v1.0.13..dev`, and the classification of current app failures.
- [[daily/2026-06-01]]: Session at 13:22 records that a cancelled CI run with green upstream jobs was not valid success and that `Tests - app` needed direct investigation.
