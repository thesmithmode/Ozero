---
title: Extra module CI exposes hidden release risk
sources:
  - [[daily/2026-05-28]]
created: 2026-05-31
updated: 2026-05-31
---

# Extra module CI exposes hidden release risk

## Summary

Adding CI coverage for modules that previously had tests but were not run can turn a false-green release gate into a real gate by exposing stale fakes, wrong Gradle tasks, and branch coverage gaps.

## Key Points

- The May 28 CI expansion found modules with tests that existed but were not started by the main CI job.
- `shared-warp-settings` required `:shared-warp-settings:test`, not Android `testDebugUnitTest`, because it is a Kotlin library module.
- New extra-module jobs exposed stale assertions and fake infrastructure in sing-box and MasterDNS modules.
- `app-desktop` tests stayed in CI, but desktop coverage verification was excluded from the Android release gate.
- This refines [[concepts/ci-extra-modules-test-gate]] and [[concepts/gradle-module-type-ci-task-selection]].

## Details

The user asked whether test coverage was enough and allowed adding integration or E2E tests if it strengthened CI. Investigation showed the bigger immediate gap was not missing tests but tests that were present and not wired into CI for modules such as singbox-related modules, MasterDNS, and shared WARP settings.

Once the extra-module job was added, the first failures were partly wiring issues and partly real latent problems. `shared-warp-settings` was a Kotlin library module, so the Android unit-test task was invalid. After that was corrected, CI exposed stale fake DAO ID generation, broad fake SSH substring matching, stale assertions, and branch coverage below the 95% threshold.

## Related Concepts

- [[concepts/ci-extra-modules-test-gate]]
- [[concepts/gradle-module-type-ci-task-selection]]
- [[concepts/fake-dao-preseed-autoincrement]]
- [[concepts/masterdns-fake-ssh-specificity]]

## Sources

- [[daily/2026-05-28]]: Sessions 18:24, 19:02, 19:33, and 19:39 recorded the discovery that several module tests were not being run and the resulting CI failures.
- [[daily/2026-05-28]]: The same sessions recorded the Kotlin library task correction, extra-module failures, and decision to keep desktop tests without making desktop coverage an Android release gate.
