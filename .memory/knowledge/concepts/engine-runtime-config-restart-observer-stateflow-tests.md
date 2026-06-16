---
title: Engine runtime config restart observer StateFlow tests
sources:
  - daily/2026-06-01.md
created: 2026-06-02
updated: 2026-06-02
---

# Engine runtime config restart observer StateFlow tests

## Key Points
- Tests for `EngineRuntimeConfigRestartObserver` should model real providers as state/baseline flows, not replayless hot flows.
- Replayless `MutableSharedFlow` in restart observer tests can miss emissions and create flaky coroutine timing failures.
- `start()` can accept a nullable lifecycle for JVM unit tests while production still passes a real lifecycle.
- Branch coverage should include lifecycle-null, lifecycle path, connecting state, idle baseline, adopted FPTN baseline, and pending fingerprint updates.

## Details

The runtime restart observer consumes provider flows that behave like state sources: DataStore maps, active slot maps, and config maps expose current values and baseline updates. Tests built on `MutableSharedFlow` without replay did not match that behavior and could race between subscription and emission, especially under coroutine-test dispatchers.

The durable testing pattern is to use `MutableStateFlow` for provider fixtures, keep Android lifecycle optional for unit-level coverage, and isolate the lifecycle branch in a dedicated test setup. This keeps the observer's state-machine coverage meaningful while avoiding JVM failures from Android main looper dependencies.

## Related Concepts
- [[concepts/settings-restart-baseline-debounce-state-machine]]
- [[concepts/runtime-restart-pending-fingerprint-baseline]]
- [[concepts/runTest-backgroundscope-hot-flow-collectors]]
- [[concepts/engine-runtime-config-provider-boundary]]

## Sources
- [[daily/2026-06-01]]: Session at 22:36 records the decision to rewrite restart observer tests from `MutableSharedFlow` to `MutableStateFlow`.
- [[daily/2026-06-01]]: Session at 22:36 records the nullable lifecycle decision and the branch coverage targets for restart observer testing.
