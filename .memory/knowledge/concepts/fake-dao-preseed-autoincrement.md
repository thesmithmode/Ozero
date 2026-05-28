---
title: Fake DAO preseed autoincrement
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---

# Fake DAO preseed autoincrement

## Key Points
- Fake DAOs that emulate autoincrement must advance their next ID past manually preseeded records.
- Otherwise, later inserts can reuse an existing ID and overwrite seeded state.
- The issue was exposed only after extra module tests were wired into CI.
- It is a concrete example of hidden test debt from [[concepts/ci-extra-modules-test-gate]] and [[concepts/ci-module-test-coverage-gap]].

## Details

The 2026-05-28 extra-modules CI run exposed stale failures in `GroupSeederTest`. The failure was traced to `FakeSubscriptionGroupDao`: the fake allowed tests to preseed rows with explicit IDs, but its generated next ID did not account for those rows. A later insert could therefore collide with a preseeded ID and replace existing test data.

The reusable rule is that persistence fakes must preserve the production invariant they emulate. If a fake supports both manual preseed and generated IDs, preseed must move the generator forward or the insert path must calculate the next ID from current contents. Without that, tests can fail for fake-only reasons or, worse, pass while hiding ordering and identity bugs.

## Related Concepts
- [[concepts/ci-extra-modules-test-gate]]
- [[concepts/ci-module-test-coverage-gap]]
- [[concepts/test-tautology-always-green]]
- [[concepts/singbox-subscription-architecture]]

## Sources
- [[daily/2026-05-28]] records that `GroupSeederTest` failed after hidden module tests were enabled.
- [[daily/2026-05-28]] records the root cause: `FakeSubscriptionGroupDao` autoincrement did not account for manually preseeded groups.
