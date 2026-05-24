---
title: "Sing-box Engine Design for Ozero"
aliases: [engine-singbox, singbox-architecture, singbox-urltest, singbox-presets]
tags: [sing-box, engine, architecture, go-runtime, android]
sources:
  - "daily/2026-05-24.md"
created: 2026-05-24
updated: 2026-05-24
---

# Sing-box Engine Design for Ozero

The `engine-singbox` module integrates [sing-box](https://github.com/SagerNet/sing-box) into Ozero as a multi-protocol VPN engine supporting 22+ protocols (VLESS, VMess, Trojan, Shadowsocks, and others). The design must respect the Go-runtime coexistence invariant (only one Go-based engine active at a time), use physical Gradle module isolation to prevent Room DB from leaking into the sing-box process, and provide preset subscription groups borrowed from КИБЕРЩИТ-X.

## Key Points

- Go-runtime guard is mandatory: sing-box uses `libgojni.so`, same as URnetwork → mutually exclusive; `GoRuntimeGuard` prevents concurrent activation
- AD-15 Gradle physical isolation: `singbox-process/build.gradle.kts` must NOT depend on `:singbox-room`; prevents DataStore/Room classes from being loaded in the sing-box process
- urlTest architecture: one sing-box config JSON with N outbounds (tag = profileId), native sing-box `urltest` selector chooses best — no per-instance process spawning
- Preset groups from КИБЕРЩИТ-X: `assets/singbox/preset_groups.json` + `SubscriptionBean.isBuiltin = true` + `GroupSeeder` that re-seeds on first run; builtin groups are undeletable
- Process isolation strategy: `:engine_singbox` process (like WARP), not ProcessBuilder — sing-box is a native Go binary requiring JNI, not a subprocess binary

## Details

### Go-Runtime Coexistence (AD-15)

Ozero has a hard constraint: only one Go-based engine may be active at a time. `libgojni.so` appears in both URnetwork and sing-box — loading it twice in the same process causes SIGABRT. The mitigation is two-layered:

1. **Gradle physical isolation**: `singbox-process` module has no compile dependency on `:singbox-room`. Room/DataStore classes for server storage are inaccessible from the sing-box JVM process, preventing accidental cross-process calls.
2. **Runtime guard**: `GoRuntimeGuard.acquire(owner = "singbox")` at engine start; paired `release(owner = "singbox")` at stop. If URnetwork holds the guard, sing-box start fails fast with a clear error.

The sentinel `OzeroAppProcessIsolationTest` verifies that `libgojni.so` is never loaded outside its designated process.

### urlTest Architecture

When a user has multiple server profiles, sing-box's native `urltest` outbound selector is used rather than spawning one process per profile. A single JSON config is generated with one outbound per profile (tag = `profileId.toString()`). The `urltest` outbound tests each periodically and routes traffic through the lowest-latency option. This approach avoids N concurrent Go runtimes — only one sing-box process is ever active.

### Preset Subscription Groups

КИБЕРЩИТ-X (a NekoBoxForAndroid fork) ships with preset subscription groups that point to maintained server lists. Ozero adopts the same `assets/singbox/preset_groups.json` format:

- `SubscriptionBean.isBuiltin: Boolean = false` marks preset entries
- `GroupSeeder` runs on first launch, inserting preset groups into the Room DB
- Builtin groups cannot be deleted by the user (enforced in the ViewModel layer)
- Sentinel tests: `SingboxPresetGroupsJsonExistsSentinelTest` and `SingboxBuiltinGroupNotDeletableSentinelTest`

### Kill Switch and Split Tunnel

Sing-box inherits kill switch and split tunnel from `OzeroVpnService` — these are OS-level features already implemented for all engines. The sing-box `route.final=block` config option is an *additional* sing-box-level routing block, not a replacement for the OS-level kill switch.

### Plan History

The design went through multiple revisions:
- v1.0: initial draft with ProcessBuilder approach (rejected — sing-box needs JNI)
- v1.1: AD-15 reworded (Gradle isolation as primary, runtime guard as fallback); urlTest multi-outbound explanation added
- v1.2: preset groups (AD-14) restored after a user message about "no presets" was traced to a different chat context (it referred to WARP, not sing-box)

## Related Concepts

- [[concepts/go-runtime-process-isolation]] - Coexistence constraint that makes GoRuntimeGuard mandatory
- [[concepts/engine-masterdns]] - ProcessBuilder pattern (subprocess) vs sing-box's JNI process isolation
- [[concepts/dual-go-runtime-eager-loading]] - Root cause: two Go runtimes → SIGABRT
- [[concepts/warp-handle-leak-sigabrt]] - Similar SIGABRT class from handle leak in WARP engine
- [[concepts/engine-ownership-boundary]] - app/ knows contract not implementation; sing-box module structure follows this
- [[concepts/core-backup-module]] - Backup module will need to handle sing-box server configs in addition to WireGuard configs

### P4/P5 Implementation — Subscription Modules

Subscription management is split across two Gradle modules:

- **`singbox-room`**: `SubscriptionEntity`, `SubscriptionDao`, `AppDatabase` extension (requires `kapt(libs.room.compiler)`)
- **`singbox-subscription`**: `SingboxSubscriptionFetcher`, `SingboxSubscriptionParser`, `SingboxPresetRepository`

The subscription fetch pipeline runs in the `app/` process only (ViewModel → Repository → Room). The VPN process receives the active config as a serialized JSON bundle — it never touches Room directly. This respects the [[concepts/hilt-cross-process-injection]] constraint.

`SingboxSubscriptionParser` supports four input formats: sing-box JSON (`"outbounds"` key), YAML Clash proxy list (`proxies:`), base64-encoded content, and plain URI link lists. Each format is detected by content inspection and dispatched to the appropriate sub-parser. Server types: `SingboxServer` sealed class with `Vless`, `Shadowsocks`, `Vmess`, `Trojan`, `WireGuard` subclasses.

Key pitfalls encountered during P4 CI cycles:
- `return` inside expression body functions (`fun f() = try { ... }`) is forbidden — must use block body
- `return@label` works only in lambdas, not regular function bodies  
- `kapt(libs.hilt.compiler)` missing from `engine-singbox/build.gradle.kts` — Hilt won't process annotations
- `AppDatabase` must have `SubscriptionEntity` added to `entities = [...]` and version bumped — see [[concepts/room-entity-database-registration]]
- Non-local `return` inside `map { }` / `mapNotNull { }` when the outer function is `inline` causes unexpected early exit from the enclosing function

## Related Concepts

- [[concepts/go-runtime-process-isolation]] - Coexistence constraint that makes GoRuntimeGuard mandatory
- [[concepts/engine-masterdns]] - ProcessBuilder pattern (subprocess) vs sing-box's JNI process isolation
- [[concepts/dual-go-runtime-eager-loading]] - Root cause: two Go runtimes → SIGABRT
- [[concepts/warp-handle-leak-sigabrt]] - Similar SIGABRT class from handle leak in WARP engine
- [[concepts/engine-ownership-boundary]] - app/ knows contract not implementation; sing-box module structure follows this
- [[concepts/hilt-cross-process-injection]] - Subscription fetch must stay in app/ process; VPN process constructs deps inline
- [[concepts/kapt-per-module-requirement]] - engine-singbox needs its own kapt declarations for Hilt + Room
- [[concepts/room-entity-database-registration]] - Adding SubscriptionEntity required AppDatabase update + version bump
- [[concepts/kotlin-expression-body-return-trap]] - Hit repeatedly during Parser implementation
- [[concepts/ci-gradle-log-reading]] - Only way to see Kotlin errors during 15+ CI iterations

## Sources

- [[daily/2026-05-24.md]] — Evening sessions: sing-box engine design plan v1.0→v1.2; urlTest multi-outbound architecture; AD-15 Gradle physical isolation; preset_groups.json from КИБЕРЩИТ-X restored after misattributed user message; kill switch/split tunnel inheritance from OzeroVpnService; P4 subscription module implementation with Room + fetch pipeline; P5 UI subscription management; 15+ CI iterations resolving Kotlin compile errors
