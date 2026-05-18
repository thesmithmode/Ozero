---
title: "extractNativeLibs=false Trap for Prebuilt Go Binaries"
aliases: [extract-native-libs, useLegacyPackaging, agp-extract-libs-trap]
tags: [android, native, agp, gotcha, subprocess]
sources:
  - "daily/2026-05-15.md"
created: 2026-05-15
updated: 2026-05-15
---

# extractNativeLibs=false Trap for Prebuilt Go Binaries

Since AGP 3.6, `android:extractNativeLibs` defaults to `false` in the merged manifest. This means `.so` files in `jniLibs/` are stored compressed in the APK and loaded directly from APK memory at runtime via `dlopen` — they are NOT extracted to `nativeLibraryDir` on disk. For libraries loaded via `System.loadLibrary`, this is transparent. For prebuilt Go binaries launched as subprocesses via `ProcessBuilder` (e.g., `libmtg.so` in `engine-telegram`), the binary physically does not exist on disk — `binary.exists()` returns `false` and process launch fails silently.

## Key Points

- AGP 3.6+ default: `extractNativeLibs=false` → `.so` files stay compressed inside APK, not extracted to `nativeLibraryDir`
- `System.loadLibrary("name")` works (Android linker reads from APK directly via `dlopen`)
- `ProcessBuilder(listOf(nativeLibraryDir + "/libmtg.so"))` fails — file does not exist on disk
- Fix: `jniLibs.useLegacyPackaging = true` in `android { packaging { } }` — forces extraction to disk at install time
- Applied in `ozero.android.application.gradle.kts` (convention plugin) — one location for all modules
- Sentinel: `MtgExtractNativeLibsSentinelTest` reads the real buildscript and asserts `useLegacyPackaging = true` is present

## Details

### The Mechanism

Android Package Manager handles `.so` files differently based on `extractNativeLibs`:

| Setting | Disk extraction | `System.loadLibrary` | `ProcessBuilder` (file access) |
|---------|----------------|---------------------|-------------------------------|
| `true` (legacy) | Extracted to `nativeLibraryDir` at install | Works | Works — file exists on disk |
| `false` (AGP 3.6+ default) | NOT extracted — stays in APK | Works (dlopen from APK) | FAILS — no file on disk |

The APK-direct loading path (`false`) is an optimization: reduces install size and install time by avoiding decompression. For standard JNI libraries this is transparent — `System.loadLibrary` delegates to the platform linker which knows how to read from the APK. But for binaries executed as separate processes (Go binaries, shell scripts), the binary must exist as a regular file on the filesystem.

### The Ozero Discovery (2026-05-15)

`engine-telegram` uses `libmtg.so` (a prebuilt Go MTProxy binary) launched via `ProcessBuilder`. After integration, the proxy silently failed to start — `MtgWrapper` logged `binary.exists()=false` for `context.applicationInfo.nativeLibraryDir + "/libmtg.so"`. The initial debugging session added only logging (no diagnosis), prompting the user to invoke `/gsd:debug` which identified the real root cause: the file was never extracted to disk because of `extractNativeLibs=false`.

The fix was adding `jniLibs.useLegacyPackaging = true` to the convention plugin `ozero.android.application.gradle.kts`, ensuring all modules that package `.so` files get them extracted to disk. This is the correct owner-layer fix — modifying it in the convention plugin applies to all build variants without per-module configuration.

### Applicability Beyond engine-telegram

Any future subprocess-based engine or tool that:
1. Ships a prebuilt binary as `.so` in `jniLibs/`
2. Launches it via `ProcessBuilder` or `Runtime.exec()`

Will require `useLegacyPackaging = true`. This pattern applies to: MTProxy (`libmtg.so`), potential future standalone Go/Rust binaries for protocol handlers, and any tool that cannot be loaded via `System.loadLibrary`.

Libraries loaded via standard JNI (`libhev-socks5-tunnel.so`, `libam-go.so`, `libbyedpi.so`) are NOT affected — they work correctly with `extractNativeLibs=false`.

### Shell Script Tests on Windows

The sentinel test `MtgExtractNativeLibsSentinelTest` reads the actual buildscript from disk. Shell-based mock scripts (`#!/bin/sh`) used in `MtgWrapperArgsTest` do not run on Windows — this is acceptable because Kotlin/Android tests only run in CI (Linux runners), never locally on the developer's Windows machine.

### locateRepoRoot() Fragility

The sentinel test traverses up to 5 directory levels to find the repository root (looking for `settings.gradle.kts`). This is somewhat fragile — if the test module's depth changes relative to the repo root, the traversal may fail. An alternative would be using a build-time generated resource with the buildscript content, but the current approach was deemed acceptable for a sentinel test.

## Related Concepts

- [[concepts/engine-telegram-mtproxy]] - The subprocess engine that triggered this discovery; `libmtg.so` is launched via ProcessBuilder, not System.loadLibrary
- [[concepts/gitignore-jnilibs-conflict]] - Related `.so` file handling trap: gitignore blocks tracking; this article covers a different `.so` trap at the packaging level
- [[concepts/amneziawg-relinker-loading-trap]] - Related native library loading trap; different mechanism (ReLinker vs extractNativeLibs) but same symptom class (library not available when expected)

## Sources

- [[daily/2026-05-15.md]] - Session 11:19: `extractNativeLibs=false` root cause found via /gsd:debug; fix = `jniLibs.useLegacyPackaging=true` in convention plugin; commit 2b99f40b; sentinel test MtgExtractNativeLibsSentinelTest
