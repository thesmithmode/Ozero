---
title: "CI Workflow Discipline"
aliases: [ci-discipline, ci-on-dev, side-branch-workflow]
tags: [ci, workflow, process]
sources:
  - "daily/2026-04-29.md"
  - "daily/2026-05-01.md"
  - "daily/2026-05-02.md"
  - "daily/2026-05-04.md"
  - "daily/2026-05-07.md"
  - "daily/2026-05-14.md"
  - "daily/2026-05-24.md"
created: 2026-04-29
updated: 2026-05-24
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

### Read ALL Errors in a Red Run, Not One By One (2026-05-04)

When CI is red multiple times consecutively, the instinct is to fix the first visible error, push, and check again. The failure pattern from v0.0.2 release: CI red → fix OkHttp version in catalog → CI still red (force() override untouched) → then discover stub class in DEX separately. Each iteration wasted a full CI run.

Rule: when CI fails, read ALL failure output in the current run before writing a single line of fix code. The first error shown may not be the only root — it may not even be the most important root. The multi-root scenario (two independent failures in the same push) is common and wastes 2-3x CI cycles if treated as single-root.

Corollary: the `--continue` flag (established 2026-05-01) ensures all modules are checked. Combining `--continue` output reading with reading ALL job output before acting eliminates most multi-cycle CI debugging.

### Intermediate Commits Are Not Validated by CI (2026-05-07)

A fifth CI truthfulness gap: when multiple commits are pushed in sequence and CI is only triggered on the final `dev` push, intermediate commits have no CI validation. The gap can hide test/implementation mismatches introduced in those commits.

In the 2026-05-07 WARP incident: commit `ee1c1ea` introduced `forceVanilla=false` in `RealWarpSdkBridge` alongside a test that expected VANILLA output — an internal contradiction. `gh run list --commit ee1c1ea` returned empty: CI was never triggered for that commit. The contradiction persisted until the next full CI run on a later push.

Rule: after any commit sequence that touches both tests and their implementation, verify `gh run list --commit <sha>` shows a completed run. If the run list is empty, squash or re-push to ensure CI validation before moving to dependent work. A green CI on branch tip does not retroactively validate intermediate commits.

### New Module Must Be Explicitly Added to CI Test Job (2026-05-14)

A fourth CI truthfulness rule: when a new Gradle module is created, it is NOT automatically included in the CI test job. Gradle's `:test` task runs only modules listed in `settings.gradle` AND explicitly included in the CI command. If `ci.yml` runs `./gradlew :app:test :engines-core:test` (explicit list), a new module `engine-telegram` will be silently skipped — zero tests, zero failures, false green.

Rule: whenever a new `engine-*` module is added, immediately verify that `ci.yml` includes it in the test command. A `BUILD SUCCESSFUL — 0 tests run` on the new module is the signal to check the CI config.

### @Volatile Fields Require a Blank Line Before Them (2026-05-24)

ktlint enforces that declarations with annotations (`@Volatile`, `@GuardedBy`, etc.) must have an empty line before them when they follow another declaration. Absence of the blank line produces the error: `Declarations and declarations with annotations should have an empty space between`.

This was caught during v0.3.0 preparation: `EngineWarp` and `RealWarpSdkBridge` each had `@Volatile` fields added without a blank line separator. The fix is mechanical — add a blank line before any `@Volatile`/`@GuardedBy` field when it follows another field or function.

Rule: whenever adding an annotated field (especially `@Volatile`), check that there is a blank line between it and the preceding declaration.

### upload-artifact v7 Breaking API Change (2026-05-24)

`actions/upload-artifact@v7` changed its required inputs: both `name` and `path` are now mandatory; previously optional deprecated parameters were removed. Bumping from v3/v4 to v7 in `release.yml` without updating the step inputs causes the action to fail with a missing required input error.

Rule: when bumping `upload-artifact` version, read the release notes for input changes before applying the bump.

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
- [[daily/2026-05-04.md]] - Session 15:22: v0.0.2 CI red twice in succession (OkHttp force() + stub class); lesson = read ALL errors in a run before acting; first symptom ≠ only root
- [[daily/2026-05-07.md]] - Session 13:24: commit `ee1c1ea` forceVanilla=false + test expecting VANILLA undetected; `gh run list --commit ee1c1ea` empty — CI not triggered; intermediate commit validation gap rule established
- [[daily/2026-05-14.md]] - new `engine-telegram` module silently skipped by CI test job; explicit module list in ci.yml required
- [[daily/2026-05-24.md]] - Session 15:46: v0.3.0 CI failed on `@Volatile` blank line missing (EngineWarp + RealWarpSdkBridge) and `upload-artifact@v7` breaking input API change
