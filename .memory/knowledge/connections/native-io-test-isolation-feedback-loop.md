---
title: "Native and IO Test Isolation Feedback Loop"
sources:
  - "daily/2026-05-10.md"
created: 2026-06-09
updated: 2026-06-09
---

# Native and IO Test Isolation Feedback Loop

The 2026-05-10 CI fixes show one shared testing boundary across URnetwork and ByeDPI: unit tests must not directly touch native-backed SDK classes or real IO probes. Both failures looked engine-specific, but both were caused by tests crossing infrastructure boundaries that should be wrapped and injected.

## Key Points

- URnetwork tests failed by mocking `ConnectLocation`, which loaded native-backed AAR classes.
- ByeDPI tests flaked by using static real SOCKS probes and mock servers.
- Wrappers and injectable contracts are the common fix, not larger timeouts or broader mocks.
- CI load makes these boundary violations visible because native libraries and real-time scheduling differ from local runs.
- The pattern connects [[concepts/mockk-aar-native-initializer-trap]] and [[concepts/byedpi-connection-probe-injection-contract]].

## Details

URnetwork and ByeDPI failed for different immediate reasons. URnetwork hit `UnsatisfiedLinkError` and cascading class initialization failures when `mockk<ConnectLocation>()` loaded a native-backed AAR class. ByeDPI hit readiness flakes because unit tests used real socket probes and mock-server daemon threads under CI scheduler load.

The common design rule is the same: unit tests should target app-level contracts, not native SDK classes or real network machinery. Native classes should be converted into lightweight wrapper models at bridge boundaries, and IO operations should be injected as interfaces or suspend lambdas.

This connection matters during future CI triage because symptom-level fixes can look tempting. Increasing timeouts may reduce ByeDPI flakes without removing real IO. Reordering tests may hide URnetwork class loading without removing the native type from test code. The durable fix is to enforce wrapper/injection boundaries.

## Related Concepts

- [[concepts/mockk-aar-native-initializer-trap]] - Native-backed AAR classes must not be mocked directly in unit tests.
- [[concepts/byedpi-connection-probe-injection-contract]] - ByeDPI SOCKS probing belongs behind an injectable contract.
- [[concepts/byedpi-mock-server-ci-fragility]] - Real mock servers and real clock loops created earlier ByeDPI CI flakes.
- [[concepts/ci-current-run-batch-failure-triage]] - Multiple independent CI roots can coexist in the same red run.

## Sources

- [[daily/2026-05-10.md]] - Session 14:00: URnetwork failed from `mockk<ConnectLocation>()`; ByeDPI failed from real-time SOCKS readiness probing under CI load.
- [[daily/2026-05-10.md]] - Session 14:48: structural ByeDPI fix extracted an injectable probe and removed real IO from unit tests.
