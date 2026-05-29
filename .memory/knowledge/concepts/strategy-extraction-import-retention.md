---
title: Strategy extraction import retention
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---
# Strategy extraction import retention

## Key Points
- After extracting resolver/strategy logic, imports must be checked against remaining type usage, not only moved code.
- Removing a still-used import can stay hidden until style blockers are fixed and compile runs.
- `MainViewModel.kt` kept using `IpInfo` after the exit-node refactor, but its import was removed.
- This is a concrete instance of [[concepts/feature-deletion-orphaned-consumers]] and [[concepts/cascade-unresolved-import-masking]].

## Details

The exit-node unification moved engine-specific display policy out of `MainViewModel` and into resolver/strategy code. During that extraction, the `IpInfo` import was removed while the type still remained in use. CI had not yet exposed it because the earlier `ktlint + detekt` failure blocked downstream compilation.

The durable lesson is that strategy extraction changes both call sites and type ownership. A file can lose most of a concern while still retaining shared data types, so import cleanup must be verified after the full diff, not inferred from the refactor intent.

## Related Concepts
- [[concepts/feature-deletion-orphaned-consumers]]
- [[concepts/cascade-unresolved-import-masking]]
- [[concepts/ci-style-job-downstream-skip]]
- [[concepts/exit-node-strategy-resolver-contract]]

## Sources
- [[daily/2026-05-29]] records that `MainViewModel.kt` still used `IpInfo` after the import was removed during exit-node resolver work.
- [[daily/2026-05-29]] records that this compile issue was expected to surface only after the `ktlint + detekt` CI blocker was fixed.
