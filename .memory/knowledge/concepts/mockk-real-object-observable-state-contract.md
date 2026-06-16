---
title: MockK Real Object Observable State Contract
sources:
  - [[daily/2026-06-04]]
created: 2026-06-04
updated: 2026-06-04
---
# MockK Real Object Observable State Contract

## Key Points
- MockK `verify` must not be used on a real object that was not created as a mock or spy.
- For real collaborators, tests should assert observable contract or state transitions.
- In `StartSequenceCoordinatorExtraTest`, checking `TunnelController` state `Idle` was the correct replacement for `verify` on the real controller.
- This keeps tests behavioral and avoids false failures from mocking mechanics.

## Details

The 2026-06-04 CI work exposed a new test failure in `common-vpn`: `StartSequenceCoordinatorExtraTest` attempted `verify` on a real `TunnelController`, which produced a MockK error instead of testing the product behavior. The fix was to observe the controller state and assert the expected `Idle` state.

This pattern matters for coordinator and lifecycle tests where some dependencies are intentionally real to exercise orchestration. Mocking assertions should stay on mocks; real components should be checked through public state, emitted events, or other observable effects.

## Related Concepts
- [[concepts/common-vpn-split-start-and-shutdown-branch-coverage]]
- [[concepts/regression-test-bounded-waits]]
- [[concepts/test-tautology-always-green]]
- [[concepts/ci-current-run-batch-failure-triage]]

## Sources
- [[daily/2026-06-04]]: sessions 21:17 and 21:35 describe replacing MockK `verify` on a real `TunnelController` with an observable `Idle` state assertion.
