---
title: Extra module CI exposes stale fakes
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---

# Extra module CI exposes stale fakes

## Key Points
- Wiring previously skipped module tests into CI turns false-green risk into concrete failures.
- The first failures are often stale test infrastructure, not new product regressions.
- Fake persistence and fake command transports are common hotspots because they encode production-like invariants informally.
- This connection links [[concepts/ci-extra-modules-test-gate]], [[concepts/fake-dao-preseed-autoincrement]], and [[concepts/masterdns-fake-ssh-specificity]].

## Details

The 2026-05-28 CI expansion demonstrated a recurring pattern: once skipped modules were added to GitHub Actions, CI immediately surfaced stale fake behavior and stale assertions. The failures were useful because they proved the previous green state was incomplete: tests existed, but the main CI path did not exercise them.

Two examples show why extra-module CI is more than task wiring. `FakeSubscriptionGroupDao` violated autoincrement identity after manual preseed, and `FakeSshTransport` used broad first-substring matching that made general shell fragments override specific deployment commands. Both issues were test infrastructure defects revealed by running dormant tests, and both needed precise fixes so the new CI job would become a trustworthy gate rather than a noisy one.

## Related Concepts
- [[concepts/ci-extra-modules-test-gate]]
- [[concepts/ci-module-test-coverage-gap]]
- [[concepts/fake-dao-preseed-autoincrement]]
- [[concepts/masterdns-fake-ssh-specificity]]

## Sources
- [[daily/2026-05-28]] records that the new extra-modules CI job exposed stale failures in sing-box and MasterDNS-related tests.
- [[daily/2026-05-28]] records the concrete fake fixes: DAO autoincrement after preseed and longest-substring SSH response matching.
