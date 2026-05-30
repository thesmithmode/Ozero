---
title: GroupSeeder URL dedupe userOrder contract
sources:
  - daily/2026-05-30.md
created: 2026-05-30
updated: 2026-05-30
---
# GroupSeeder URL dedupe userOrder contract
## Summary
`GroupSeeder.seedPresets` may skip duplicate subscription URLs, but `userOrder` must still come from the original preset position, not from the count of inserted groups.
## Key Points
- URL deduplication should skip only duplicate URLs while still allowing valid distinct presets.
- `userOrder` is based on the preset index from the input list.
- Refactors must preserve `forEachIndexed` when code still depends on `index`.
- Kotlin labels must match the lambda after refactor, such as `return@forEachIndexed`.
## Details
The red `dev` CI on 2026-05-30 looked like multiple failures across `buildSrc`, `assembleDebug`, `Tests - app`, and `Tests - singbox + extra modules`, but the shared compile blocker was in `GroupSeeder.kt`. A deduplication refactor had removed `forEachIndexed` while the body still used `index`.

The first fix restored `forEachIndexed`, but CI then exposed the remaining label mismatch: `return@forEach` still referred to the old lambda form. The final fix changed it to `return@forEachIndexed` and kept the new `seenUrls` guard.

The behavioral contract is also important. Deduplication must not compress ordering. If the second preset is skipped as a duplicate URL, a later inserted preset must keep a `userOrder` derived from its original preset position, not from how many groups were inserted before it. A regression test was added to pin this behavior.
## Related Concepts
- [[concepts/singbox-karing-json-import-parity]]
- [[concepts/ci-grouped-job-failure-attribution]]
- [[concepts/regression-test-bounded-waits]]
- [[concepts/kotlin-import-at-file-level-only]]
## Sources
- [[daily/2026-05-30]]: CI run `26686071641` failed across several jobs, with `GroupSeeder.kt` as the common blocker.
- [[daily/2026-05-30]]: The fix restored `forEachIndexed`, then corrected `return@forEach` to `return@forEachIndexed`.
- [[daily/2026-05-30]]: A regression test was added to ensure duplicate URL skipping does not change `userOrder` semantics.
