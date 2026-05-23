---
title: "FPTN SNI Bypass Method: Enum, sniDomain Field, Reality Protocol Requirement"
aliases: [fptn-sni, fptn-bypass-method, fptn-reality-sni, fptn-sni-domain]
tags: [fptn, engine, tls, sni, reality, configuration]
sources:
  - "daily/2026-05-23.md"
created: 2026-05-23
updated: 2026-05-23
---

# FPTN SNI Bypass Method: Enum, sniDomain Field, Reality Protocol Requirement

FPTN supports three bypass modes controlled by `FptnBypassMethod` enum: `SNI` (SNI spoofing), `OBFUSCATION`, and `SNI_AND_REALITY`. Each mode changes how the TLS ClientHello is constructed in `PerformFakeHandshake2()`. The critical correctness requirement: Reality mode requires a **real registered domain name** (not an IP address) in the ClientHello SNI field, because the Reality server validates the domain against its configuration. Sending the server's IP address as SNI causes a 15-second timeout (HTTP 608) with no response.

## Key Points

- Default bypass method: `SNI` (matches original FPTN application UI default)
- Default SNI domain: `ads.x5.ru` (from original FPTN app screenshot; confirmed by user)
- `FptnBypassMethod.SNI_AND_REALITY` requires a real domain in SNI — IP address → server silently times out
- FPTN HTTP 608 is the `ExecuteWithTimeout` outer wrapper timeout (15s), NOT an HTTP status code
- The regression: `sni = host` (server IP used as SNI) instead of `sni = config.sniDomain` — caused every Reality connection to fail
- `buildManualConfig` must pass `sniDomain` through `DataStoreFptnConfigStore → FptnConfig → FptnEngine.start()`

## Details

### Bypass Method Enum

```kotlin
enum class FptnBypassMethod(val key: String, val isReality: Boolean) {
    SNI("sni", false),
    OBFUSCATION("obfuscation", false),
    SNI_AND_REALITY("sni_and_reality", true)
}
```

Helper properties `isReality` and `usesSni` replace `when`-branch chains across the codebase.

The default changed from `OBFUSCATION` (original Ozero assumption) to `SNI` (actual original FPTN app default). Users connecting to Reality servers must explicitly select `SNI_AND_REALITY` and enter the correct domain.

### sniDomain Configuration Flow

The SNI domain travels from DataStore to the native TLS layer through the following chain:

```
DataStoreFptnConfigStore.fptnConfig()
  → FptnConfig(sniDomain = "ads.x5.ru")   ← default from DataStore key "fptn_sni_domain"
    → FptnEngine.start(config)
      → config.sniDomain assigned to _sniDomain
        → setupSsl(sniDomain)             ← SSLContext.setServerNames(sniDomain)
          → PerformFakeHandshake2()       ← TLS ClientHello with correct SNI
```

Fresh install: `fptn_sni_domain` key absent in DataStore → fallback `"ads.x5.ru"`.

The regression introduced `sni = host` (where `host = config.host = server IP`) instead of `sni = config.sniDomain`. The IP address was passed as the SNI server name to `setupSsl()`. For `SNI` and `OBFUSCATION` modes this may or may not matter depending on server config. For `SNI_AND_REALITY` mode the server validates the SNI domain against its configuration — an IP is always invalid, so the server responds with nothing → 15s timeout → HTTP 608.

### FPTN HTTP 608 Semantics

`HTTP 608` in FPTN logs is the exit code from `ExecuteWithTimeout`, an outer retry wrapper. It means the 15-second overall timeout expired before receiving any response — not a real HTTP status. The underlying cause (wrong SNI, wrong server, network unreachable) is hidden by this wrapper. When debugging FPTN connectivity, HTTP 608 = "15s total timeout, no response received."

### UI for Bypass Method

Three radio buttons in `FptnBypassCard` (Compose):
- `SNI` — SNI spoofing, no TLS fingerprinting
- `Обфускация` / `Obfuscation` — traffic obfuscation
- `SNI + Reality` — requires valid SNI domain in field below

SNI domain field is shown only when `SNI` or `SNI_AND_REALITY` is selected. Reset button restores `sniDomain` to `"ads.x5.ru"`. `FptnBypassCard` was decomposed into 3 private functions to satisfy detekt `LongMethod` rule (195 → <120 lines per function).

## Related Concepts

- [[concepts/fptn-engine-protocol]] — Overall FPTN engine architecture; SNI bypass is part of the TLS auth layer
- [[concepts/kotlin-suspendcancellablecoroutine-type-inference]] — Kotlin type inference trap when handling multiple bypass mode branches
- [[concepts/android-vpn-self-traffic-bypass]] — IP probe architecture; FPTN uses `ipProbeRoute()` like other engines

## Sources

- [[daily/2026-05-23.md]] — Session 13:43: sniDomain flow trace verified through full code read; `sni = host` regression identified as root cause of HTTP 608; default `ads.x5.ru` confirmed from original FPTN app; `DEFAULT_BYPASS_METHOD = SNI` (not Reality) to match original. Session (13:31+): FptnBypassMethod enum with `SNI` added, `isReality`/`usesSni` helpers, FptnBypassCard decomposed for detekt LongMethod, `sni = config.sniDomain` fix committed
