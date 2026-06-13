---
title: ByeDPI proxy lane test race synchronization
sources:
  - daily/2026-05-30.md
created: 2026-05-30
updated: 2026-05-30
---
# ByeDPI proxy lane test race synchronization

## Summary
ByeDPI wedged-lane recovery tests must synchronize with the proxy coroutine before verifying recovery behavior, because a successful mock probe can let `engine.start()` return before `startProxy` reaches the expected lane.

## Key Points
- A green push CI can still miss a flaky ByeDPI test that appears on the PR synthetic merge ref.
- If `socksProbe` returns success immediately, `engine.start()` may finish before the proxy coroutine executes the recovery path.
- Tests that assert `emergencyReset()` or lane rotation should wait for the second or third `startProxy` attempt before verification.
- Production `ByeDpiEngine` should not be changed when the evidence points to test synchronization rather than runtime logic.

## Details
PR CI for the `dev -> main` merge exposed a failure in `ByeDpiEngineTest.start rotates proxy lane when previous native job keeps proxy dispatcher occupied()`. The investigation found that the production path was not the immediate suspect: the test's mocked probe could complete early, while the background proxy coroutine had not yet reached `startProxy`.

The correct repair is to synchronize the test with the runtime point being asserted. This matches [[concepts/regression-test-bounded-waits]]: tests should use explicit bounded waits for real asynchronous events, not assume that a returned public API call means all internal coroutines have completed. It also reinforces [[concepts/byedpi-wedged-lane-generation-restart]], where lane rotation and generation guards are contract behavior that tests must observe at the right time.

Related tests with similar async structure need the same scrutiny, especially scenarios like restarting after a wedged stop when job references were cleared. Detekt size failures from adding race tests should be handled by splitting tests by scenario class, not suppressing the rule.

## Related Concepts
- [[concepts/byedpi-wedged-lane-generation-restart]]
- [[concepts/byedpi-wedged-lane-restart-isolation]]
- [[concepts/regression-test-bounded-waits]]
- [[concepts/pr-ci-push-vs-pull-request-drift]]

## Sources
- [[daily/2026-05-30]]: PR #79 failed only in `Tests — engine-urnetwork + engine-byedpi` on a ByeDPI wedged proxy lane test.
- [[daily/2026-05-30]]: The diagnosis kept production code unchanged and fixed the test by waiting for the expected `startProxy` attempt before verifying recovery calls.
