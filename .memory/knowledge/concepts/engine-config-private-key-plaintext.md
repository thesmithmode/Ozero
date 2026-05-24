---
title: "Engine Config Private Key Plaintext Storage"
aliases: [private-key-datastore, plaintext-key-risk, engine-security-p2]
tags: [security, datastore, warp, engine, p2-debt]
sources:
  - "daily/2026-05-04.md"
created: 2026-05-04
updated: 2026-05-04
---

# Engine Config Private Key Plaintext Storage

Engine configurations in Ozero store cryptographic private keys (e.g., WireGuard private keys for WARP) in Android DataStore as plaintext strings. This is a known P2 security debt identified during v0.0.2 code review. The keys are protected only by file-system-level Android app sandbox isolation, not by hardware-backed encryption.

## Key Points

- WARP engine stores WireGuard private keys in DataStore (`DataStoreWarpConfigStore`) as plain `stringPreferencesKey` entries
- DataStore is backed by a protobuf file in the app's private data directory, readable on rooted devices
- Android Keystore integration (encrypt-then-store) would provide hardware-backed protection on devices with a secure element
- This was classified P2 (known risk, non-blocking) during the v0.0.2 code review — does not block release but must be addressed
- Input validation for engine configs is a related P2 debt: no boundary checks on user-supplied parameters before they reach native layers

## Details

### Why DataStore Plaintext Is a Risk

Android's file-based app sandbox prevents other apps from reading DataStore files without root access. However, on rooted devices, full DataStore contents including private keys are accessible in plaintext. For a VPN app handling user identity and routing, private key exposure can enable traffic interception or identity spoofing.

The WireGuard private key stored in DataStore is used to authenticate the device to Cloudflare WARP servers. Compromise of this key allows impersonation of the device's WARP registration. Unlike JWT tokens (which can be revoked server-side), a compromised WireGuard private key continues to be valid until explicitly rotated.

### Mitigation Path

The standard Android mitigation is Android Keystore-backed encryption:

1. Generate a symmetric key in the Android Keystore (hardware-backed on devices with StrongBox/TEE)
2. Encrypt the private key before storing in DataStore
3. Decrypt on retrieval, keeping the plaintext in memory only for the duration of the operation

`EncryptedSharedPreferences` from `androidx.security:security-crypto` provides a ready-made implementation. For DataStore specifically, a custom `Serializer<T>` with Keystore-backed AES-GCM encryption is the standard approach.

### Input Validation P2 Debt

Alongside plaintext keys, the code review identified missing boundary validation for user-supplied engine configuration parameters (ports, addresses, timeout values) before they are passed to native layers (JNI, subprocess). Native code receiving out-of-range values can crash silently. This is a separate P2 debt requiring boundary guards at the Kotlin-to-native boundary for each engine.

## Related Concepts

- [[concepts/amnezia-wg-warp-migration]] - WARP DataStore schema with 19 keys including private key fields
- [[concepts/release-stub-gate]] - Other P2-adjacent issue discovered in the same v0.0.2 code review cycle
- [[concepts/release-process]] - v0.0.2 was released with P2 debts acknowledged and deferred
- [[concepts/byedpi-jni-guard-hardening]] - Related hardening for JNI boundary validation

## Sources

- [[daily/2026-05-04.md]] - Session 12:05: code review by two subagents found P2 debts — plaintext private key in DataStore, missing input validation for engine configs; classified non-blocking for v0.0.2 release; deferred to next cycle
