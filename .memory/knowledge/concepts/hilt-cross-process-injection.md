---
title: "Hilt Cross-Process Injection Failure"
aliases: [hilt-process-isolation, hilt-singleton-per-process, vpn-process-hilt]
tags: [hilt, android, di, process-isolation, engine]
sources:
  - "daily/2026-05-24.md"
created: 2026-05-24
updated: 2026-05-24
---

# Hilt Cross-Process Injection Failure

When an Android VPN service runs in a separate process (`:engine_singbox`, `:engine_warp`), Hilt creates an independent `SingletonComponent` for that process. Bindings provided by modules in the `app/` process — `OkHttpModule`, `DatabaseModule`, `GsonModule` — are invisible in the VPN service process. Attempting to `@Inject` them in the service process causes a Hilt binding resolution failure at runtime or Dagger compile error.

## Key Points

- Hilt's `SingletonComponent` is per-process; app process and VPN service process each have their own instance
- `OkHttpClient`, `AppDatabase`, `Gson` provided in `app/` modules cannot be injected in the VPN process
- Fix: create dependencies inline in VPN-process classes — `OkHttpClient()` and `Gson()` constructor calls — rather than injecting them
- Alternatively: move the operation requiring the dependency to the `app/` process and use AIDL/IPC
- `@InstallIn(SingletonComponent::class)` in a module used by VPN service does NOT share the app's SingletonComponent
- `kapt(libs.hilt.compiler)` must be in every Gradle module that uses `@AndroidEntryPoint` or `@HiltViewModel` — Hilt annotation processing is not transitive

## Details

### Discovery Context

During `engine-singbox` P4 (subscriptions) implementation, `SingboxSubscriptionFetcher` was given an `@Inject constructor(private val client: OkHttpClient)`. This compiled but would fail at runtime in the VPN service process because `OkHttpClient` is only provided by `OkHttpModule` in the `app/` process. The same issue applies to `Gson` and `AppDatabase`.

The correct resolution: subscription fetching belongs in the `app/` process (ViewModel → Repository → Room). The VPN service process only receives the active config as a serialized bundle. Classes that run in the VPN process and need HTTP or JSON must construct their dependencies inline.

### Boundary Rule

```
app/ process:              VPN service process:
  OkHttpModule               @Inject OkHttpClient → FAIL
  DatabaseModule             AppDatabase → FAIL
  GsonModule                 Gson → FAIL

VPN classes must:          Instead:
  OkHttpClient()               create inline
  Gson()                       create inline
  Room via AIDL                use AIDL/bundle
```

### `@InstallIn` Does Not Bridge Processes

A module annotated `@InstallIn(SingletonComponent::class)` and listed in the app's Hilt component graph is still only visible in the process where Hilt initializes it. If `SingboxVpnService` starts in `:engine_singbox` process, Hilt initializes a fresh `SingletonComponent` there, without the app's module bindings.

## Related Concepts

- [[concepts/go-runtime-process-isolation]] - Why VPN engines run in separate processes in Ozero
- [[concepts/engine-masterdns]] - MasterDNS process pattern; ProcessBuilder subprocess also has no Hilt
- [[concepts/hilt-assistedinject-mixed-injection]] - Other Hilt injection edge cases in Ozero
- [[concepts/hilt-di-native-library-failure]] - Hilt failure when native library load fails

## Sources

- [[daily/2026-05-24.md]] — Session 19:13: engine-singbox P4 CI failures; `OkHttpClient`/`Gson` inject from app/ failed in VPN process; fix = inline construction (`OkHttpClient()`, `Gson()` constructors); `AppDatabase` also unavailable cross-process; architecture corrected to fetch subscriptions in app/ process only; `kapt(libs.hilt.compiler)` missing from engine-singbox module — Hilt annotation processing not transitive
