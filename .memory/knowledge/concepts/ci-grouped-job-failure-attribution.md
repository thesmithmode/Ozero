---
title: Grouped CI jobs require artifact-level failure attribution
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---
# Grouped CI jobs require artifact-level failure attribution

## Key Points
- A grouped CI job name does not identify the failing module; logs and artifacts must be inspected before assigning blame.
- A job named for multiple modules can fail because of only one module's tests.
- Test-report artifacts and JaCoCo XML are the primary evidence source when local tests are forbidden.
- Fixes should target the failing module and concrete assertion or coverage gap, not the grouped job label.

## Details

During the 2026-05-28 CI cycle, a grouped job named `engine-urnetwork + engine-byedpi` failed, but the daily log records that the actual failure came from ByeDPI tests rather than URnetwork or FPTN runtime behavior. Treating the job label as the root cause would have sent the investigation to the wrong engine.

The same day, the extra-module CI job repeatedly exposed hidden failures in `singbox-subscription`, `engine-masterdns`, and `shared-warp-settings`. Those failures were diagnosed from concrete GitHub Actions run IDs, downloaded test reports, and JaCoCo coverage XML, not from local reproduction.

## Related Concepts
- [[concepts/ci-artifact-report-driven-debugging]]
- [[concepts/ci-extra-modules-test-gate]]
- [[concepts/github-actions-run-level-polling]]
- [[connections/ci-extra-gate-latent-failures]]

## Sources
- [[daily/2026-05-28]] records the grouped `engine-urnetwork + engine-byedpi` job failure and the later finding that ByeDPI tests caused the failure.
- [[daily/2026-05-28]] records artifact-driven diagnosis of `GroupSeederTest`, `MasterDnsEngineTest`, `MasterDnsDeployerTest`, and `shared-warp-settings` branch coverage failures.
