---
title: Targeted branch-coverage remediation before gate weakening
sources:
  - daily/2026-06-04.md
created: 2026-06-05
updated: 2026-06-09
---

## Summary
CI coverage deficits in this run were addressed by closing narrow branch hotspots through focused tests and selective code simplification, not by lowering global quality gates.

## Key Points
- Hotspots came from rare branches in `buildSrc`, `common-vpn`, `engine-warp`, `singbox-fmt`, `singbox-subscription`, and `shared-warp-settings`.
- Branch misses were fixed with minimal behavior tests (e.g., allow/block split modes, edge parsers, shutdown and fallback paths).
- Dead or impossible fallback branches were removed instead of padded with artificial assertions.
- Reflection-based helper tests were used where private helper behavior needed coverage without API expansion.
- JaCoCo XML and sourcefile hotspots should drive the target list before adding tests.
- Per-module baseline discussion was considered only after targeted branch remediation reached known deterministic code paths.

## Details
The sessions document a cycle: global JaCoCo percentages were red despite green unit compile signals, with low branch/instruction coverage spread across specific modules. Direct coverage-boosting tactics were avoided; instead, small tests were added around concrete uncovered branches (`start`, `shutdown`, parser/null, transport edge paths).

Late 2026-06-04 sessions sharpened the method: coverage work should start from JaCoCo XML/sourcefile hotspots, not from module-level percentage guesses. This prevented WARP-related misses from being assigned to the wrong module and separated real parser/helper branches from synthetic coroutine or dead fallback branches. The related pattern is captured in [[concepts/jacoco-sourcefile-hotspot-driven-coverage]].

The same approach also reduced collateral risk: where branches were provably unreachable, dead code was simplified and removed, and helper visibility stayed internal. This avoided broad excludes and preserved verifier trust boundaries while still reducing recurring “historical debt” risk in runtime-heavy modules.

## Related Concepts
- [[concepts/buildsrc-lockfileparser-date-branch-coverage]]
- [[concepts/shared-warp-settings-branch-coverage]]
- [[concepts/common-vpn-split-start-and-shutdown-branch-coverage]]
- [[concepts/jacoco-historical-debt-per-module-baseline-boundary]]
- [[concepts/jacoco-sourcefile-hotspot-driven-coverage]]

## Sources
- [[daily/2026-06-04.md]] sessions 15:26, 18:54, 19:35, 20:39, 21:17, 23:24 show branch-focused coverage work in these exact modules.
- [[daily/2026-06-04.md]] sessions 18:54, 19:52, 23:20 and 23:24 record using JaCoCo XML/sourcefile evidence, dead-branch simplification, and private helper edge tests.
