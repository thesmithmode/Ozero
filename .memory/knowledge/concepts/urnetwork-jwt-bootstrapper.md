---
title: "URnetwork JWT Bootstrapper: Extract, Session-Flag, Migration Pre-Check Bug"
aliases: [jwt-bootstrapper, urnetwork-bootstrap-extract, jwt-migration-precheck]
tags: [urnetwork, jwt, relay, architecture, coordinator-pattern, migration]
sources:
  - "daily/2026-05-23.md"
created: 2026-05-23
updated: 2026-06-12
---

# URnetwork JWT Bootstrapper: Extract, Session-Flag, Migration Pre-Check Bug

JWT acquisition for URnetwork relay was originally embedded inside `EngineUrnetwork.start()`. This created an architectural gap: `UrnetworkRelayCoordinator` could start the bridge only when a JWT was already present in DataStore, but could not trigger acquisition itself. New users who never selected URnetwork as the active engine had no JWT → relay never activated → 0 payout bytes. The fix extracts JWT bootstrap into a standalone `UrnetworkJwtBootstrapper` singleton injectable by both the engine and coordinator.

## Key Points

- **`UrnetworkJwtBootstrapper` interface** + `RealUrnetworkJwtBootstrapper` impl in `engine-urnetwork/`; `@Provides @Singleton` in `UrnetworkModule` — shared singleton injected into both `EngineUrnetwork` and `UrnetworkRelayCoordinator`
- **Session-flag** (`AtomicBoolean bootstrapAttemptedThisSession`): resets on `!Connected`. Prevents retry thrash within one session, but allows retry after disconnect+reconnect on unstable networks
- **Mutex + double-check**: protects against concurrent POST race when user starts URnetwork engine and immediately switches to WARP before the server responds — two parallel `acquireGuestJwt` with same pubkey would produce 409 server-side
- **Migration pre-check bug** (commit `1a58aa88`): extracted bootstrapper pre-checked `if (byClientJwt != null) return AlreadyPresent`. This short-circuited the legacy guest migration path for users who had `byJwt="legacy", byClientJwt="legacy-cjwt", devicePubkey=null` — migration never ran → wallet API blocked → payouts broken
- Fix: pre-check computes `migrationPending = byJwt != null && devicePubkey.isNullOrBlank() && deviceIdentity != null`. `AlreadyPresent` returned only when `cjwt != null && !migrationPending`

## Details

### Bootstrap Gap Before Extract

The original architecture had JWT bootstrap coupled to `EngineUrnetwork.start()`:

```
User selects ByeDPI/WARP → EngineUrnetwork.start() never called
→ ensureGuestJwt() never called
→ byClientJwt stays null in DataStore
→ UrnetworkRelayCoordinator: JWT check fails → skips bridge start
→ Relay never activates → 0 relay bytes → 0 payout
```

This gap existed since commit `194d7701` (2026-05-17, when RelayCoordinator was added). It was NOT a regression of the `cc9e3c67` provideSecretKeys fix or `0ef16e3a` walletAuth fix — those fixed different root causes (mesh identity and server-side guest blocking respectively).

The investigation also corrected stale memory: "walletAuth resolved" did not mean "relay works for all first-run users." WalletAuth solved the server-side rejection of guest identities, but a new user running WARP or FPTN still had no trigger that would acquire a client JWT before the coordinator checked DataStore.

### Extract Architecture (commit e6bac9eb)

```kotlin
// engine-urnetwork/src/main/.../RealUrnetworkJwtBootstrapper.kt
class RealUrnetworkJwtBootstrapper @Inject constructor(
    private val configStore: UrnetworkConfigStore,
    private val sdkBridge: UrnetworkSdkBridge,
) : UrnetworkJwtBootstrapper {

    private val mutex = Mutex()
    private val bootstrapAttemptedThisSession = AtomicBoolean(false)

    override suspend fun ensureClientJwt(): BootstrapResult {
        if (!bootstrapAttemptedThisSession.compareAndSet(false, true)) return AlreadyAttempted
        return mutex.withLock {
            val cjwt = configStore.byClientJwt()
            if (cjwt != null && !isMigrationPending()) return@withLock AlreadyPresent
            // ensureGuestJwt → tryAcquireDeviceJwt → ensureClientJwt chain
        }
    }

    fun resetForNextSession() { bootstrapAttemptedThisSession.set(false) }
}
```

`UrnetworkRelayCoordinator` calls `bootstrapper.ensureClientJwt()` when `tunnel.Connected(engineId != URNETWORK) && byClientJwt == null`. After the call, `configStore.byClientJwt()` emits → coordinator's `combine` re-triggers → bridge starts.

### Migration Pre-Check Bug (commit 1a58aa88)

The extraction introduced a regression for users with legacy migration pending:

**Broken pre-check:**
```kotlin
val cjwt = configStore.byClientJwt()
if (cjwt != null) return AlreadyPresent  // ← BUG: short-circuits migration
```

**Legacy user state:** `byJwt="legacy_token"`, `byClientJwt="legacy_cjwt"`, `devicePubkey=null`  
**Effect:** pre-check sees `cjwt != null` → `AlreadyPresent` → `tryAcquireDeviceJwt` never called → `devicePubkey` stays null → wallet API blocked.

**Fixed pre-check:**
```kotlin
val migrationPending = configStore.byJwt() != null
    && configStore.devicePubkey().isNullOrBlank()
    && configStore.deviceIdentity() != null
val cjwt = configStore.byClientJwt()
if (cjwt != null && !migrationPending) return AlreadyPresent
```

Sentinel tests `DeviceWalletJwtSentinelTest` also required updating: they read `EngineUrnetwork.kt` for migration logic grep patterns but the logic moved to `RealUrnetworkJwtBootstrapper.kt`. Updated `engineSource` to point at bootstrapper.

### Test Surface Tax

The extract changed the `EngineUrnetwork` constructor signature (bootstrapper injected). 14 test sites across `EngineUrnetworkDeviceJwtTest`, `DeviceWalletJwtSentinelTest`, and related files required updating. This is the cost of the extract — the shim/delegation pattern in `EngineUrnetwork.start()` provides backward compat at runtime but not at test compilation time.

Lesson: when extracting from a class with many test callsites, plan the test migration alongside the production code migration rather than as a follow-up.

## Related Concepts

- [[concepts/urnetwork-relay-always]] — JWT-gating in coordinator that bootstrapper now resolves; relay-always architecture
- [[concepts/urnetwork-walletauth-per-device-registration]] — Per-device Ed25519 walletAuth that the bootstrapper's migration path (tryAcquireDeviceJwt) executes for legacy users
- [[concepts/urnetwork-provide-secret-keys-identity]] — The other half of relay identity (mesh keypair); fixed in the prior session (cc9e3c67); bootstrapper handles the JWT half
- [[concepts/relay-coordinator-ownership-transfer]] — Coordinator ownership pattern; bootstrapper is a separate concern that coordinator calls into
- [[concepts/sentinel-protecting-bug-trap]] — `DeviceWalletJwtSentinelTest` reading old file (EngineUrnetwork.kt) after logic moved to bootstrapper — same class of sentinel drift

## Sources

- [[daily/2026-05-23.md]] — Session 15:57: JWT bootstrap blind spot discovered via 5 cross-validated haiku subagents; architectural gap since 194d7701; bug NOT regression from cc9e3c67/0ef16e3a; coordinator triggers ensureClientJwt() when Connected+non-URNETWORK+JWT==null. Session 16:22/16:30: UrnetworkJwtBootstrapper extract implementation; session-flag (AtomicBoolean); Mutex+double-check; sentinel behavior tests; 14 test sites updated (commit e6bac9eb). Session ~18:00: CI regression — migration pre-check `byClientJwt != null` short-circuits legacy migration; fix: migrationPending guard; sentinel source path updated EngineUrnetwork→RealUrnetworkJwtBootstrapper (commit 1a58aa88)
