---
title: "Sing-box: splithttp Transport Not Supported in Current libbox.so"
aliases: [singbox-splithttp, splithttp-unsupported, unknown-transport-splithttp]
tags: [singbox, android, configuration, transport, aar]
sources:
  - "daily/2026-05-26.md"
created: 2026-05-26
updated: 2026-05-26
---

# Sing-box: splithttp Transport Not Supported in Current libbox.so

The `splithttp` transport type is not supported in the version of `libbox.so` bundled with Ozero's sing-box AAR. Profiles containing `"type": "splithttp"` in their transport configuration produce `unknown transport type: splithttp` error at config load time, causing the engine to fail or skip the affected server. These profiles must be filtered or marked unsupported at parse time rather than passed through to the engine.

## Key Points

- `"transport": {"type": "splithttp"}` → `unknown transport type: splithttp` at sing-box config load
- The error is a runtime config parse failure — the server is silently skipped or causes a crash depending on sing-box's error handling mode
- Fix: in `SingboxSubscriptionParser`, filter out any server with `transport.type == "splithttp"` (and other unsupported transports)
- The set of supported transports depends on the AAR/libbox version — document supported types in a constant or comment
- Common in modern VLESS/VMess profiles that use splithttp for CDN compatibility

## Details

### Why splithttp Appears in Profiles

The `splithttp` transport (also called "XHTTP" in some implementations) is a relatively recent addition to the sing-box/xray ecosystem. It was designed for CDN compatibility by splitting the HTTP request/response into separate upload and download streams. Many public subscription providers have added `splithttp`-based servers to their lists.

When these profiles are imported into Ozero's sing-box engine, the current `libbox.so` version does not recognize the transport type. The engine logs `unknown transport type: splithttp` and either skips the server or fails to start entirely if it's the only server.

### Parser-Level Filtering

The `SingboxSubscriptionParser` should reject unsupported transport types during parse, not rely on the engine to handle them gracefully:

```kotlin
private val UNSUPPORTED_TRANSPORTS = setOf("splithttp", "xhttp")

fun parseServer(json: JsonObject): SingboxServer? {
    val transport = json.getAsJsonObject("transport")
    val transportType = transport?.get("type")?.asString
    if (transportType in UNSUPPORTED_TRANSPORTS) {
        return null  // skip, log at debug level
    }
    // ... continue parsing
}
```

### Relationship to SIGABRT

In Ozero v0.2.8/v0.2.9, 3 SIGABRTs were observed in `:engine_singbox`. The `unknown transport type: splithttp` error was one of the contributing factors found during log analysis. Whether this specific error caused a crash (vs. silent skip) depends on the libbox version's error handling — but it is categorized as a T2 fix (after the `dns outbound` T1 fix).

## Related Concepts

- [[concepts/singbox-dns-outbound-deprecated]] - Companion deprecation issue from the same log analysis session
- [[concepts/singbox-subscription-architecture]] - Parser component that needs the transport filter
- [[concepts/singbox-engine-design]] - libbox.so version and AAR dependency management
- [[concepts/cascade-unresolved-import-masking]] - How one parse error can mask others

## Sources

- [[daily/2026-05-26.md]] - Session 19:44: log analysis found `unknown transport type: splithttp` in v0.2.8/v0.2.9 `:engine_singbox` logs; categorized as T2 in fix priority; fix = filter `transport.type=splithttp` in parser before passing to engine; current libbox.so does not support this transport
