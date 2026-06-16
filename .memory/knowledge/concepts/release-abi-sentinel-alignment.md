---
title: "Release ABI sentinel alignment"
sources:
  - "daily/2026-05-18.md"
created: 2026-06-12
updated: 2026-06-12
---
# Release ABI sentinel alignment

## Key Points
- Release sentinels must match the current `abiFilters`, not a copied historical multi-ABI template.
- Ozero release APKs are arm64-v8a only; a sentinel that expects `armeabi-v7a` or `x86_64` can fail a correct release.
- `libmtg.so` exposed this drift because its release.yml check still required three ABIs while other native checks were already arm64-only.
- Build configuration changes should be mirrored in project instructions and release assertions in the same review.

## Details

The v0.1.1 release failed because the `libmtg.so` sentinel in `release.yml` checked for `armeabi-v7a` even though commit `5d35fc4b` had already constrained the APK to `arm64-v8a` only. The native libraries `libhev`, `libbyedpi`, and `libam` already had arm64-only checks, so the release failure was not a packaging regression; it was a stale sentinel.

The fix was to align the `libmtg.so` assertion with the actual APK ABI surface and update the project invariant that still described three ABIs. This belongs with [[concepts/release-process]] and [[concepts/new-engine-module-ci-checklist]] because new native binaries need lockfile coverage, packaging checks, and ABI assertions that all describe the same artifact.

This also reinforces [[concepts/ci-workflow-discipline]]: a CI or release sentinel is only useful when it asserts the intended contract. A copied sentinel can become a blocker even when the produced APK is correct.

## Related Concepts
- [[concepts/release-process]]
- [[concepts/new-engine-module-ci-checklist]]
- [[concepts/ci-engine-module-missing-tests]]
- [[concepts/extract-native-libs-legacy-packaging]]

## Sources
- [[daily/2026-05-18]]: Session 10:41 records that release v0.1.1 failed because `libmtg.so` was checked for three ABIs while the APK intentionally contained only `arm64-v8a`.
- [[daily/2026-05-18]]: Session 10:41 records the decision to make the `libmtg` sentinel arm64-only and update the stale ABI invariant.
