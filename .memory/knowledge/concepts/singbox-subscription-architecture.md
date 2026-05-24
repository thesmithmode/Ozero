---
title: "Sing-box Subscription Architecture"
aliases: [singbox-subscriptions, singbox-room-db, subscription-fetch-process]
tags: [singbox, android, architecture, room, hilt, process-isolation]
sources:
  - "daily/2026-05-24.md"
created: 2026-05-24
updated: 2026-05-24
---

# Sing-box Subscription Architecture

Subscription fetching and storage for the sing-box engine must run exclusively in the `app/` process. The VPN service process (`:engine_singbox`) has no access to the app's `AppDatabase`, `OkHttpClient`, or `Gson` bindings. Classes that must run in the VPN process create HTTP/JSON dependencies inline. The split is: fetch → app process; config delivery → serialized bundle to VPN process.

## Key Points

- `SingboxSubscriptionRepository` lives in the `app/` process — `ViewModel → Repository → Room DAO`
- `SingboxSubscriptionFetcher` runs in app process; creates `OkHttpClient()` inline (not injected) to avoid cross-process Hilt failure
- `AppDatabase` must include `SubscriptionEntity` in its `entities` list + version bump + DAO accessor — all three required or runtime crash
- VPN service process only receives the active config as a `SingboxConfig` data class serialized via Gson; it does not touch Room
- `SingboxPresetRepository` provides built-in subscriptions (preset_groups.json assets); `SingboxSubscriptionRepository` stores user-added ones in Room
- Subscription fetching must happen ONLY in `app/` process — VPN service process cannot inject `AppDatabase` or `SubscriptionDao` from app/; classes in the VPN process that need HTTP must create `OkHttpClient()` inline

## Details

### Process Boundary

```
app/ process                         :engine_singbox process
────────────────────────────────     ──────────────────────────────
SubscriptionListViewModel            SingboxVpnService
  → SingboxSubscriptionRepository      @Inject SingboxGoRuntimeGuard
  → SubscriptionDao (Room)             @Inject SingboxBridge
  → SingboxSubscriptionFetcher           ↳ Gson() inline
    ↳ OkHttpClient() inline            SingboxEnginePlugin
SingboxPresetRepository                  ↳ createConfig() → SingboxConfig
  ↳ preset_groups.json assets          sends bundle to SingboxBridge
```

### DAO Contract

`SubscriptionDao` requires these methods; mismatches with the actual DAO names cause compile-time "Unresolved reference" errors in the repository:

```kotlin
@Dao interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions") fun getAll(): Flow<List<SubscriptionEntity>>
    @Query("SELECT * FROM subscriptions WHERE enabled = 1") fun getActiveSubscriptions(): Flow<List<SubscriptionEntity>>
    @Insert(onConflict = REPLACE) suspend fun insert(e: SubscriptionEntity)
    @Query("DELETE FROM subscriptions WHERE id = :id") suspend fun deleteById(id: String)
    @Query("UPDATE subscriptions SET enabled = :enabled WHERE id = :id") suspend fun updateEnabled(id: String, enabled: Boolean)
}
```

Calling `dao.getAllActive()` when the method is named `getActiveSubscriptions()` produces compile error; there is no runtime fallback.

### AppDatabase Registration

Adding `SubscriptionEntity` to the database requires all three steps:

1. Add to `@Database(entities = [..., SubscriptionEntity::class])`
2. Bump `version` integer (no migrations needed if `exportSchema = false` + `fallbackToDestructiveMigration`)
3. Add `abstract fun subscriptionDao(): SubscriptionDao`

Missing step 1 or 3 = compile error. Missing step 2 = Room runtime crash on first install upgrade.

### SingboxServer Sealed Hierarchy

Parsed servers use a sealed class hierarchy, not flat data classes. Reference implementations (VLESS parser) must use `SingboxServer.Vless(...)` not standalone `VlessServer(...)`:

```kotlin
sealed class SingboxServer {
    data class Vless(val host: String, val port: Int, val uuid: String, ...) : SingboxServer()
    data class Shadowsocks(val host: String, val port: Int, val method: String, val password: String) : SingboxServer()
    data class Vmess(val host: String, val port: Int, val uuid: String, ...) : SingboxServer()
    data class Trojan(val host: String, val port: Int, val password: String, ...) : SingboxServer()
    data class WireGuard(...) : SingboxServer()
}
```

Importing `WireGuardServer` (non-existent flat class) instead of referencing `SingboxServer.WireGuard` generates phantom imports that cascade into unrelated "Unresolved reference" errors throughout the file.

## Related Concepts

- [[concepts/hilt-cross-process-injection]] - Root cause: app/ bindings invisible in VPN process; solution = inline creation
- [[concepts/room-entity-database-registration]] - AppDatabase entities list + version bump + DAO accessor all required
- [[concepts/kapt-per-module-requirement]] - engine-singbox needs `kapt(libs.hilt.compiler)` and `kapt(libs.room.compiler)` explicitly
- [[concepts/cascade-unresolved-import-masking]] - Wrong sealed class import names cause phantom cascade errors
- [[concepts/singbox-engine-design]] - Overall engine architecture; subscription architecture feeds config into Go runtime

## Sources

- [[daily/2026-05-24.md]] — Sessions 19:13, 19:34, 19:53: engine-singbox P4/P5 implementation; cross-process Hilt trap for OkHttp/Gson/AppDatabase; subscription fetch must stay in app/ process only; VPN process cannot inject AppDatabase or SubscriptionDao; classes in VPN process create OkHttpClient inline; DAO method naming mismatches (getAllActive vs getActiveSubscriptions); SingboxServer sealed class vs flat class imports; AppDatabase version bump requirement
