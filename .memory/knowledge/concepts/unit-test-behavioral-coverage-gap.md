---
title: "Unit Test Behavioral Coverage Gap"
aliases: [fake-coverage-gap, behavioral-coverage, integration-miss, coverage-theater]
tags: [testing, architecture, android, gotcha]
sources:
  - "daily/2026-05-09.md"
created: 2026-05-09
updated: 2026-05-09
---

# Unit Test Behavioral Coverage Gap

High line/branch coverage achieved through unit tests with fakes and mocks does not guarantee that real behavioral bugs — particularly race conditions, engine lifecycle ordering errors, and integration failures — are caught. Fakes implement the simplest contract-satisfying behavior; they do not reproduce the temporal, concurrent, or environmental characteristics of the real system. In Ozero v0.0.8, four production bugs were missed by 100% unit test coverage because all were behavioral: they depended on rapid engine switching, Go runtime guard deadlocks, and IP fetch timing — none of which fake implementations exercise.

## Key Points

- 100% line/branch coverage on `FakeIpInfoProvider`, `FakeEnginePlugin`, etc. — v0.0.8 shipped with 4 production bugs none of the tests caught
- Fakes implement the happy path in nanoseconds; real engines take seconds, acquire OS resources, and interact with Go runtimes
- Race conditions (GoRuntimeGuard deadlock, IP warmup cancelled on restart) require timing and concurrent actors — impossible to reproduce with synchronous fakes
- The behavioral gap is not a test quality problem — it is a structural limitation of isolated unit testing
- Coverage metrics signal syntax-level correctness; behavioral correctness requires integration or stress tests

## Details

### The Ozero v0.0.8 Case

Ozero maintained ≥95% line/branch/instruction coverage enforced by JaCoCo thresholds in `coverage.gradle.kts`. After the v0.0.8 release, four production bugs were reported simultaneously: IP display empty, URnetwork engine broken, split tunnel empty on entry, split tunnel ALL mode showing app list. All four traced to a single behavioral root — the engine-switch chain.

None were caught by the test suite because:

1. **GoRuntimeGuard deadlock** — `GoRuntimeGuardTest` correctly tested acquire/release/concurrent-threads, but the deadlock required `startVpn` to cancel the teardown coroutine (which calls `release`) while `acquire` was pending. No test exercised the engine-switch cancellation path.

2. **IP warmup cancellation** — `FakeIpInfoProvider` returns instantly. The real 3-second warmup delay that gets cancelled on each engine restart was never exercised. The test checked that IP was fetched; it never checked what happened when fetch was interrupted.

3. **Split tunnel empty flash** — Unit tests initialized `SplitTunnelViewModel` with `store.setRaw(...)` before the VM was constructed, giving it pre-populated state. The real app initialized with `Content(apps=[])` (empty list) before `loadApps` completed — a timing gap invisible to synchronous test setup.

4. **Split tunnel ALL mode** — A design decision (hide app list when mode=ALL) that no test enforced because the test suite focused on state transitions, not on rendered output.

### Why Fakes Cannot Reproduce This

A fake engine plugin's `start()` returns `StartResult.Started` in under 1 millisecond. It does not:
- Spawn Go goroutines that need protecting
- Hold a native TUN file descriptor that must be closed
- Block on `acquireGoRuntimeGuard()`
- Wait for a socket to become available on a port

The 7 rapid `startVpn` calls in 30 seconds produced a cascade of cancellations and partial teardowns that only emerges with real engines. The fake's instant-success behavior made the test pass; the real engine's async lifecycle made the app break.

### What Would Catch These

Behavioral bugs of this class require one of:

1. **Integration tests** — Tests that exercise real (or real-process) engine plugins through the full `OzeroVpnService` lifecycle, including cancellation and restart.

2. **Stress/scenario tests** — Tests that deliberately trigger rapid engine switching (N toggles in M seconds) and assert that connectivity is restored, IP is displayed, and the service is stable.

3. **Instrumented device tests** — Tests running on-device where the VPN service can be exercised with real network stack interactions.

The trade-off is cost: unit tests run in milliseconds and catch 90% of logic errors. Integration tests catch behavioral/timing errors but run in seconds to minutes and require emulators or devices.

The lesson from v0.0.8 is not that coverage thresholds are wrong — it is that coverage is a necessary but insufficient condition for correctness. The team's post-v0.0.8 response was to add stress scenario tests alongside the existing unit suite.

## Related Concepts

- [[concepts/engine-switch-chain-cascading-failures]] - The v0.0.8 bugs that exposed this gap; all four bugs were behavioral
- [[concepts/test-tautology-always-green]] - Related failure mode: tests that assert the wrong thing; behavioral gap is different — tests assert correctly on fakes that don't reproduce the bug
- [[concepts/byedpi-mock-server-ci-fragility]] - Another case where test infrastructure (repeat=N mock server) failed to reproduce real CI load conditions
- [[concepts/stateIn-eagerly-test-trap]] - Eagerly-initialized StateFlow in tests misses the race visible in real lifecycle

## Sources

- [[daily/2026-05-09.md]] - Session 13:12: "100% test coverage didn't catch any of these — unit tests on fakes miss behavioral/integration scenarios; FakeIpInfoProvider passes but real engine-switch race never tested"; advisor note: line/branch coverage ≠ behavioral coverage; GoRuntimeGuard deadlock and IP warmup cancellation missed entirely by test suite
