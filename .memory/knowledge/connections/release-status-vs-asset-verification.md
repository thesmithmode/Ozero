---
title: Release success must be verified by assets, not only workflow conclusion
sources:
  - daily/2026-05-27.md
created: 2026-05-28
updated: 2026-05-28
---
# Release Status Vs Asset Verification

## Key Points
- GitHub workflow conclusion can be failure while release artifacts are present.
- The release goal is user-visible assets, not just a green workflow badge.
- Always verify tag, release, and asset names after pipeline anomalies.
- CI fixes still matter, but release triage must separate build failure from post-job or infrastructure failure.

## Details

The 2026-05-27 release session showed a confusing state: v0.2.12 existed with APK, deb, EXE, and DMG assets even though the workflow still reported failure. This connected two existing lessons: asset names must be checked directly, and all-success or all-artifacts states can diverge from the workflow conclusion.

The practical diagnostic path is to inspect the failed run, but also independently run release verification via gh release view and asset checks. When all required artifacts exist, the release may be usable even if the workflow needs follow-up hardening for future signal clarity.

## Related Concepts
- [[concepts/github-workflow-failure-all-jobs-success]]
- [[concepts/github-release-asset-name-verification]]
- [[concepts/release-process]]

## Sources
- [[daily/2026-05-27.md]]: Session 00:08 recorded v0.2.12 release with all four artifacts despite a failing workflow conclusion.
