---
title: "URnetwork SDK Integration"
aliases: [urnetwork-engine, urnetwork-aar, bringyour-sdk]
tags: [engine, urnetwork, integration, native, go]
sources:
  - "daily/2026-05-01.md"
  - "daily/2026-05-02.md"
  - "daily/2026-05-05.md"
created: 2026-05-01
updated: 2026-05-05
---

# URnetwork SDK Integration

URnetwork is a P2P VPN engine integrated into Ozero via two AAR artifacts: `userwireguard.aar` (WireGuard transport layer) and the URnetwork SDK AAR (`com.bringyour.sdk.*` classes). The SDK AAR provides client lifecycle, authentication, and network management. Integration required 4 build iterations to produce a working AAR with the full API surface.

## Key Points

- Two AAR artifacts required: `userwireguard.aar` (WireGuard transport) + URnetwork SDK AAR (client API)
- AARs are placed in `engine-urnetwork/libs/` for local consumption
- SDK AAR must be built by binding `gomobile bind` directly against the `sdk` package — a thin wrapper approach failed (exported only `Version`)
- `build-tools/Dockerfile` requires explicit `ANDROID_CMDLINE_TOOLS_SHA256` env variable — undocumented, breaks all AAR CI jobs when missing
- As of 2026-05-02: `RealUrnetworkSdkBridge` written, consent system removed entirely, `EngineUrnetwork.stop()` leak fixed
- `getNetworkSpace(key)` returns null on first run — must call `importNetworkSpaceFromJson` to create (see [[concepts/urnetwork-networkspace-init]])

## Details

### Build Pipeline

The URnetwork SDK AAR build went through 4 iterations:

1. **gomobile/bind dependency missing** — `golang.org/x/mobile/bind` not in the module graph. Build failed with a cryptic error. Fix: explicit `go get`.
2. **Wrapper package trap** — A thin Go wrapper importing `github.com/urnetwork/sdk` was created to simplify the binding target. However, the wrapper only defined `func Version() string`, so the generated AAR contained only a `Version` class. None of the SDK's real types were accessible.
3. **javac encoding failure** — Binding directly against the SDK package revealed non-ASCII characters in Go source comments. `javac` failed during the AAR packaging step.
4. **Success** — After addressing encoding, the direct bind produced an AAR with real `com.bringyour.sdk.*` classes (client, auth, network management types).

The `userwireguard.aar` built successfully on the first attempt using the existing gomobile pipeline.

### Dockerfile Infrastructure

The `build-tools/Dockerfile` used for all AAR builds (ByeDPI, AmneziaWG, Hysteria2, URnetwork) requires an `ANDROID_CMDLINE_TOOLS_SHA256` environment variable that is not documented anywhere. When omitted, the Docker build fails at the SDK tools download step. This was discovered during the URnetwork build session and affects all AAR CI jobs.

### Integration Progress (2026-05-02)

The engine progressed from stub to partially functional:

1. **RealUrnetworkSdkBridge** — Implemented with `start()`/`stop()`/`isRunning` using real `com.bringyour.sdk.*` classes
2. **Consent system removed** — The consent permission gate (`UrnetworkConsentStore`, `UrnetworkModule` consent params) was deleted entirely rather than fixed. The auto-grant workaround was rejected as symptom-patching (see [[connections/symptom-fix-vs-system-removal]])
3. **stop() leak fixed** — `EngineUrnetwork.stop()` was not calling `sdkBridge.stop()`, leaving Go goroutines alive. Fixed: `stop()` now calls `sdkBridge.stop()` which calls `networkSpaceManager?.close()`
4. **NetworkSpace init** — First-run null from `getNetworkSpace` handled via `importNetworkSpaceFromJson` (see [[concepts/urnetwork-networkspace-init]])

### Runtime Failures (2026-05-05)

Two SIGABRT causes identified during device testing:

1. **env="prod" vs env="main"** — production URnetwork environment identifier is `"main"`, not `"prod"`. Using wrong env causes Go SDK abort on guest network creation. See [[concepts/urnetwork-networkspace-bundle-fields]].

2. **Missing bundle fields** — `linkHostName`, `migrationHostName`, `wallet` must be set via `updateNetworkSpace()` after `importNetworkSpaceFromJson` but before `networkCreate`. Omitting them causes SIGABRT. Reference values sourced from `.claude/Контекст/android`.

The SIGABRT appeared after Nubia-guard removal (commit `47d0156`) — the guard was masking the underlying misconfiguration.

## Remaining Work

- CI verification of full fix chain (env + bundle fields)
- `libgojni.so` ~28MB in APK — size impact assessment pending

## Related Concepts

- [[concepts/gomobile-bind-gotchas]] - All four build iteration failures relate to gomobile bind traps documented here
- [[concepts/vpn-engine-pipeline]] - URnetwork plugs into the engine pipeline as another selectable engine
- [[concepts/xray-aar-build-research]] - Same Dockerfile infrastructure and gomobile pattern used for Xray AAR
- [[concepts/urnetwork-networkspace-init]] - First-run initialization flow for NetworkSpace creation
- [[connections/symptom-fix-vs-system-removal]] - Consent removal decision pattern

## Sources

- [[daily/2026-05-01.md]] - URnetwork SDK 4-iteration build, userwireguard.aar success, Dockerfile SHA env trap, remaining integration tasks identified
- [[daily/2026-05-02.md]] - Consent system removed, stop() leak fixed, NetworkSpace init flow discovered via bytecode introspection, RealBridge implemented
