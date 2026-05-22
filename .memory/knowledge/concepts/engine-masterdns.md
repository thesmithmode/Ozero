---
title: "engine-masterdns — Subprocess EnginePlugin (libmdnsvpn.so)"
aliases: [masterdns, engine-masterdns, masterdns-subprocess]
tags: [masterdns, subprocess, engine, dns, architecture]
sources:
  - "daily/2026-05-21.md"
created: 2026-05-21
updated: 2026-05-21
---

# engine-masterdns — Subprocess EnginePlugin (libmdnsvpn.so)

`engine-masterdns` is a full `EnginePlugin` (`@IntoSet` via `MasterDnsModule`) that runs `libmdnsvpn.so` as a subprocess via `ProcessBuilder` — the same subprocess-pattern as `engine-telegram`, but unlike `engine-telegram`, MasterDNS is registered as a VPN engine. The native binary is launched as a child process; it is never `System.loadLibrary`-d into the main process.

## Key Points

- Subprocess pattern: `ProcessBuilder` executes `libmdnsvpn.so` binary from native library directory; no JNI
- `supportsUpstreamSocks = false` — `upstream` param in `start()` is intentionally ignored (`@Suppress("UNUSED_PARAMETER")` on override)
- `MasterDnsEngine.start`: `withTimeoutOrNull(startTimeoutMs = 10s)` + `service.stop()` on timeout to prevent zombie subprocess
- `MasterDnsResolversCache`: `stateIn(SharingStarted.Eagerly)` snapshot replaces `runBlocking + withTimeoutOrNull` in Hilt provider (anti-pattern in DI graph)
- `MasterDnsConfigWriter`: TOML filter is case-insensitive (`startsWith(key, ignoreCase = true)`) — LISTEN_IP / LISTEN_PORT / LOCAL_DNS_ENABLED keys matched regardless of case from user TOML

## Details

### Architecture

MasterDNS follows the subprocess-pattern documented in `engine-telegram` but is registered as a full VPN engine (`EnginePlugin`, `@IntoSet`). The binary `libmdnsvpn.so` is stored in the native library directory and executed via `ProcessBuilder`. This contrasts with WARP (`libam-go.so` loaded via ReLinker AAR ctor) and URnetwork (Go SDK AAR via JNI).

The engine is deliberately simple: DNS-only, no SOCKS upstream. The `upstream` parameter from `Engine.start(config, upstream)` has no effect and is suppressed. This matches `MasterDnsCapabilities.supportsUpstreamSocks = false`.

### MasterDnsConfigWriter

Generates a TOML config file for the subprocess. Three keys are reserved and must not be forwarded from user TOML: `LISTEN_IP`, `LISTEN_PORT`, `LOCAL_DNS_ENABLED`. Filtering is case-insensitive to handle user configs with mixed-case key names. Port parsing supports both `host:port` and `[ipv6]:port` formats — previously port was hardcoded to 53.

### MasterDnsResolversCache

A `stateIn(SharingStarted.Eagerly)` snapshot of available DNS resolvers injected into the Hilt graph. The Eagerly-sharing strategy guarantees the first value is available synchronously for `.value` reads, eliminating the need for `runBlocking + withTimeoutOrNull` in the DI provider (which is an anti-pattern: blocking thread inside Hilt init). See [[concepts/stateIn-eagerly-test-trap]] for the test implications.

### Stdout logging limit

`MasterDnsClientService` limits subprocess stdout reader to `MAX_LOG_LINES = 200`, after which it writes a `PersistentLoggers.warn` instead of `Log.w` — required because `LoggingContractTest` enforces that persistent log calls come from a whitelist.

### Error handling

`MasterDnsSettingsViewModel` wraps each DataStore write (`setX`) in `runCatching + PersistentLoggers.error` so DataStore IO errors surface in the persistent log rather than being silently swallowed.

### Auto-mode enrollment

After squash merge, `engine-masterdns` was added to `engineAutoPriority` in auto-mode engine list. Any new VPN engine that is a full `EnginePlugin` must be added to `engineAutoPriority` — absence means the engine never appears in auto-mode rotation. See [[concepts/vpn-engine-pipeline]].

## Related Concepts

- [[concepts/engine-telegram-mtproxy]] — subprocess side-car pattern (non-VPN); MasterDNS shares process launch but is a full EnginePlugin
- [[concepts/vpn-engine-pipeline]] — auto-mode engine priority list; new engines must enroll
- [[concepts/stateIn-eagerly-test-trap]] — Eagerly vs WhileSubscribed: Eagerly guarantees .value; WhileSubscribed returns initialValue when no subscriber
- [[concepts/persistent-logger-accumulation-trap]] — stdout log spam → PersistentLoggers.warn only at limit, not on each line
- [[concepts/extract-native-libs-legacy-packaging]] — useLegacyPackaging=true required for ProcessBuilder subprocess launch

## Sources

- [[daily/2026-05-21.md]] — squash merge dns-tunnel-module + 7 review fixes: timeout, upstream @Suppress, case-insensitive TOML, MasterDnsResolversCache Eagerly, host:port parsing, log spam limit, silent DataStore errors
