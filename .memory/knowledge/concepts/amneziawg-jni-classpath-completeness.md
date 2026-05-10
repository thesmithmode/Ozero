---
title: "AmneziaWG JNI Classpath Completeness Requirement"
aliases: [amneziawg-registernatives, libam-go-classpath, awg-jni-classes]
tags: [warp, amneziawg, native, jni, gotcha]
sources:
  - "daily/2026-05-08.md"
created: 2026-05-08
updated: 2026-05-08
---

# AmneziaWG JNI Classpath Completeness Requirement

`libam-go.so` registers JNI methods via `RegisterNatives` in `JNI_OnLoad` for multiple Java classes. Every class the SO references must exist in the app's classpath at load time — missing any causes `ClassNotFoundException` during `System.loadLibrary("am-go")`, preventing the library from loading. When replacing the Maven AAR with checked-in SO files, these Java classes must be added manually.

## Key Points

- `libam-go.so` `JNI_OnLoad` calls `RegisterNatives` for: `GoBackend` (7 methods), `ProxyGoBackend` (6 methods), `SocketProtector` (interface, parameter type)
- Missing any class → `ClassNotFoundException` at SO load time → `UnsatisfiedLinkError` propagates up
- Classes can be discovered by: `grep -ao "amnezia[/_a-zA-Z]*" libam-go.so | sort -u` — outputs JNI symbol class names
- When using Maven AAR these classes come automatically; with checked-in SO they must be copied manually from AAR sources
- `SocketProtector` is an interface used as parameter type in `awgSetSocketProtector(...)` JNI method — must be present even if unused by the app

## Details

### Why RegisterNatives Requires All Classes

The `JNI_OnLoad` function runs synchronously during `System.loadLibrary`. At this point, the Go runtime in `libam-go.so` attempts to resolve all Java classes it will interact with via `FindClass` and then register native methods via `RegisterNatives`. If any `FindClass` call returns NULL (class not found), `RegisterNatives` fails and `JNI_OnLoad` returns the error code, causing the entire library load to fail.

This is in contrast to lazy JNI method lookup (where the runtime looks up methods only when called) — Go's JNI binding via gomobile uses eager registration at startup for performance and early error detection.

### Classes Required for libam-go.so

Discovered by inspecting JNI symbol names from the binary:

| Class | Package | Methods |
|-------|---------|---------|
| `GoBackend` | `org.amnezia.awg.backend` | 7 native methods (awgTurnOn, awgTurnOff, awgGetConfig, etc.) |
| `ProxyGoBackend` | `org.amnezia.awg.backend` | 6 native methods |
| `SocketProtector` | `org.amnezia.awg.backend` | interface, no native methods |

The Maven `com.zaneschepke:amneziawg-android` AAR bundles these as compiled Java/Kotlin classes. When using checked-in SO without the AAR, all three must be present as source files in the module.

### Discovery Method

To find all classes a native library requires at load time:

```bash
grep -ao "amnezia[/_a-zA-Z]*" libam-go.so | sort -u
```

This extracts JNI class descriptors (using `/` as package separator) from the binary's string table. Each unique result that matches a Java class path is a required class. Convert `/` → `.` for the fully qualified class name.

### Relationship to AAR Replacement

When migrating from Maven AAR to checked-in SO (as done in Ozero's WARP engine migration), the Java glue layer becomes the developer's responsibility. The minimum viable set is exactly the three classes listed above — no other classes from the original AAR are required for core tunnel operations (`awgTurnOn`/`awgTurnOff`/`awgGetConfig`).

## Related Concepts

- [[concepts/amneziawg-so-binary-integrity]] - The SO migration that made this requirement explicit
- [[concepts/amneziawg-relinker-loading-trap]] - Earlier loading trap from the same SO; complementary failure modes
- [[concepts/hilt-di-native-library-failure]] - Related pattern: missing JNI class causes DI graph failure

## Sources

- [[daily/2026-05-08.md]] - Session 12:18: GoBackend (7) + ProxyGoBackend (6) + SocketProtector discovered as required classpath entries when removing zaneschepke AAR; discovery method via grep on SO binary
