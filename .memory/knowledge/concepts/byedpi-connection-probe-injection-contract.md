---
title: "ByeDPI Connection Probe Injection Contract"
sources:
  - "daily/2026-05-10.md"
created: 2026-06-09
updated: 2026-06-09
---

# ByeDPI Connection Probe Injection Contract

ByeDPI readiness tests must not depend on the real `Socks5HandshakeProbe`, real `ServerSocket`, or daemon-thread timing. The connection probe is an IO dependency and belongs behind an injectable contract so production can use the real SOCKS5 handshake while unit tests use deterministic success and failure probes.

## Key Points

- Static `Socks5HandshakeProbe` usage made ByeDPI unit tests depend on real IO and CI scheduler timing.
- `ByeDpiConnectionProbe` separates production SOCKS5 probing from test-controlled probe behavior.
- Hilt binding should map `ByeDpiConnectionProbe` to `Socks5HandshakeProbe` for production.
- Unit tests should inject mock probes instead of starting real mock SOCKS servers.
- This complements [[concepts/byedpi-mock-server-ci-fragility]] and avoids another timing variant of the same CI flake family.

## Details

The 2026-05-10 ByeDPI CI failure showed that increasing timeouts was not enough when tests still touched real IO. `Socks5HandshakeProbe` used real dispatcher threads and real socket timing, so `runTest` virtual time could not make the test deterministic. Under CI load, daemon threads and socket accept loops could be delayed enough to produce false readiness failures.

The structural fix is to extract an injectable probe contract. Production keeps the real implementation, while tests inject a probe that returns success or throws a controlled exception. This moves the test assertion from "a local mock server happened to respond in time" to "the engine reacts correctly to probe success or failure."

The daily log also recorded a later implementation variant that used a suspend lambda with a default `Socks5HandshakeProbe::probe`. Both shapes encode the same contract: real IO must sit behind an injectable boundary, and unit tests must not allocate ports or rely on daemon-thread ordering.

## Related Concepts

- [[concepts/byedpi-mock-server-ci-fragility]] - Earlier ByeDPI SOCKS mock-server flakiness and timeout-loop fixes.
- [[concepts/tcp-probe-test-local-closed-port-contract]] - Controlled network-probe tests should avoid environmental assumptions.
- [[concepts/runtest-uncompleted-coroutines-trap]] - Coroutine tests must isolate long-running or real-time work from `runTest`.
- [[concepts/ci-failure-batch-analysis-before-push]] - CI flake fixes should be grounded in the full failing batch.

## Sources

- [[daily/2026-05-10.md]] - Session 14:48: `Socks5HandshakeProbe` as a static object made tests with real `ServerSocket` inherently flaky; decision was to extract `ByeDpiConnectionProbe` and bind it through Hilt.
- [[daily/2026-05-10.md]] - Session 15:04: final implementation used injectable `socksProbe` with deterministic success and failure lambdas in `ByeDpiEngineTest`.
