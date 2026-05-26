---
title: "Singbox Ping: AbstractBean Deserialization for probeLatencyMs"
aliases: [singbox-probe-latency, singbox-abstractbean-ping, singbox-vlessbean-trap]
tags: [singbox, android, kotlin, deserialization, ping]
sources:
  - "daily/2026-05-26.md"
created: 2026-05-26
updated: 2026-05-26
---

# Singbox Ping: AbstractBean Deserialization for probeLatencyMs

When probing server latency in the sing-box engine, `probeLatencyMs` must be deserialized through `AbstractBean` rather than a protocol-specific subclass such as `VLESSBean`. Deserializing through `VLESSBean` only populates fields for VLESS servers; all other protocol types (VMess, Trojan, Shadowsocks, WireGuard) return `-1` because their fields are absent from the VLESS schema.

## Key Points

- `AbstractBean` is the common base for all sing-box server types; it carries `serverAddress` and `serverPort` which are sufficient for latency probing
- Deserializing via `VLESSBean` silently returns `-1` for non-VLESS servers — no exception, just wrong data
- The fix: deserialize the JSON blob with `AbstractBean` (or the correct specific subclass), not always `VLESSBean`
- `probeLatencyMs` is a field on `AbstractBean` itself — no subclass needed for the latency value
- The bug is easy to introduce when copy-pasting probe logic from a VLESS-only context

## Details

### Root Cause

The sing-box configuration model uses a sealed hierarchy: `AbstractBean` at the top, with protocol-specific subclasses (`VlessBean`, `VmessBean`, `TrojanBean`, etc.). During latency probing, the code needs `serverAddress` and `serverPort` to establish a test connection, and `probeLatencyMs` to record the result. These three fields all live on `AbstractBean`.

If the deserializer is hardcoded to `VlessBean::class`, the JSON fields for other protocols don't map to any property on `VlessBean`. Gson/Moshi silently ignores unknown fields and returns a partially-filled object. For VLESS servers, the result is correct. For everything else, `probeLatencyMs` ends up at its default value of `-1`, which renders latency sorting and display useless for most of the server list.

### Correct Pattern

```kotlin
// Wrong: only works for VLESS
val bean = gson.fromJson(serverJson, VlessBean::class.java)
val latency = bean.probeLatencyMs  // -1 for non-VLESS

// Correct: works for all protocol types
val bean = gson.fromJson(serverJson, AbstractBean::class.java)
val latency = bean.probeLatencyMs  // actual measured latency
val address = bean.serverAddress
val port = bean.serverPort
```

### Impact on UI

This bug caused the server list ping UI to display `-1` ms for all non-VLESS servers, making the "sort by latency" feature useless for mixed-protocol subscription groups (which is the common case with preset groups from КИБЕРЩИТ-X).

## Related Concepts

- [[concepts/singbox-engine-design]] - Sealed server hierarchy and preset groups context
- [[concepts/pingJob-viewmodel-cancellation]] - Companion concept: once deserialization is fixed, correct ViewModel cancellation is also needed
- [[concepts/singbox-subscription-architecture]] - Subscription fetch populates the server list that ping operates on

## Sources

- [[daily/2026-05-26.md]] - Session 13:59: decided to deserialize `probeLatencyMs` via `AbstractBean` not `VLESSBean`; all non-VLESS servers returned -1 before fix; `bean.serverAddress/serverPort` used for probe connection
