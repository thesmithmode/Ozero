---
title: "CI Workflow Discipline"
aliases: [ci-discipline, ci-on-dev, side-branch-workflow]
tags: [ci, workflow, process]
sources:
  - "daily/2026-04-29.md"
  - "daily/2026-05-01.md"
  - "daily/2026-05-02.md"
created: 2026-04-29
updated: 2026-05-02
---

# CI Workflow Discipline

Ozero follows a strict CI workflow where feature branches are squash-merged into `dev` immediately after push, and CI runs exclusively on `dev`. Side branches do not wait for their own CI — the team merges first and validates on the integration branch. This prevents bottlenecks from per-branch CI pipelines and ensures that the `dev` branch is always the single source of CI truth.

## Key Points

- Side branches are squash-merged into `dev` immediately after push, without waiting for CI on the branch
- CI (GitHub Actions) runs on `dev` — never on side branches
- ktlint and detekt linting errors should be caught locally before push to avoid CI failures
- The v1.0.5 release saw two CI failures (ktlint+detekt, then tests) before the third run passed
- Local testing is not used for Kotlin/Android — CI is the sole gatekeeper

## Details

The workflow emerged from practical experience: waiting for CI on feature branches wastes time and creates false confidence. A green CI on a feature branch doesn't guarantee green on `dev` after merge, so the team bypasses this step entirely. The trade-off is that `dev` may occasionally have a red CI, but this is considered acceptable because fixes are applied immediately.

The v1.0.5 development cycle validated this approach. The D1-D6 features were committed and pushed as a batch, squash-merged into `dev`, and CI was run on `dev`. The first run failed on ktlint and detekt violations, the second on test failures. Both were fixed in place on `dev`, and the third run was green. This confirmed the rule: run CI on the integration branch, fix issues there, and move forward.

A key lesson reinforced during v1.0.5: lint errors (ktlint, detekt) should be caught before push. While the workflow tolerates CI failures on `dev`, avoidable failures from formatting issues waste CI minutes and delay the pipeline.

### CI Truthfulness Rules (2026-05-01)

Two additional CI discipline rules were established after the `useJUnitPlatform()` incident revealed that CI had been reporting false greens for months:

1. **`--continue` is mandatory** for both test and style steps in `ci.yml`. Gradle's default fail-fast behavior hides failures in modules that run after the first failure. With `--continue`, a single CI run exposes all broken surfaces.

2. **Verify N > 0 tests per module**. A `BUILD SUCCESSFUL` with 0 tests executed is not green CI — it means the test runner failed to discover tests (e.g., missing `useJUnitPlatform()`). Coverage gates on 0 tests pass trivially, producing fictional coverage reports.

Both rules were codified in the global `CLAUDE.md` as permanent CI practices after the v0.0.2 latent test discovery.

### GitHub Actions Job Dependency Masking (2026-05-02)

A third CI truthfulness issue was discovered: GitHub Actions `needs:` dependencies create hard gates where a failing predecessor silently skips all successors. During v0.0.2-5, a detekt threshold failure in the `kotlin-style` job caused `assemble-debug` to be skipped entirely, hiding a compile error (`Os.close(Int)` — nonexistent API) that was only caught later in `release.yml`. This is a job-level analog of Gradle's task-level fail-fast. See [[concepts/ci-job-dependency-masking]] for the full incident analysis.

## Related Concepts

- [[concepts/release-process]] - Release tagging happens only after CI is green on `dev`
- [[concepts/per-engine-ui]] - The UI screens whose lint issues caused the first CI failure
- [[concepts/junit-platform-silent-skip]] - The incident that prompted the N > 0 tests rule
- [[concepts/gradle-continue-full-failures]] - The --continue discipline established alongside
- [[concepts/ci-job-dependency-masking]] - Job-level dependency masking discovered in v0.0.2-5

## Sources

- [[daily/2026-04-29.md]] - CI failed twice on v1.0.5 batch (ktlint+detekt, then tests), third run green; confirmed rule about not waiting for CI on side branches
- [[daily/2026-05-01.md]] - `--continue` added to ci.yml; N > 0 test verification rule established after useJUnitPlatform() revealed 3 months of silent test skipping
- [[daily/2026-05-02.md]] - detekt failure in `kotlin-style` job masked compile error in `assemble-debug` via `needs:` dependency chain
