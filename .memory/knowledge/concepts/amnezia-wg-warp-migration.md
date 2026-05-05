---
title: "AmneziaWG Integration for WARP Engine"
aliases: [warp-awg-migration, wireguard-replacement, awg-warp]
tags: [warp, amneziawg, engine, architecture]
sources:
  - "daily/2026-05-04.md"
created: 2026-05-04
updated: 2026-05-04
---

# AmneziaWG Integration for WARP Engine

Ozero's WARP engine was refactored to use AmneziaWG2 instead of the Wireguard library, replacing `com.wireguard.android:tunnel` with `com.zaneschepke:amneziawg-android:2.3.7`. This change required structural expansion of the WarpConfig to accommodate AmneziaWG-specific parameters (H-values for MTU, jitter, packet obfuscation) and a rearchitecture of the WARP SDK bridge abstraction.

## Key Points

- Replaced `com.wireguard.android:tunnel` dependency with `com.zaneschepke:amneziawg-android:2.3.7`
- WarpConfig expanded to include `AwgParams` (H-values, obfuscation settings) alongside original connection fields
- DataStore schema expanded from 8 keys to 19 keys — AWG H-values stored as String (no `longPreferencesKey` support in Amnezia)
- RealWarpSdkBridge refactored to use `AwgBackend` internal interface, decoupling from GoBackend for testability
- New test infrastructure: `mockwebserver` added to `:engine-warp` for config parsing tests

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

New tests cover:
- WarpIniBuilderTest (13 cases): INI parsing, H-value extraction, DNS server parsing
- WarpAutoConfigTest (+8 cases): config URL parsing and WarpConfig instantiation
- WarpConfigStoreTest (+3 cases): DataStore key management
- RealWarpSdkBridgeTest (10 cases): start/stop lifecycle with mocked AwgBackend
- WireguardKeyPairGeneratorTest (6 cases): key generation
- HttpUrlConnectionClientTest (7 cases): HTTP client with timeout handling

## Related Concepts

- [[concepts/vpn-engine-pipeline]] - WARP is one engine in the pipeline, now with AmneziaWG as the underlying tunnel
- [[concepts/warp-config-generator-api]] - Config parsing depends on correct API response handling
- [[concepts/tun-mtu-dual-layer]] - AmneziaWG H-values provide another layer of MTU configuration
- [[connections/dependency-override-masking]] - Dependency management for amneziawg-android

## Sources

- [[daily/2026-05-04.md]] - Session 12:05: AWG WARP swap completed; WarpConfig expansion, DataStore schema 8→19 keys, AwgBackend abstraction, 49 new tests added; CI green on commit 1077a02
