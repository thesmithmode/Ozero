---
title: Coverage security defensive branches test contract
sources:
  - daily/2026-06-02.md
created: 2026-06-02
updated: 2026-06-02
---

# Coverage security defensive branches test contract

## Key Points
- Security-sensitive defensive branches belong in coverage and should be tested instead of excluded.
- Provider exception paths in crypto verification should return safe failure values and be covered with targeted seams.
- Parser and formatter edge cases should be driven by JaCoCo misses rather than duplicated blindly.
- Test expectations must match intentional idempotent cleanup behavior, even when it means repeated calls.
- This contract complements [[concepts/jacoco-testable-logic-exclude-boundary]] and [[concepts/bootstrap-signature-real-trust-gate]].

## Details

The 2026-06-02 coverage work treated `common-crypto` defensive behavior as production-important logic. Branches in `SubscriptionVerifier` that handle provider exceptions such as `IllegalArgumentException`, `DataLengthException`, and `RuntimeException` should be covered with focused tests and return `false`, not hidden behind excludes.

The same rule applies to parser and formatter edge cases in singbox-related modules. Existing parser edge tests should not be duplicated without a fresh JaCoCo miss map, but uncovered branch-heavy code such as formatters, share-link parsers, lock-file parsing, binary downloader helpers, and subscription helpers should be covered behaviorally when the report identifies gaps.

The session also corrected a `common-vpn` expectation: production correctly calls `statsLogger.cancel()` twice for idempotent cleanup. The regression test should assert the intended shutdown contract rather than simplify the code to satisfy a too-narrow expectation.

## Related Concepts

- [[concepts/jacoco-testable-logic-exclude-boundary]]
- [[concepts/jacoco-exclude-evidence-boundary]]
- [[concepts/bootstrap-signature-real-trust-gate]]
- [[concepts/singbox-vless-early-data-string-contract]]

## Sources

- [[daily/2026-06-02]]: Session 19:28 decided not to exclude crypto/parser defensive branches from coverage because they are security-sensitive behavior.
- [[daily/2026-06-02]]: Session 19:28 specified an internal test seam for `SubscriptionVerifier` provider exceptions returning `false`.
- [[daily/2026-06-02]]: Session 19:28 recorded the corrected `common-vpn` expectation that `statsLogger.cancel()` is called twice for idempotent shutdown.
