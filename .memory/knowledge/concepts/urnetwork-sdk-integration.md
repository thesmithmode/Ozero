---
title: "URnetwork SDK Integration"
aliases: [urnetwork-engine, urnetwork-aar, bringyour-sdk]
tags: [engine, urnetwork, integration, native, go]
sources:
  - "daily/2026-05-01.md"
created: 2026-05-01
updated: 2026-05-01
---

# URnetwork SDK Integration

URnetwork is a P2P VPN engine integrated into Ozero via two AAR artifacts: `userwireguard.aar` (WireGuard transport layer) and the URnetwork SDK AAR (`com.bringyour.sdk.*` classes). The SDK AAR provides client lifecycle, authentication, and network management. Integration required 4 build iterations to produce a working AAR with the full API surface.

## Key Points

- Two AAR artifacts required: `userwireguard.aar` (WireGuard transport) + URnetwork SDK AAR (client API)
- AARs are placed in `engine-urnetwork/libs/` for local consumption
- SDK AAR must be built by binding `gomobile bind` directly against the `sdk` package — a thin wrapper approach failed (exported only `Version`)
- `build-tools/Dockerfile` requires explicit `ANDROID_CMDLINE_TOOLS_SHA256` env variable — undocumented, breaks all AAR CI jobs when missing
- As of 2026-05-01: AAR built and inspected with real classes, but `RealUrnetworkSdkBridge` not yet written — engine remains stub in DI graph
- Task decomposition: 1-SP micro-steps for remaining integration (RealBridge → DI switch → smoke test)

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

### Remaining Integration Work

The AAR is built, but the engine is still a stub in the Hilt DI graph:

1. **RealUrnetworkSdkBridge** — Implement using real `com.bringyour.sdk.*` classes from the AAR (start/stop client, auth flow, network selection)
2. **DI switch** — `UrnetworkModule`: swap `StubUrnetworkSdkBridge` → `RealUrnetworkSdkBridge`
3. **Smoke test** — Device test with real URnetwork credentials

Each step is decomposed as a 1-SP micro-task per project workflow discipline.

### WARP Engine Note

During the same session, WARP engine timeout was increased from 15s to 30s as a quick fix. The `warp-gen1.vercel.app` fallback integration was deferred to a future session.

## Related Concepts

- [[concepts/gomobile-bind-gotchas]] - All four build iteration failures relate to gomobile bind traps documented here
- [[concepts/vpn-engine-pipeline]] - URnetwork will plug into the engine pipeline as another selectable engine
- [[concepts/xray-aar-build-research]] - Same Dockerfile infrastructure and gomobile pattern used for Xray AAR

## Sources

- [[daily/2026-05-01.md]] - URnetwork SDK 4-iteration build, userwireguard.aar success, Dockerfile SHA env trap, remaining integration tasks identified
