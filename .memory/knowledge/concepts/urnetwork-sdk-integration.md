---
title: "URnetwork SDK Integration"
aliases: [urnetwork-engine, urnetwork-aar, bringyour-sdk]
tags: [engine, urnetwork, integration, native, go]
sources:
  - "daily/2026-05-01.md"
  - "daily/2026-05-02.md"
  - "daily/2026-05-05.md"
  - "daily/2026-05-12.md"
  - "daily/2026-05-20.md"
  - "daily/2026-05-23.md"
created: 2026-05-01
updated: 2026-05-23
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

### Peer Discovery Loss and Watchdog Recovery (2026-05-12)

Production log analysis (6-subagent diagnostic session) revealed that URnetwork peers drain after 4-5 minutes of connected operation. Peer count drops from 7+ to 0 with no re-discovery triggered by the Go SDK. Fix: `OzeroVpnService` calls `sdkBridge.recover()` before transitioning to Failed state, triggering SDK-level peer re-discovery without full VPN restart. See [[concepts/urnetwork-peer-watchdog-recovery]].

### UI Cleanup: Solana/URx/Wallet Removal (2026-05-12)

Solana, URx token, wallet address, and balance UI elements removed from URnetwork settings and strings.xml. VM pollers for `unpaidBytes` and `subscriptionBalance` retained (bridge methods exist) but UI no longer displays them. Ozero uses URnetwork as a pure P2P VPN engine without the cryptocurrency/payment layer.

### runStartOnMain Symmetry Fix (2026-05-20, v0.1.9)

The SDK device configuration must be applied symmetrically in both code paths that call `ensureDeviceOnMain` and `runStartOnMain`. Previously, the fix to apply all 12 device fields (`routeLocal`, `provideMode`, `providePaused`, `locationId`, `locationName`, `locationCountry`, etc.) from `localState` was only applied in `ensureDeviceOnMain` — the path called when the user opens the settings screen. The `runStartOnMain` path (called when `engine.start()` is invoked without the settings screen being open) still applied only `providePaused=true` (1 field). The SDK saw a device missing the other 11 fields and hid cities and regions from the location picker.

Fix: apply all 12 fields in `runStartOnMain` as well, or extract the field-application into a shared function called from both paths. Sentinel: a test that verifies symmetric 12-field application in both code paths.

### Country Switch UX (2026-05-12)

`switchingCountry` flag added to URnetwork ViewModel. When user selects a different exit country, StatusRow shows "Переключение страны…" and the engine performs soft peer re-discovery targeting the new country's relay nodes. Uses the watchdog `recover()` mechanism for non-destructive mesh refresh.

### Provider Identity Persistence (2026-05-23, commit cc9e3c67)

Root cause of 0 relay bytes despite successful VPN connections: `addProvideSecretKeysListener` was missing from `RealUrnetworkSdkBridge`. Without the listener, `initProvideSecretKeys()` generated a new keypair on every app restart. Each restart = new relay node identity in the URnetwork mesh → mesh routes 0 bytes to unknown node.

Upstream `DeviceManager.kt:121-130` registers the listener before `initProvideSecretKeys()` to save generated keys into `localState.provideSecretKeys`. The fix replicates this pattern. Additionally, `addJwtRefreshListener` was added to update `localState.byClientJwt` when the SDK auto-refreshes the JWT.

**Identity components are separate:**
- `byClientJwt` = billing identity (walletAuth-derived, stable after first engine start)
- `provideSecretKeys` = mesh identity (P2P relay node keypair, must persist across restarts)

See [[concepts/urnetwork-provide-secret-keys-identity]] for full analysis.

## Remaining Work

- TOFU host key verification for MasterDNS SSH (unrelated; see [[concepts/masterdns-deploy-hardening]])
- `libgojni.so` ~28MB in APK — size impact assessment pending

## Related Concepts

- [[concepts/gomobile-bind-gotchas]] - All four build iteration failures relate to gomobile bind traps documented here
- [[concepts/vpn-engine-pipeline]] - URnetwork plugs into the engine pipeline as another selectable engine
- [[concepts/xray-aar-build-research]] - Same Dockerfile infrastructure and gomobile pattern used for Xray AAR
- [[concepts/urnetwork-networkspace-init]] - First-run initialization flow for NetworkSpace creation
- [[connections/symptom-fix-vs-system-removal]] - Consent removal decision pattern
- [[concepts/urnetwork-peer-watchdog-recovery]] - Peer discovery loss and auto-recovery pattern
- [[concepts/urnetwork-provide-secret-keys-identity]] - provideSecretKeys persistence fix; listener registration order; mesh identity vs billing identity distinction

## Sources

- [[daily/2026-05-01.md]] - URnetwork SDK 4-iteration build, userwireguard.aar success, Dockerfile SHA env trap, remaining integration tasks identified
- [[daily/2026-05-02.md]] - Consent system removed, stop() leak fixed, NetworkSpace init flow discovered via bytecode introspection, RealBridge implemented
- [[daily/2026-05-12.md]] - Session 21:19: peer discovery loss after 4-5 min diagnosed; recover() watchdog added; Solana/URx/wallet UI removed; country switch UX with switchingCountry flag
- [[daily/2026-05-20.md]] - runStartOnMain symmetry fix: both runStartOnMain AND ensureDeviceOnMain must apply all 12 device fields; previous fix only covered ensureDeviceOnMain (settings path); engine.start() path stayed at 1 field → SDK hid cities/regions
- [[daily/2026-05-23.md]] - 5-subagent diagnostic proved 0 relay bytes; root cause addProvideSecretKeysListener missing → keypair regenerated each restart → new mesh identity → 0 routed bytes; also addJwtRefreshListener; commit cc9e3c67; 5 sentinel tests in RealUrnetworkSdkBridgeContractTest
