---
title: Extra CI gates expose latent fake and coverage failures
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---
# Extra CI gates expose latent fake and coverage failures

## Key Points
- Adding skipped module tests to CI often reveals stale fakes before it reveals production bugs.
- Coverage gates can fail on branch coverage even when line coverage is already sufficient.
- The correct response is to fix the hidden test infrastructure or missing branches, not remove tests by default.
- Module type must be verified before wiring Gradle tasks into CI.

## Details

The new extra-modules CI job uncovered several hidden problems after previously skipped module tests started running. `shared-warp-settings` was first wired with an Android unit-test task even though it is a Kotlin library module, and later failed branch coverage while line coverage passed. MasterDNS tests failed because fake SSH command matching used broad substrings, and subscription tests exposed fake DAO id collisions after manual preseed.

This connects the general false-green CI problem with concrete stale fake behavior. Expanding CI scope is only trustworthy after the resulting failures are treated as evidence: wrong Gradle task names, fake matcher specificity, fake DAO autoincrement, and coverage branch gaps each had to be fixed before the new gate could be considered meaningful.

## Related Concepts
- [[concepts/ci-extra-modules-test-gate]]
- [[connections/extra-module-ci-exposes-stale-fakes]]
- [[concepts/masterdns-fake-ssh-specificity]]
- [[concepts/fake-dao-preseed-autoincrement]]
- [[concepts/shared-warp-settings-branch-coverage]]

## Sources
- [[daily/2026-05-28]] records that adding extra module tests exposed wrong Gradle task wiring for `shared-warp-settings`.
- [[daily/2026-05-28]] records that MasterDNS fake SSH matching and subscription fake DAO id generation were corrected after the expanded CI job failed.
- [[daily/2026-05-28]] records that `shared-warp-settings` needed branch-focused tests because branch coverage, not line coverage, blocked the gate.
