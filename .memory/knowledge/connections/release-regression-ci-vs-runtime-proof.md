---
title: Release regression CI vs runtime proof
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---

# Release regression CI vs runtime proof

## Key Points
- Green CI and a successful release workflow are necessary but not sufficient proof for engine runtime regressions.
- Runtime-heavy fixes need evidence from logs, reference behavior, config validation, or device traces.
- Timeout changes are high-risk until the underlying readiness or stop contract is reviewed.
- CI should be expanded to cover known missing module tests, but some live-network scenarios remain outside deterministic CI.

## Details

The v1.0.3 cycle showed a non-obvious relationship between release automation and runtime correctness. The dev CI became green, the main release workflow succeeded, and `v1.0.3` was published, but the user correctly stopped the process because the architectural basis of some fixes had not been reviewed. This created a durable rule: release success is a delivery signal, not proof that engine runtime behavior is correct.

The same session tied together multiple concepts. [[concepts/release-regression-evidence-checklist]] provides the workflow guard, [[concepts/urnetwork-readiness-connectionstatus]] shows a timeout masking a wrong readiness gate, [[concepts/singbox-autochain-validator-parity]] shows an untested config path, and [[concepts/ci-module-test-coverage-gap]] shows why a green CI run can still omit relevant module tests. Together they refine the older release checks in [[connections/release-status-vs-asset-verification]].

## Related Concepts
- [[concepts/release-regression-evidence-checklist]]
- [[concepts/urnetwork-readiness-connectionstatus]]
- [[concepts/singbox-autochain-validator-parity]]
- [[concepts/ci-module-test-coverage-gap]]
- [[connections/release-status-vs-asset-verification]]

## Sources
- [[daily/2026-05-28.md]] records that `v1.0.3` release workflow succeeded before the user requested deeper architectural review.
- [[daily/2026-05-28.md]] records that CI green could not provide a 100% guarantee for URnetwork startup, ByeDPI stop-drain, or sing-box live traffic.
- [[daily/2026-05-28.md]] records the decision to add missing module test coverage and still treat runtime scenarios as requiring separate evidence.
