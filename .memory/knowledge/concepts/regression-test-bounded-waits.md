---
title: Regression tests need bounded waits
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---
# Regression tests need bounded waits

## Key Points
- Regression tests that use latches or blocking waits must set explicit real-time timeouts.
- An unbounded `CountDownLatch.await()` can hang an entire CI job and hide the actual regression signal.
- `runTest` virtual time can produce false timeout behavior for code that intentionally uses real drain windows.
- Restart-only fallback paths should not run on ordinary first-start paths in tests or production.

## Details

The ByeDPI regression test added during the Android release hardening initially used latch-style waiting. The daily log records that this was changed to a real-time `runBlocking` timeout so CI could fail terminally instead of hanging the job.

The same session also identified a `runTest` trap: virtual time can make drain and timeout logic fire differently from production. For ByeDPI, the drain dispatcher was limited to the restart path after an old `proxyJob`, because unconditional drain behavior created false timeouts during first-start test scenarios.

## Related Concepts
- [[concepts/runtest-uncompleted-coroutines-trap]]
- [[concepts/test-io-thread-zombie-trap]]
- [[concepts/byedpi-stop-timeout-contract]]
- [[concepts/engine-failure-recovery-isolation]]

## Sources
- [[daily/2026-05-28]] records that the ByeDPI regression test was rewritten from unbounded `CountDownLatch.await()` to a bounded real-time wait.
- [[daily/2026-05-28]] records that unconditional ByeDPI drain caused false timeout behavior in `runTest` and was limited to restart after an old `proxyJob`.
