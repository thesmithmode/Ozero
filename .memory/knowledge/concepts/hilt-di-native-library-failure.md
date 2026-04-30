---
title: "Hilt DI Graph Failure on Native Library Load"
aliases: [hilt-loadlibrary-crash, hilt-native-crash]
tags: [android, hilt, native, jni, crash-diagnosis]
sources:
  - "daily/2026-04-29.md"
created: 2026-04-29
updated: 2026-04-29
---

# Hilt DI Graph Failure on Native Library Load

When a Hilt-managed dependency provider calls `System.loadLibrary()` and the load fails (e.g., `UnsatisfiedLinkError`, missing `.so`, `RegisterNatives` failure), the entire Hilt dependency injection graph breaks. The exception thrown during provider construction is not caught by the typical `try-catch` in `startCommand` of a `VpnService`, because Hilt's injection happens before the service method body executes.

This was identified as an alternative hypothesis during the Ozero VPN silent crash investigation on Android 15 (Nubia NX729J).

## Key Points

- `System.loadLibrary("hev-socks5-tunnel")` inside a Hilt `@Inject` provider can throw `UnsatisfiedLinkError`, `NoSuchMethodError`, or `LinkageError`
- Hilt constructs the DI graph during `onCreate` of the `@AndroidEntryPoint` component — exceptions here bypass any `try-catch` in service command handlers
- The crash manifests in the second phase of VPN startup: after click → `startService` → `onCreate`, not during the initial UI interaction
- For the specific Nubia silent crash (where click never reached `onConnectClick`), this hypothesis was ruled out because Hilt Service injection only fires after `startService` is called
- Ozero's `loadOnce()` pattern with `catch (e: Throwable)` (enforced by `TProxyServiceLogTest`) mitigates but does not eliminate this vector

## Details

In Ozero's architecture, native libraries are loaded via an idempotent `loadOnce()` function that catches `UnsatisfiedLinkError`, `SecurityException`, and a generic `Throwable` fallback. This pattern was established after the v1.0.1 SIGSEGV incident where eager loading in `Application.onCreate` caused crashes.

However, when `loadOnce()` is invoked from within a Hilt-provided dependency (e.g., a `@Provides` method or an `@Inject` constructor), the failure mode changes. Hilt wraps provider calls in its own generated code. If `loadOnce()` catches the error and sets an error state (rather than rethrowing), Hilt may proceed with a partially initialized object. If `loadOnce()` rethrows (or if an uncaught exception type leaks through), Hilt's entire graph construction fails, producing an opaque crash at `onCreate` time.

The multi-agent debug session concluded that while this is a real vulnerability, it does not explain the specific silent crash where the user's click never reached the `onConnectClick` handler — that crash occurs before any `startService` call, hence before Hilt Service injection.

## Related Concepts

- [[concepts/compose-launchedeffect-crash-invisibility]] - Both represent early-lifecycle crash vectors that produce minimal diagnostic evidence
- [[concepts/android-silent-crash-diagnosis]] - Hilt DI failure is one of several vectors requiring systematic diagnosis methodology
- [[concepts/nubia-rom-permission-enforcement]] - Nubia ROM strictness may compound native library loading issues

## Sources

- [[daily/2026-04-29.md]] - Alternative hypothesis (alt-vpn-dev) during Nubia silent crash investigation; ruled out for the specific crash but confirmed as a real vulnerability
