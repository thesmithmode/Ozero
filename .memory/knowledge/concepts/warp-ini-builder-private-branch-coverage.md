---
title: WarpIniBuilder Private Branch Coverage
sources:
  - [[daily/2026-06-04]]
created: 2026-06-04
updated: 2026-06-04
---
# WarpIniBuilder Private Branch Coverage

## Key Points
- `shared-warp-settings` coverage misses can belong to `WarpIniBuilder`, `WarpConfParser`, and `RawWarpIniMerger`, not only to obvious WARP runtime classes.
- Private helper branches may be covered through reflection tests when widening public API would be worse.
- Dead or unreachable merge branches should be simplified in production instead of artificially covered.
- Raw INI preservation tests should include malformed lines, duplicate preserved lines, default headers, and merge paths.

## Details

On 2026-06-04, the WARP coverage investigation showed that visible engine coverage problems could actually sit in `shared-warp-settings`. The relevant hotspots included `WarpIniBuilder.build(config, preserveRawIni)`, `WarpConfParser`, and an unreachable branch in `RawWarpIniMerger.appendPreferredSection`.

The selected approach was to add targeted edge-case tests for real behavior and simplify unreachable branches when coverage would otherwise require meaningless test scaffolding. For private helper branches in `WarpIniBuilder`, reflection testing was accepted to avoid expanding the production API solely for coverage.

## Related Concepts
- [[concepts/warp-raw-ini-preserve-unmodeled-peer-fields]]
- [[concepts/warp-awg-field-preservation-contract]]
- [[concepts/jacoco-historical-debt-per-module-baseline-boundary]]
- [[concepts/shared-warp-settings-branch-coverage]]

## Sources
- [[daily/2026-06-04]]: sessions 19:52, 20:xx and 23:20 describe `WarpIniBuilder`, `WarpConfParser`, preserved raw INI branches, and the decision to use reflection for private helper coverage.
