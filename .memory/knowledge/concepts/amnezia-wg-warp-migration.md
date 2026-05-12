---
title: "AmneziaWG Integration for WARP Engine"
aliases: [warp-awg-migration, wireguard-replacement, awg-warp]
tags: [warp, amneziawg, engine, architecture]
sources:
  - "daily/2026-05-04.md"
  - "daily/2026-05-07.md"
  - "daily/2026-05-08.md"
created: 2026-05-04
updated: 2026-05-08
---

# AmneziaWG Integration for WARP Engine

Ozero's WARP engine was refactored to use AmneziaWG2 instead of the Wireguard library, replacing `com.wireguard.android:tunnel` with `com.zaneschepke:amneziawg-android:2.3.7`. This change required structural expansion of the WarpConfig to accommodate AmneziaWG-specific parameters (H-values for MTU, jitter, packet obfuscation) and a rearchitecture of the WARP SDK bridge abstraction.

## Key Points

- **Phase 1 (2026-05-04)**: Replaced `com.wireguard.android:tunnel` with `com.zaneschepke:amneziawg-android:2.3.7`; WarpConfig expanded 8→19 keys; `AwgBackend` abstraction
- **Phase 2 (2026-05-08)**: Maven `libam-go.so` (sha256=`cc119dbc`, 8589456B) ≠ PORTAL_WG v1.4.3 binary (sha256=`2ebc0ee9`, 8578640B); migrated to checked-in SO from PORTAL_WG
- DataStore schema expanded from 8 keys to 19 keys — AWG H-values stored as String (no `longPreferencesKey` support in Amnezia)
- RealWarpSdkBridge refactored to use `AwgBackend` internal interface, decoupling from GoBackend for testability
- AWG obfuscation (Jc=5, Jmin=100, Jmax=200, H1-H4) required for Russian ISPs with TSPU — vanilla WireGuard blocked

## Details

### WarpConfig Structure

The WarpConfig was previously limited to standard WireGuard parameters: private key, public key, endpoint address. AmneziaWG introduces additional configuration through H-values (mtu_h, jitter_h, packet_obfuscation_h, and others). These parameters control MTU obfuscation, jitter injection, and packet reordering — techniques for evading DPI inspection without requiring a separate DPI engine.

The WarpConfig object was expanded to include:
- Original WireGuard fields (keys, endpoint, addresses)
- New AwgParams struct containing H-values
- DNS servers (existing but now explicit)
- Keepalive interval

### DataStore Schema Migration

Previously, WARP settings used 8 DataStore keys, mapping directly to connection parameters. The expansion to 19 keys reflects the new H-value count. A critical discovery during implementation: Amnezia does not support `LongPreferencesKey` for storing H-values as 64-bit integers. Instead, all H-values are stored as String representations of hex or decimal values, requiring type conversion during config instantiation.

### WarpIniBuilder and Parsing

A new `WarpIniBuilder` class was introduced to parse WireGuard INI configuration format and extract both standard parameters and AmneziaWG-specific headers. The builder handles:
- Section-based INI parsing (`[Interface]`, `[Peer]`)
- Extraction of H-value lines (`mtu_h`, `jitter_h`, etc.)
- DNS server lines from the `DNS` field
- Keepalive value from the `PersistentKeepalive` field

### RealWarpSdkBridge Refactoring

The bridge was refactored to use an internal `AwgBackend` interface, abstracting away the GoBackend (`GoBackend.awgTurnOn`, `GoBackend.awgTurnOff`). This abstraction allows test code to mock `AwgBackend` without depending on the Go JNI layer, and enables future switching to different Amnezia implementations without bridge changes.

### Test Coverage

Phase 1 tests:
- WarpIniBuilderTest (13 cases): INI parsing, H-value extraction, DNS server parsing
- WarpAutoConfigTest (+8 cases): config URL parsing and WarpConfig instantiation
- WarpConfigStoreTest (+3 cases): DataStore key management
- RealWarpSdkBridgeTest (10 cases): start/stop lifecycle with mocked AwgBackend
- HttpUrlConnectionClientTest (7 cases): HTTP client with timeout handling
- `WireguardKeyPairGeneratorTest` deleted alongside `WireguardKeyPairGenerator.kt` (dead code — never called)

Phase 2 additions:
- `AmneziaWgRuntimeBinaryTest`: verifies packaged `libam-go.so` SHA256 matches PORTAL_WG reference (`2EBC0EE9...`)
- `NoMavenAmneziawgTest`: sentinel — `zaneschepke` must not appear in gradle/libs.versions.toml
- `release.yml` step: assert libam-go.so SHA256 before publishing APK

### Phase 2: Runtime Binary Migration (2026-05-08)

After Phase 1, WARP continued crashing under AWG obfuscation configs containing I1 field. SHA256 comparison identified the root cause: the Maven zaneschepke binary differs from the PORTAL_WG v1.4.3 binary despite identical version labels. All prior Kotlin-side fixes (raw INI passthrough, awgTurnOn guards) addressed symptoms; the native runtime itself was incompatible.

Migration approach:
1. Remove `implementation(libs.amneziawg.android)` from `engine-warp/build.gradle.kts`
2. Copy `libam-go.so`, `libam.so`, `libam-quick.so` from PORTAL_WG v1.4.3 → `engine-warp/src/main/jniLibs/arm64-v8a/`
3. Add Java glue: `GoBackend` (7 native methods), `ProxyGoBackend` (6 native methods), `SocketProtector` (interface) — all required by `RegisterNatives` in `JNI_OnLoad` (see [[concepts/amneziawg-jni-classpath-completeness]])
4. Restrict ABI to arm64-v8a (`ndk { abiFilters += listOf("arm64-v8a") }`) — PORTAL_WG provides no x86_64 binary
5. Add `.gitignore` exception `!engine-warp/src/main/jniLibs/**/*.so` (Python template `*.so` rule silently blocked tracking — see [[concepts/gitignore-jnilibs-conflict]])
6. SHA256 sentinel test + `release.yml` assertion gate against future binary regression

Pre-JNI `PersistentLoggers.warn` checkpoints added before/after every `awgTurnOn`/`awgTurnOff`/`loadOnce` call in `RealWarpSdkBridge` — eliminates pre-SIGSEGV diagnostic silence.

## Related Concepts

- [[concepts/vpn-engine-pipeline]] - WARP is one engine in the pipeline, now with AmneziaWG as the underlying tunnel
- [[concepts/warp-config-generator-api]] - Config parsing depends on correct API response handling
- [[concepts/tun-mtu-dual-layer]] - AmneziaWG H-values provide another layer of MTU configuration
- [[connections/dependency-override-masking]] - Dependency management for amneziawg-android

## Sources

- [[daily/2026-05-04.md]] - Session 12:05: AWG WARP swap completed; WarpConfig expansion, DataStore schema 8→19 keys, AwgBackend abstraction, 49 new tests added; CI green on commit 1077a02
- [[daily/2026-05-07.md]] - AWG obfuscation requirement confirmed for Russian ISPs (TSPU blocks vanilla WG); forceVanilla reverted; AwgParams() ≠ VANILLA confusion documented
- [[daily/2026-05-08.md]] - Session 12:05/12:18: Maven libam-go.so sha256 mismatch discovered; Phase 2 migration to PORTAL_WG SO committed; Java glue added; SHA256 sentinel + release.yml assertion; WireguardKeyPairGenerator dead code deleted
