---
title: Release CI green does not prove engine runtime recovery
sources:
  - [[daily/2026-05-28]]
created: 2026-05-31
updated: 2026-05-31
---

# Release CI green does not prove engine runtime recovery

## Summary

For Ozero engine regressions, green CI and a successful release workflow must be combined with same-process runtime evidence, self-review, and scenario-specific sentinels before declaring recovery reliable.

## Key Points

- The `v1.0.3` release succeeded, but the user stopped the process because the fixes had not been reviewed deeply enough.
- WARP events after a full process restart did not prove recovery after ByeDPI poisoned state in the same process.
- CI was strengthened with extra-module gates, but device runtime cases still required explicit scenario evidence.
- The connection links [[concepts/release-regression-self-review-before-main]], [[concepts/engine-poisoned-state-recovery-proof]], and [[concepts/ci-extra-modules-exposes-hidden-release-risk]].

## Details

The May 28 work exposed two different kinds of confidence. GitHub Actions could prove compilation, linting, test execution, and release workflow completion. It could not, by itself, prove that a failed engine stop/start recovered in the same Android process or that a live subscription behaved correctly under real traffic.

This distinction shaped the later `v1.0.5` work: the release was narrowed to APK-only, same-process recovery was treated as a required proof boundary, and regression sentinels were added for engine behaviors. Extra-module CI reduced false greens, but it remained a secondary signal rather than a substitute for runtime evidence.

## Related Concepts

- [[concepts/release-regression-self-review-before-main]]
- [[concepts/engine-poisoned-state-recovery-proof]]
- [[concepts/ci-extra-modules-exposes-hidden-release-risk]]
- [[connections/release-regression-ci-vs-runtime-proof]]

## Sources

- [[daily/2026-05-28]]: Sessions 16:54, 17:20, 20:37, 20:59, and 22:00 recorded the gap between green CI, release success, same-process recovery proof, and runtime scenario validation.
- [[daily/2026-05-28]]: The WARP-after-restart clarification established that a full process restart is not evidence of in-process recovery.
