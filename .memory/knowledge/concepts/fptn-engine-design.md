---
title: "FPTN Engine Design"
aliases: [fptn-engine, fptn-architecture, fptn-jni, fptn-websocket]
tags: [fptn, engine, jni, android, vpn, tun, websocket]
sources:
  - "daily/2026-05-22.md"
created: 2026-05-22
updated: 2026-05-22
---

# FPTN Engine Design

FPTN is a TUN-based VPN engine using WebSocket + TLS (SNI camouflage) + Protobuf messages. Unlike SOCKS-based engines (ByeDPI, MasterDNS), FPTN receives a raw TUN file descriptor and routes all traffic directly — requiring `VpnService.Builder` setup in Kotlin with `protect(socket)` calls similar to URnetwork. The server list is delivered via an encrypted token: `fptn:` + Base64(JSON), decoded to `{servers:[{host, port, vpn_port}]}`.

## Key Points

- Protocol: WebSocket + TLS with configurable SNI, Protobuf frames
- Token format: `fptn:` prefix + Base64-encoded JSON `{servers:[{host,port,vpn_port}]}`
- Auth flow: POST `/api/v1/auth` → response contains encrypted server list
- TUN-based (not SOCKS) — needs `VpnService.Builder` + `protect(socket)`, same pattern as URnetwork
- JNI callbacks into Kotlin: `onOpenImpl()`, `onMessageImpl([B)V`, `onFailureImpl()`
- `fptnb:` token variant (brotli-compressed) not supported without `org.brotli:dec` dep — requires explicit approval

## Details

### Token Decoding

Two token formats exist:
- `fptn:` prefix → Base64 → JSON `{servers:[{host,port,vpn_port}]}`
- `fptnb:` prefix → brotli-compressed Base64 → JSON (not supported without brotli dep)

`android.util.BrotliInputStream` is a hidden/system API — inaccessible from user apps even with `@TargetApi(31)`. Adding `fptnb:` support requires the `org.brotli:dec` Maven dependency.

### JNI Lifecycle

The native layer (`websocket_client.cpp`) receives a `WeakGlobalRef` to the Kotlin wrapper object. Proper lifecycle:

```cpp
// nativeCreate: store weak ref
wrapper_ = env->NewWeakGlobalRef(thiz);

// nativeDestroy: clean up
env->DeleteWeakGlobalRef(wrapper_);
wrapper_ = nullptr;
```

`GetWrapper()` getter dereferences the weak ref and returns null if the Kotlin object was GC'd. Without `DeleteWeakGlobalRef` in `nativeDestroy`, the weak ref leaks per JNI session.

`utils.h`: every `GetStringUTFChars` call must have a matching `ReleaseStringUTFChars` — omitting it causes a heap leak per JNI invocation.

In `https_client.cpp`: local refs (`clazz`, `body_str`, `error_str`) on non-exceptional paths must be explicitly `DeleteLocalRef`'d to avoid local frame exhaustion.

### Stop Sequence

`fis.read()` (reading the TUN fd) is a blocking syscall that does not respect Kotlin coroutine cancellation. Incorrect order causes deadlock:

```
// WRONG: cancel scope → job.join() hangs because read() is blocking
scope.cancel() → job.join()
```

Correct stop sequence:
```
nativeStop() → pfd.close() → scope.cancel() → job.join() → nativeDestroy()
```

`pfd.close()` closes the TUN fd → unblocks `fis.read()` with `IOException` → TUN loop exits → coroutine completes → `join()` returns → `nativeDestroy()` is safe.

### Logging Invariants

Safe to log (user-visible, not sensitive): server hostname + port, auth result code, TUN handle creation, stop sequence checkpoints.

Must NOT log: server IP addresses derived from DNS, auth tokens, connection payloads.

`PersistentLoggers.error` on: native lib load failure, auth 200 with empty token, auth HTTP error, TUN read loop `IOException`, `awaitReady` timeout.

### CI / Test Limitations

JNI C++ code (`utils.h`, `websocket_client.cpp`, `https_client.cpp`) compiles only during `.so` build — not exercised by unit test CI. Memory leak fixes (missing `ReleaseStringUTFChars`, undeleted `WeakGlobalRef`) are not caught by CI; they require Valgrind or ASan on device build.

## Related Concepts

- [[concepts/fptn-engine-protocol]] - Earlier protocol research; this article supersedes with implementation details
- [[concepts/fptn-sni-bypass-method]] - SNI bypass mechanism used by FPTN TLS layer
- [[concepts/engine-masterdns]] - Subprocess-pattern engine; FPTN follows the full EnginePlugin contract instead
- [[concepts/android-parcelfiledescriptor-close-trap]] - `pfd.close()` is the correct fd close pattern on Android
- [[concepts/jni-local-global-ref-lifecycle]] - General JNI ref lifecycle patterns applied here

## Sources

- [[daily/2026-05-22.md]] - Session 22:20+: FPTN CI chain runs 1-3 (ktlint fixes, BrotliInputStream hidden API, JNI memory leaks); stop() order nativeStop→pfd.close→cancel→join→nativeDestroy; JNI leaks: GetStringUTFChars without Release, NewWeakGlobalRef never deleted; fptnb: unsupported without brotli dep; Session 21:27: protocol analysis (WebSocket+TLS+Protobuf, fptn: token Base64 JSON, TUN-based, JNI callbacks onOpenImpl/onMessageImpl/onFailureImpl)
