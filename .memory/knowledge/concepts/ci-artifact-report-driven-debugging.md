---
title: CI artifact report driven debugging
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---

# CI artifact report driven debugging

## Key Points
- When local tests are prohibited, GitHub Actions logs and uploaded test or coverage artifacts become the primary debugging surface.
- CI failures should be tied to a concrete run ID and job ID before drawing conclusions.
- HTML/XML reports can reveal exact failing assertions and JaCoCo ratios that the top-level workflow summary hides.
- Adding skipped module tests to CI is useful only if the resulting failures are debugged from real artifacts rather than guessed locally.

## Details

During the 2026-05-28 CI hardening session, the new extra-modules job exposed failures that had not been visible in the existing `dev` gate. The user required looking at real GitHub Actions logs, and the investigation anchored on concrete runs such as `26585604206`, with test-report and JaCoCo artifacts used to identify `GroupSeederTest`, `MasterDnsEngineTest`, `MasterDnsDeployerTest`, and `shared-warp-settings` branch coverage failures [[daily/2026-05-28.md]].

The artifact reports drove the fixes. They showed a fake DAO id collision after manual preseed, a fake SSH matcher where broad substrings stole more specific deploy responses, and branch coverage gaps in `shared-warp-settings`. These were fixed without running local tests, because the repository rule allowed only a local linter while test validation had to happen in GitHub Actions [[daily/2026-05-28.md]].

This pattern is stricter than merely watching the workflow conclusion. A terminal `failure` identifies that CI is red, but artifacts explain why and prevent speculative changes when multiple modules fail in the same job [[daily/2026-05-28.md]].

## Related Concepts
- [[concepts/github-actions-run-id-monitoring]]
- [[concepts/ci-extra-modules-test-gate]]
- [[connections/ci-extra-gate-latent-failures]]
- [[concepts/shared-warp-settings-branch-coverage]]

## Sources
- [[daily/2026-05-28.md]]: records the use of run `26585604206`, job artifacts, HTML test reports, and JaCoCo XML to diagnose failures.
- [[daily/2026-05-28.md]]: records that local tests were not run and GitHub Actions remained the test-validation source.
