---
title: "CodeQL Schedule Missing AAR Download Step"
aliases: [codeql-aar-gap, codeql-silent-failure, schedule-workflow-gap]
tags: [ci, github-actions, codeql, gotcha]
sources:
  - "daily/2026-05-09.md"
created: 2026-05-09
updated: 2026-05-09
---

# CodeQL Schedule Missing AAR Download Step

Ozero's `codeql.yml` workflow runs on a schedule trigger against `main` branch. It was missing the AAR download step that `ci.yml` has, causing `com.bringyour.sdk.*` (URnetwork SDK) classes to be unresolved during CodeQL analysis. The workflow failed silently for 4+ days because schedule-triggered workflows on `main` are not visible during normal `dev` branch development — pushes to `dev` don't trigger it, and nobody checks `main` CI independently.

## Key Points

- `codeql.yml` runs on `schedule` trigger against `main` — independent from `ci.yml` which runs on `push` to `dev`
- Missing step: AAR download for URnetwork SDK (`userwireguard.aar` + SDK AAR) needed for `com.bringyour.sdk.*` compilation
- Silent failure for 4+ days — schedule runs don't send notifications by default; nobody monitors `main` CI independently
- Fix: add AAR download step to `codeql.yml` matching the pattern from `ci.yml`
- General pattern: any workflow that compiles the full project must include ALL dependency download steps, not just the ones visible in `ci.yml`

## Details

### The Visibility Gap

GitHub Actions schedule-triggered workflows run independently of push-triggered workflows. When a developer pushes to `dev`, `ci.yml` fires and its status is immediately visible. But `codeql.yml` on `main` runs on its own schedule (typically weekly or daily). Its failures appear only in the Actions tab under the `main` branch filter — a view that developers rarely check during active `dev` branch development.

The URnetwork SDK integration (added to `dev` and eventually merged to `main`) introduced a build dependency on two AAR files that must be downloaded before compilation. `ci.yml` had the download step added during the integration. `codeql.yml`, being a separate workflow file, was not updated simultaneously. After the merge to `main`, CodeQL runs started failing on every schedule trigger.

### Discovery

The failure was discovered during the v0.0.8 release debugging session when the user reported "CodeQL красный на main — 4 дня." Investigation revealed the compile error for `com.bringyour.sdk.*` — the same error that would occur in `ci.yml` if the AAR download step were removed.

### Prevention Pattern

Any workflow that compiles the full Android project must include:
1. All Gradle dependency resolution steps
2. All local AAR download/copy steps (for engines with checked-in or CI-built AARs)
3. All native library preparation steps

A structural improvement would be to extract shared build preparation into a reusable workflow or composite action, ensuring all workflows stay synchronized. Currently, `ci.yml` and `codeql.yml` duplicate build preparation steps, creating drift risk.

## Related Concepts

- [[concepts/ci-job-dependency-masking]] - Another CI visibility issue: `needs:` hides downstream failures; CodeQL schedule hides across-workflow failures
- [[concepts/urnetwork-sdk-integration]] - The engine integration that introduced the AAR dependency
- [[connections/ci-false-green-vectors]] - CodeQL silent failure is another vector for incomplete CI visibility

## Sources

- [[daily/2026-05-09.md]] - Session 13:12: CodeQL failing on main for 4+ days; root cause = missing AAR download step in codeql.yml; fix = add step matching ci.yml pattern
