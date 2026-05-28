---
title: CI artifact driven extra module debugging
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---

# CI artifact driven extra module debugging

## Key Points
- When new CI jobs expose previously skipped modules, GitHub Actions logs and artifacts become the primary evidence source.
- Test report XML/HTML and JaCoCo XML should be inspected before changing tests or thresholds.
- Hidden module failures often reveal stale fakes, wrong Gradle task names, and branch-coverage gaps.
- Coverage gates should stay release-relevant; non-release surfaces can keep tests and reports without blocking Android release readiness.

## Details

After extra module tests were added to CI on 2026-05-28, the first failures were not all product regressions. One failure came from selecting the Android task `:shared-warp-settings:testDebugUnitTest` for a Kotlin library module that needed `:shared-warp-settings:test`. Later runs exposed real latent failures in sing-box modules, MasterDNS tests, fake DAOs, fake SSH matching, and `shared-warp-settings` branch coverage.

The reliable workflow was to anchor on a concrete GitHub Actions run ID, inspect failed job logs, download or read test-report artifacts, and use JaCoCo XML for exact coverage ratios. That avoided guessing from local assumptions and supported targeted fixes: unique fake DAO IDs after manual preseed, longest-substring command matching in MasterDNS fake SSH transport, and branch-focused tests for parser/AWG paths.

## Related Concepts
- [[concepts/ci-extra-modules-test-gate]]
- [[connections/ci-extra-gate-latent-failures]]
- [[concepts/github-actions-run-id-monitoring]]
- [[concepts/app-desktop-coverage-gate-scope]]

## Sources
- [[daily/2026-05-28]] records that CI run `26583636181` failed because `shared-warp-settings` was wired to the wrong Gradle task.
- [[daily/2026-05-28]] records that reports from run `26585604206` identified failures in `GroupSeederTest`, `MasterDnsEngineTest`, and `MasterDnsDeployerTest`.
- [[daily/2026-05-28]] records that JaCoCo XML showed `shared-warp-settings` branch coverage below the 0.95 gate while line coverage was already sufficient.
