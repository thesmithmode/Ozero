---
title: "Singbox Ping: AbstractBean vs VLESSBean Deserialization"
aliases: [singbox-ping-latency, probelatencyms-deserialization]
tags: [singbox, ping, deserialization, kotlin, android]
sources:
  - "daily/2026-05-26.md"
created: 2026-05-26
updated: 2026-05-26
---

# Singbox Ping: AbstractBean vs VLESSBean Deserialization

When deserializing `probeLatencyMs` for server ping results in the Singbox engine, the target type must be `AbstractBean`, not `VLESSBean`. Deserializing into `VLESSBean` returns -1 for all non-VLESS protocol servers (VMess, Trojan, Shadowsocks, WireGuard).

## Key Points

- `probeLatencyMs` is a field on `AbstractBean` — the common base class for all protocol server types
- Deserializing into `VLESSBean` directly causes all non-VLESS servers to silently return `-1` latency
- The fix is to deserialize into `AbstractBean` and access `serverAddress`/`serverPort` from the base class
- Ping results appear to succeed (no exception) but contain wrong data — makes this a silent correctness bug
- Always deserialize to the most general type when handling heterogeneous server lists

## Details

### Root Cause

The Singbox server list ViewModel iterates over profiles of mixed protocol types. When the ping callback returns a result object, naive deserialization targets `VLESSBean` — the first concrete type encountered during development. This works for VLESS servers but silently produces `-1` for VMess, Trojan, Shadowsocks, and WireGuard servers because those fields are absent in the VLESS subtype's JSON schema.

The correct approach deserializes the result into `AbstractBean`, which is the sealed class parent containing `probeLatencyMs`, `serverAddress`, and `serverPort`. All protocol-specific subclasses inherit these fields. The `AbstractBean` deserialization accepts any protocol variant and correctly hydrates the latency value.

### Tombstone Diagnostics

Unrelated but discovered in the same session: singbox SIGSEGV/SIGABRT crashes require pulling tombstone files from the device:
```
adb pull /data/user/0/ru.ozero.app/files/debug/
```
The crash directory path is specific to the Ozero package name and the singbox debug output location.

## Related Concepts

- [[concepts/singbox-engine-design]] - Engine architecture including server types (VLESSBean, SingboxServer sealed class)
- [[concepts/singbox-subscription-architecture]] - SingboxServer sealed class with Vless/Shadowsocks/Vmess/Trojan/WireGuard subclasses
- [[concepts/cascade-unresolved-import-masking]] - Silent wrong-type deserialization is a similar class of masked error
- [[concepts/pingJob-viewmodel-cancellation]] - Companion pattern: storing pingJob for cancellation alongside ping latency display

## Sources

- [[daily/2026-05-26.md]] — Session 13:59: ping deserialization via AbstractBean (not VLESSBean) fixes -1 for non-VLESS servers; tombstone pull path for singbox crash diagnostics
