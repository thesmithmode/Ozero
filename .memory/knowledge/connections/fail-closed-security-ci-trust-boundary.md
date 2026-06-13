---
title: Fail-closed security and CI trust boundary
sources:
  - daily/2026-05-31.md
created: 2026-05-31
updated: 2026-06-13
---
# Fail-closed security and CI trust boundary

## Summary
Ozero runtime safety, public-secret hygiene, and CI coverage validity form one trust boundary: leaks can survive green CI when failure routing, shipped assets, or coverage artifacts are not proven.

## Key Points
- Fail-closed routing must be proven through watchdog/startup failure tests.
- Secret storage and bootstrap assets are security inputs, not documentation details.
- CI coverage is only evidence when it reads real artifacts and includes the relevant modules.
- Runtime leak fixes need both route-level behavior and trustworthy gates.
- This connection links [[concepts/fail-closed-watchdog-startup-lockdown-contract]], [[concepts/public-repo-secret-and-insecure-asset-boundary]], and [[concepts/ci-coverage-gate-artifact-trust-contract]].
- Intentional product tradeoffs such as backup static-key restore must be documented separately so security review focuses on live credentials, insecure defaults, and fail-open behavior.

## Details
The 2026-05-31 whole-project review tied together three otherwise separate concerns. Runtime failure routing can leak traffic if it bypasses the watchdog; public assets can leak credentials or normalize insecure defaults; and a false-green coverage gate can miss both classes of regression. Treating them as independent cleanup items understates their shared role in release trust.

The practical connection is that release confidence must be built from user-visible fail-closed behavior plus credible CI evidence. A green workflow is insufficient if coverage reads stale artifacts, if modules such as desktop or `buildSrc` are outside gates, or if startup/runtime failure paths are not directly exercised.

The same review separated intentional architecture from real security defects. The backup static key was kept as a one-click restore contract, while live proxy credentials, insecure bootstrap defaults, watchdog bypasses, and false-green coverage remained actionable risks. This distinction prevents future reviews from repeatedly treating a documented product decision as the same class of defect as public live secrets or fail-open routing.

## Related Concepts
- [[concepts/fail-closed-watchdog-startup-lockdown-contract]]
- [[concepts/public-repo-secret-and-insecure-asset-boundary]]
- [[concepts/ci-coverage-gate-artifact-trust-contract]]
- [[connections/release-ci-green-vs-runtime-engine-proof]]
- [[concepts/backup-one-click-restore-contract]]
- [[concepts/intentional-tradeoff-sentinel-documentation]]

## Sources
- [[daily/2026-05-31]]: session 20:48 records fail-closed routing, static backup key, live proxy credentials, and false-green coverage as top review findings.
- [[daily/2026-05-31]]: session 20:49 records the priority order: fail-closed routing, security storage/assets, then CI coverage credibility.
- [[daily/2026-05-31]]: sessions 21:23 and 23:44 record that backup static key is intentional one-click restore behavior and should be documented as a sentinel rather than repeatedly reopened as a generic security bug.
