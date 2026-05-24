---
title: "Relay Coordinator Ownership Transfer Pattern"
aliases: [relay-owned, coordinator-ownership, relay-bridge-lifecycle]
tags: [urnetwork, architecture, coordinator-pattern, concurrency]
sources:
  - "daily/2026-05-17.md"
  - "daily/2026-05-22 (1).md"
created: 2026-05-17
updated: 2026-05-22
---

# Relay Coordinator Ownership Transfer Pattern

`UrnetworkRelayCoordinator` manages the URnetwork SDK bridge lifecycle for relay-always mode. A critical design challenge: when the active VPN engine is URnetwork, the engine itself starts the bridge; when the engine is ByeDPI/WARP, the relay coordinator starts the bridge. The `relayOwned: AtomicBoolean` flag tracks who started the bridge, ensuring only the owner stops it during transitions.

## Key Points

- `relayOwned = AtomicBoolean(false)` — true when the coordinator started the bridge, false when the engine owns it
- Bridge `start()` made idempotent: returns `Success` if already running (previously returned `Failed("already running")`)
- Coordinator starts bridge only for non-URnetwork engines; for URnetwork engine, only sets `providePaused = false`
- Engine switch BYEDPI→URNETWORK: coordinator releases ownership (`relayOwned = false`), engine takes over bridge lifecycle
- Engine switch URNETWORK→BYEDPI: coordinator starts bridge fresh (`relayOwned = true`)
- VPN stop: coordinator stops bridge ONLY if `relayOwned.get() == true` — avoids double-stop with engine teardown

## Details

### The Ownership Problem

Without ownership tracking, two actors can interfere with the bridge lifecycle:

1. **Double-start**: User switches to ByeDPI. Coordinator calls `bridge.start()`. User switches to URnetwork. Engine also calls `bridge.start()` in `EngineUrnetwork.start()`. Two callers think they own the bridge.

2. **Orphan bridge**: User is on ByeDPI (coordinator owns bridge). User stops VPN. Engine teardown calls `bridge.stop()` — but bridge was started by coordinator, not engine. Or: coordinator calls `bridge.stop()`, but engine teardown has already stopped it.

3. **Premature stop**: User switches URnetwork→ByeDPI. Engine teardown stops bridge. Coordinator hasn't started it yet. A brief gap with no bridge running.

### The AtomicBoolean Solution

```kotlin
class UrnetworkRelayCoordinator @Inject constructor(
    private val bridge: UrnetworkSdkBridge,
    private val tunnelController: TunnelController,
) {
    private val relayOwned = AtomicBoolean(false)

    fun onEngineChanged(engineId: EngineId, vpnActive: Boolean) {
        if (!vpnActive) {
            if (relayOwned.compareAndSet(true, false)) {
                bridge.stop()
            }
            return
        }
        when (engineId) {
            EngineId.URNETWORK -> {
                // Engine owns bridge; coordinator just enables relay
                relayOwned.set(false)
                bridge.setProvidePaused(false)
            }
            else -> {
                // Coordinator owns bridge for relay
                bridge.start(relayConfig)
                relayOwned.set(true)
                bridge.setProvidePaused(false)
            }
        }
    }
}
```

`compareAndSet(true, false)` ensures stop is called exactly once by the owner. If the engine already stopped the bridge, `relayOwned` is `false` and the coordinator skips the stop.

### Bridge Idempotency Requirement

The coordinator calls `bridge.start()` without knowing if the engine already started it. The original bridge returned `Failed("already running")` — which the coordinator would interpret as an error. Making `start()` idempotent (return `Success` when already running) eliminates this concern. The coordinator always calls `start()` and always gets `Success` regardless of bridge state.

This was a deliberate API change in commit `194d7701`: `RealUrnetworkSdkBridge.start()` checks `isRunning()` first and returns early with `StartResult.Success` if true.

### Transition Sequences

| Transition | Coordinator action | Engine action | Bridge state |
|------------|-------------------|---------------|-------------|
| OFF → BYEDPI | start(), relayOwned=true | — | Running (coordinator) |
| BYEDPI → URNETWORK | relayOwned=false, setProvidePaused(false) | start() | Running (engine) |
| URNETWORK → BYEDPI | start(), relayOwned=true | stop() | Running (coordinator) |
| BYEDPI → OFF | stop() (relayOwned was true) | — | Stopped |
| URNETWORK → OFF | skip (relayOwned=false) | stop() | Stopped |

### Guest JWT and Wallet Requirements

The coordinator observes `byClientJwt` and `walletAddress` flows alongside tunnel state. Bridge start requires a valid guest JWT (for network authentication) and optionally a wallet address (for relay payout binding via `setupPayoutWallet`). If JWT is null, the coordinator skips bridge start — no authentication means no relay.

## Related Concepts

- [[concepts/urnetwork-relay-always]] - Architecture overview of relay-always; this article details the ownership transfer implementation
- [[concepts/engine-telegram-mtproxy]] - TelegramProxyCoordinator follows the same combine(tunnelState, config) observer pattern
- [[concepts/engine-ownership-boundary]] - VpnService owns engine lifecycle; coordinator owns relay lifecycle — distinct ownership domains

### providePaused Hardcode Bug (2026-05-22)

`UrnetworkRelayCoordinator` lines 67+81 originally called `bridge.setProvidePaused(false)` unconditionally in both branches — ignoring `configStore`. This meant:

- **URNETWORK branch**: coordinator overwrote the result of `EngineUrnetwork.start()` which had already applied configStore. If the user had disabled "provide" in settings, the coordinator immediately re-enabled it.
- **Relay branch (other engines)**: coordinator hardcoded `false` instead of reading `configStore.provideEnabled()`.

The bug was latent from commit `194d7701` (first implementation) and became visible only when the UI gained a settings toggle for `provide` that could be changed from NotConnected state.

**Fix** (commit `e0d53ca4`):

```kotlin
when (engineId) {
    EngineId.URNETWORK -> {
        // Engine already applied configStore — do NOT override
        relayOwned.set(false)
        // removed: bridge.setProvidePaused(false)
    }
    else -> {
        bridge.start(relayConfig)
        relayOwned.set(true)
        bridge.setProvidePaused(!configStore.provideEnabled())  // read from store
    }
}
```

`UrnetworkEngineSettingsViewModel` now exposes `providePaused: StateFlow` sourced from `configStore`, keeping UI and coordinator in sync.

## Sources

- [[daily/2026-05-17.md]] - Session current: UrnetworkRelayCoordinator implemented with relayOwned AtomicBoolean; bridge.start() made idempotent; commit 194d7701; ownership transfer between engine and coordinator for URNETWORK↔other transitions
- [[daily/2026-05-22 (1).md]] - Session 16:02+: setProvidePaused(false) hardcoded in both coordinator branches since 194d7701; ignored configStore; engine branch overwrote EngineUrnetwork.start() result; fix: URNETWORK branch removes call, relay branch reads configStore.provideEnabled(); commit e0d53ca4
