---
title: "FPTN Engine Protocol Architecture"
aliases: [fptn-engine, fptn-protocol, fptn-tun-engine]
tags: [fptn, engine, tun, protocol, architecture, planning, jni]
sources:
  - "daily/2026-05-22.md"
  - "daily/2026-05-23.md"
created: 2026-05-22
updated: 2026-05-23
---

# FPTN Engine Protocol Architecture

FPTN (Free Private Tunnel Network) is a VPN protocol that uses WebSocket+TLS+Protobuf for transport and operates at the TUN layer (not SOCKS). In Ozero it is planned as a full `EnginePlugin` (`@IntoSet`) following the URnetwork/WARP pattern, with a native `libfptn_native_lib.so` built from C++ sources via CMakeLists inside `engine-fptn`. Unlike `engine-telegram` (subprocess side-car) or `engine-masterdns` (subprocess-pattern EnginePlugin), FPTN requires full fd-based TUN integration with `VpnService.Builder` and `protect(socket)` calls.

## Key Points

- Protocol transport: WebSocket+TLS+Protobuf (not raw WireGuard, not SOCKS5)
- TUN-based: reads/writes to VPN fd directly, same as URnetwork Go SDK
- Auth token format: `fptn:` + Base64-encoded JSON → `{"servers":[{"host":"...","port":N,"vpn_port":N}]}`
- Token acquisition: POST `/api/v1/auth` → server returns encrypted server list inside the token
- Native JNI callbacks required: `onOpenImpl()`, `onMessageImpl([B)V`, `onFailureImpl()`
- Build: `libfptn_native_lib.so` from C++ sources via CMakeLists in `engine-fptn` (not prebuilt `.so`)
- User: @fptn_bot provides access tokens (link needed in settings screen)

## Details

### Protocol and Token Decoding

FPTN tokens use a custom `fptn:` URI scheme. Stripping the prefix and Base64-decoding the remainder yields a JSON object containing a list of servers with `host`, `port`, and `vpn_port` fields. The server list is the only configuration needed — the client connects to any server via WebSocket over TLS and negotiates a TUN session using Protobuf framing.

The auth endpoint (`POST /api/v1/auth`) expects user credentials and returns a token that already includes the encrypted server list. This is different from the WireGuard-based engines (WARP, AWG) where the config is negotiated via a separate config API and stored locally.

### Engine Architecture Decision

Two candidate patterns were considered:

1. **Subprocess pattern** (like `engine-telegram` or `engine-masterdns`): run `libfptn_native_lib.so` as a `ProcessBuilder` child process, pass the TUN fd via `ParcelFileDescriptor`
2. **Full EnginePlugin** (like URnetwork/WARP): native library loaded in-process via JNI, reads/writes TUN fd directly

Decision: **Full EnginePlugin**. FPTN is TUN-based and requires tight VPN service lifecycle integration (`protect(socket)`, `Builder.establish()` fd ownership). The subprocess pattern requires `extractNativeLibs=true` (legacy packaging), adds IPC complexity, and makes fd passing harder. Building from C++ sources (not prebuilt) means the build is reproducible and the binary can be customized.

Contrast: `engine-telegram` is a side-car subprocess that does NOT implement `Engine` at all and does NOT register `@IntoSet`. FPTN is a first-class engine.

### JNI Surface

The native library exposes:
- `onOpenImpl()` — called when the WebSocket connection is established and ready to receive VPN fd
- `onMessageImpl([B)V` — called by the native layer when a Protobuf message arrives (typically a control message or keepalive)
- `onFailureImpl()` — called on connection failure; Kotlin side should trigger `handleEngineFailure()`

These are the same callback pattern as the URnetwork AAR bridge, but implemented in C++ rather than Go.

### Module Structure

```
engine-fptn/
├── src/main/
│   ├── cpp/
│   │   ├── CMakeLists.txt         # builds libfptn_native_lib.so
│   │   └── fptn_native_lib.cpp    # JNI exports + WebSocket+TLS+Protobuf impl
│   ├── java/ru/ozero/enginefptn/
│   │   ├── FptnEngine.kt          # EnginePlugin implementation
│   │   ├── FptnNative.kt          # JNI wrapper + callbacks
│   │   └── FptnModule.kt          # @IntoSet Hilt binding
│   └── res/
└── build.gradle.kts               # cmake block, arm64-v8a only
```

### UI Requirements

- Server list picker with RTT ping display (auto-select by lowest latency)
- Token input field (paste or share from @fptn_bot)
- Link to @fptn_bot for token acquisition
- Experimental reconnect settings (retry interval, max retries) — low priority

### Session Status (2026-05-22)

Implementation was started mid-session (21:27) and stopped by user request — a plan must be written first and approved before code is written. Three uncommitted changes also exist from AMNEZIA removal (EngineId.kt, EngineConfig.kt, MainScreen.kt) that must be committed before FPTN work begins.

Pending plan topics:
- CMakeLists.txt structure for C++ FPTN native library
- JNI binding strategy for `onOpenImpl`/`onMessageImpl`/`onFailureImpl`
- TUN fd lifecycle: when to call `Builder.establish()`, when to call `protect(socket)`
- Token parsing and server selection algorithm
- CI integration: native build in ci.yml arm64-v8a step

### Implementation Details and JNI Bug Fixes (2026-05-22, session 22:20+)

A partial implementation ran through CI and uncovered several critical issues:

**`android.util.BrotliInputStream` is not a public SDK API.** Despite being present on some Android versions, it is inaccessible from user apps even with `@TargetApi(31)`. FPTN `fptnb:` tokens (Brotli-compressed) require `org.brotli:dec` dependency for decoding. Until this dep is explicitly approved, `fptnb:` tokens return `null` and are unsupported.

**JNI memory leaks in the C++ layer** (all fixed in commit `eb237773`):
- `utils.h`: `GetStringUTFChars` called without paired `ReleaseStringUTFChars` → memory leak per JNI call
- `websocket_client.cpp`: `NewWeakGlobalRef(thiz)` in `nativeCreate`, `DeleteWeakGlobalRef` never called → leak; fixed by `GetWrapper()` getter + `nativeDestroy` cleanup
- `https_client.cpp`: `NewWeakGlobalRef(thiz)` created but `wrapper_` never used → removed; `NewGlobalRef(response_obj)` in `create_response` → leak, removed; local refs `clazz/body_str/error_str` not deleted on success path → `DeleteLocalRef` added

**`FptnEngine.stop()` race condition**: `tunScope.cancel()` without join before `nativeDestroy` left `fis.read()` (blocking native call) still running inside a cancelled coroutine. Correct teardown order: `nativeStop → pfd.close → scope.cancel → join → nativeDestroy`. Key principle: `fis.read()` does not respect coroutine cancellation — the fd must be closed first to unblock it, then scope cancelled, then joined, then native resources freed.

**Rule**: `NewGlobalRef` in JNI is NOT needed for return values from JNI functions — local refs work correctly as return values. Creating a `GlobalRef` for a return value creates an untracked leak.

**Diagnostic logging added:**
- `Log.i` on: start (server:port, bypass, peer count), authenticate (request/success/HTTP code), attachTun (handle creation, WS run loop launch), awaitReady (poll/timeout), stop (begin/nativeStop/join/destroy/done)
- `PersistentLoggers.error` on: native lib load fail, auth 200 without token, auth HTTP error, authenticate exception, TUN read loop IOException, awaitReady timeout

JNI fixes are applied in C++ source but not verifiable in unit-test CI (the `.so` is only built during release/assembly steps, not during unit test runs).

### brotli Token (fptnb:) — Implemented (2026-05-23, commit cbeaeaee)

`fptnb:` prefix = `base64(brotli(JSON))`. Implemented via `org.brotli:dec:0.1.2` (pure-Java decoder, Apache 2.0 license). Only decoding is needed (tokens come from server, not generated by client). No positive-test for brotli encoding (encoder absent in decoder-only lib) — only negative (non-brotli rejection) + source-sentinel confirming the dep is used.

### SNI Bypass Fix (2026-05-23)

Critical regression: `FptnEngine.kt` line 272 had `sni = host` (server IP). Reality mode requires a real domain in TLS ClientHello (`PerformFakeHandshake2`). IP as SNI → server silently times out → HTTP 608 (15s `ExecuteWithTimeout`) every attempt.

Fix: `sni = config.sniDomain`. New `FptnEngineConfig.sniDomain` field (default `"ads.x5.ru"`) flows from DataStore through `buildManualConfig` to `start()` to `setupSsl()`.

New `FptnBypassMethod` enum: `SNI` (default, matches original FPTN app), `OBFUSCATION`, `SNI_AND_REALITY`. See [[concepts/fptn-sni-bypass-method]] for full details.

### c++_static Link Requirement (2026-05-23, Conan profile fix)

`libfptn_native_lib.so` was linked with `compiler.libcxx=c++_shared` in the Conan android-arm64 host profile. On Android, `libc++_shared.so` is NOT available in the app's native library namespace (`clns-7`) — only in the system namespace. Result: runtime crash `dlopen failed: library "libc++_shared.so" not found` when loading the engine.

Fix: `compiler.libcxx=c++_static` — statically links the C++ runtime into the `.so`. No external dependency at runtime. This is the correct choice for ALL Android `.so` files that use C++ runtime features.

Additional Conan fix: `compiler.cppstd=17` must be explicitly set in the android-arm64 host profile. Without it, Conan's compiler check fails with a cppstd validation error before the build starts.

### Kotlin Fd-Leak Fixes (2026-05-22, session 23:11)

Code review found three Kotlin-level resource leaks in `FptnEngine.kt`:

1. **`FileOutputStream` not closed on error path** — `attachTun()` opened an output stream to write to the TUN fd but the `catch` block returned without closing it. Fixed via `use{}` wrapping.
2. **`FileInputStream` without `.use{}`** — `fis` (TUN read loop) was opened without RAII; if an exception escaped the read loop before `pfd.close()`, the stream leaked. Fixed with `.use{}`.
3. **`SupervisorJob` accumulation** — `attachTun()` created a new `tunScope` with `SupervisorJob()` on each call. Without a paired `stop()` between calls, repeated `attachTun()` invocations accumulated orphaned `SupervisorJob` instances. Fixed by ensuring `stop()` always cancels the previous scope before `attachTun()` creates a new one.

Rule: every `FileInputStream`/`FileOutputStream` wrapping a VPN fd must use `.use{}` or equivalent RAII to guarantee closure even on exception paths.

**CI failure from engine-telegram removal (session 23:11):** `ci.yml` contained job references to `engine-telegram` that were not removed when the module was deleted. Pattern: when deleting a Gradle module, audit `ci.yml`, `release.yml`, and `settings.gradle.kts` for all references. Similarly, adding FPTN as a non-stub `EnginePlugin` broke `SettingsRepositoryTest` because the auto-priority reconcile test enumerated expected engines and did not include FPTN. Lesson: **when adding a new engine, grep for reconcile/enumeration tests** that list all engines by name and update them before push.

## Related Concepts

- [[concepts/vpn-engine-pipeline]] — EnginePlugin contract this must implement
- [[concepts/engine-telegram-mtproxy]] — subprocess side-car pattern (NOT what FPTN uses)
- [[concepts/engine-masterdns]] — subprocess-pattern EnginePlugin (closer, but FPTN is in-process)
- [[concepts/go-runtime-process-isolation]] — WARP's process isolation; FPTN avoids this by being C++ not Go
- [[concepts/extract-native-libs-legacy-packaging]] — required for subprocess engines; FPTN avoids this
- [[concepts/gomobile-bind-gotchas]] — AAR build traps for Go engines; FPTN is C++ so different toolchain
- [[concepts/fptn-sni-bypass-method]] — FptnBypassMethod enum, sniDomain field, Reality SNI requirement details

## Sources

- [[daily/2026-05-23.md]] — Session 02:30: fptnb: brotli token implemented (org.brotli:dec:0.1.2); commit cbeaeaee. Session 12:23: c++_shared → c++_static Conan fix (libc++_shared.so not in clns-7); FptnLink composable cleanup (bot hint only). Session 13:31+: FptnBypassMethod.SNI added, sniDomain field, sni=host→sni=config.sniDomain fix, FptnBypassCard decomposed, ktlint wildcard import fixes. Session 03:04+: Conan android-arm64 requires explicit compiler.cppstd=17; libfptn.so assert added to release.yml
- [[daily/2026-05-22.md]] — Session 21:27: FPTN protocol analyzed (WebSocket+TLS+Protobuf, fptn: token format, auth endpoint); architecture decision: full EnginePlugin not subprocess; C++ native lib; JNI callbacks onOpenImpl/onMessageImpl/onFailureImpl; TUN-based fd integration; @fptn_bot UI; user stopped implementation to demand written plan first; MTG dropped; Clash deferred to SS/VMess future. Session 22:20+: CI implementation run — android.util.BrotliInputStream not public API (fptnb: unsupported); JNI memory leaks found and fixed (GetStringUTFChars without Release, NewWeakGlobalRef without Delete, NewGlobalRef return value leak); stop() race condition fixed (correct teardown order: nativeStop→pfd.close→scope.cancel→join→nativeDestroy); diagnostic logging added. Session 23:11: Kotlin fd-leak fixes (FileOutputStream error path, FileInputStream .use{}, SupervisorJob accumulation); CI failures from engine-telegram ci.yml references left behind; SettingsRepositoryTest reconcile broken by FPTN addition (must update engine-enumeration tests when adding new engine)
