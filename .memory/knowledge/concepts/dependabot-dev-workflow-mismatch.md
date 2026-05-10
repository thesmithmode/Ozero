---
title: "Dependabot PR Workflow Mismatch with dev-Only Flow"
aliases: [dependabot-main-target, dependabot-dev-mismatch, dependabot-triage]
tags: [ci, github, dependencies, workflow, process]
sources:
  - "daily/2026-05-09.md"
created: 2026-05-09
updated: 2026-05-09
---

# Dependabot PR Workflow Mismatch with dev-Only Flow

Dependabot PRs in Ozero target `main` (GitHub default), but the project's workflow uses `dev` as the integration branch. PRs targeting `main` are useless in the dev-only flow — they must be manually cherry-picked or applied to `dev`. Additionally, Dependabot bumps require risk assessment: minor bumps (appcompat, espresso, hadolint, checkout action) are safe, while major bumps (Kotlin 2.3, AGP 9.2, ktlint 14) carry breaking change risk from prior experience.

## Key Points

- Dependabot PRs target `main` by default — Ozero uses `dev` as default branch; PR merge doesn't reach the integration branch
- Manual triage required: apply safe patches to `dev` locally, ignore risky bumps until deliberate analysis
- Safe bumps (v0.0.8 session): appcompat 1.7.0→1.7.1, espresso 3.6.1→3.7.0, checkout v4→v6, hadolint 3.1.0→3.3.0
- Risky bumps (deferred): Kotlin 2.3 (K2 breaks ktlint baselines, requires compose-compiler sync), AGP 9.2 (major version), ktlint 14 (major version), upload-artifact v7, gh-release v3, cache v5
- 10 PRs triaged in one session: 4 applied to dev, 6 deferred with user confirmation ("не просто так использовали не самые новые версии")

## Details

### The Workflow Mismatch

GitHub's Dependabot creates PRs against the repository's default branch. In Ozero, `main` is the release branch (only updated by explicit command), while `dev` is the working branch where all integration happens. Dependabot PRs targeting `main` cannot be merged through the normal flow — merging them would put dependency bumps on `main` without going through `dev` first, violating the project's branch discipline.

The practical workflow for safe Dependabot bumps:
1. Review the PR diff on GitHub
2. Apply the version bump to `dev` locally (edit `libs.versions.toml` or workflow files)
3. Push to `dev`, verify CI
4. Close the Dependabot PR (it targeted `main`, but the change is now on `dev`)

This is manual and error-prone. A potential improvement: configure Dependabot to target `dev` via `.github/dependabot.yml` `target-branch: dev`. However, this was not implemented — the volume of PRs (10 in a batch) suggests the current triage approach is manageable.

### Risk Assessment Categories

From prior experience documented in project memory:

**Safe (minor/patch bumps, no API changes):**
- AndroidX library patch versions (appcompat, espresso, material)
- GitHub Actions version bumps for non-critical actions (checkout, hadolint)
- CI tool minor versions

**Risky (major bumps, known breaking changes):**
- **Kotlin 2.3**: K2 compiler mode breaks ktlint baselines; requires synchronized compose-compiler bump; `runTest` + `Dispatchers.IO` regressions documented in `feedback_dependency_bumps` and `feedback_runtest_while_true_init`
- **AGP 9.2**: Major version; may require Gradle version bump, change build API surface
- **ktlint 14**: Major version; rule changes may cause mass violations requiring baseline regeneration
- **upload-artifact v7 / gh-release v3 / cache v5**: GitHub Actions major versions; may change inputs/outputs API

The user explicitly confirmed the risky PRs should not be touched: "не просто так использовали не самые новые версии" — deliberate version pinning based on prior compatibility issues.

## Related Concepts

- [[concepts/ci-workflow-discipline]] - dev-only CI flow that Dependabot PRs don't align with
- [[concepts/okhttp5-kotlin-version-constraint]] - Prior example of a dependency bump (OkHttp 5.x) causing cascading compatibility issues
- [[connections/dependency-override-masking]] - Related: dependency version management complexity

## Sources

- [[daily/2026-05-09.md]] - Session 12:27: 10 dependabot PRs analyzed; 4 safe applied to dev, 6 risky deferred; user confirmed deliberate version pinning
- [[daily/2026-05-09.md]] - Session 12:32: Kotlin 2.3 risk assessed (K2 + ktlint + compose-compiler + runTest); explicit user rejection of risky bumps
