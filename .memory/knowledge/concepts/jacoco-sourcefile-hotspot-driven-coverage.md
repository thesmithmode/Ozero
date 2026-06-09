---
title: JaCoCo sourcefile hotspot driven coverage
sources:
  - daily/2026-06-04.md
created: 2026-06-09
updated: 2026-06-09
---
# JaCoCo sourcefile hotspot driven coverage

## Summary
Coverage fixes should start from JaCoCo XML/sourcefile hotspots and concrete branch gaps, because module-level percentages often point at the wrong owning layer.

## Key Points
- Module-level red coverage can hide the real sourcefile, helper, or shared-module owner.
- `engine-warp` coverage symptoms could belong to `shared-warp-settings` helpers such as `WarpIniBuilder` and `RawWarpIniMerger`.
- `singbox-fmt` and `singbox-subscription` gaps required parser/helper edge cases rather than broad test expansion.
- Sourcefile-level branch evidence is more actionable than general HTML percentage summaries.

## Details
On 2026-06-04, the coverage work moved from broad red modules toward JaCoCo XML and sourcefile-level hotspots. This mattered because the apparent failing owner was sometimes misleading: WARP-related coverage misses were not necessarily in `engine-warp` runtime code, but in `shared-warp-settings` helpers. Similar branch-heavy helpers existed in `singbox-fmt`, `singbox-subscription`, and `buildSrc`.

The durable workflow is to convert each red percentage into a path, class, method, branch, and reason before adding tests. That keeps fixes aligned with [[concepts/coverage-gap-targeted-branch-remediation]] and avoids weakening [[concepts/jacoco-honest-coverage-gate-boundary]]. It also explains why sourcefile evidence can justify either an edge-case test or dead-branch removal, but not random test padding.

## Related Concepts
- [[concepts/coverage-gap-targeted-branch-remediation]]
- [[concepts/warp-ini-builder-private-branch-coverage]]
- [[concepts/singbox-subscription-branch-coverage-edges]]
- [[concepts/jacoco-honest-coverage-gate-boundary]]

## Sources
- [[daily/2026-06-04]]: sessions 18:54 and 19:52 record using JaCoCo XML/sourcefile gaps for `DownloadBinaryTask`, `StartSequenceCoordinator`, `WarpIniBuilder`, `UriCompat`, and `ConfigBuilder`.
- [[daily/2026-06-04]]: sessions 23:20 and 23:24 record that `shared-warp-settings` hotspots were in `WarpIniBuilder`, `WarpConfParser`, and `RawWarpIniMerger`, not obvious WARP runtime classes.
