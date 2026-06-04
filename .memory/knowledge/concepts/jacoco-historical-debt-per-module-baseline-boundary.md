---
title: JaCoCo Historical Debt Per-Module Baseline Boundary
sources:
  - [[daily/2026-06-04]]
created: 2026-06-04
updated: 2026-06-04
---
# JaCoCo Historical Debt Per-Module Baseline Boundary

## Key Points
- Bundle-wide 95% JaCoCo can become a historical-debt gate when runtime/native modules with old low coverage are newly included.
- The boundary should preserve strict coverage for deterministic parsers/builders while using explicit per-module baselines for historical runtime bundles.
- Broad excludes or threshold weakening hide product logic and violate the coverage contract.
- Coverage fixes should be driven by JaCoCo XML line/branch hotspots, not by percentage chasing.

## Details

On 2026-06-04, local and CI coverage showed large deficits in `common-vpn`, `engine-warp`, `singbox-config`, `singbox-fmt`, `singbox-subscription`, `shared-warp-settings`, and a smaller `buildSrc` branch miss. Some deficits were real branch gaps in deterministic helpers, while others came from runtime orchestration, native boundaries, coroutine glue, DTO/preset accessors, and SDK-facing code that had historical low coverage.

The chosen direction was not to disable the gate or mask deterministic code. Instead, deterministic parser/builders stayed under strict expectations, dead branches were simplified when they could not be meaningfully tested, and explicit per-module baselines were considered for historically red runtime bundles. Sentinel tests in `buildSrc` also constrained exclude policy, rejecting inner-class masks that would hide deterministic surfaces.

## Related Concepts
- [[concepts/ci-coverage-historical-debt-gate-boundary]]
- [[concepts/jacoco-honest-coverage-gate-boundary]]
- [[concepts/jacoco-testable-logic-exclude-boundary]]
- [[connections/coverage-artifact-policy-feedback-loop]]

## Sources
- [[daily/2026-06-04]]: sessions 21:17 and 21:35 list measured coverage deficits and distinguish green unit tests from red JaCoCo verification.
- [[daily/2026-06-04]]: session 21:35 records rejecting broad excludes and selecting explicit baselines for historically low-coverage runtime bundles while keeping deterministic code in the gate.
