---
title: "URnetwork Relay-Always Architecture (UrnetworkRelayCoordinator)"
aliases: [relay-always, urnetwork-relay-coordinator, urnetwork-provide]
tags: [urnetwork, relay, architecture, coordinator-pattern]
sources:
  - "daily/2026-05-14.md"
  - "daily/2026-05-17.md"
  - "daily/2026-05-18.md"
created: 2026-05-14
updated: 2026-05-18
---

# URnetwork Relay-Always Architecture

URnetwork SDK decouples TUN-routing (`tunnelStarted`) from relay contribution (`providePaused`). Relay works without an active TUN interface — meaning Ozero can contribute relay capacity in parallel with any VPN engine (ByeDPI, WARP, URnetwork, etc.).

## Key Points

- `tunnelStarted = true` required ONLY when URnetwork is the active routing engine (traffic goes through URnetwork TUN)
- `providePaused = false` (relay ON) works regardless of which engine is active — relay is IO-only, no TUN needed
- When any VPN engine is running → `device.providePaused = false`
- When URnetwork is the active engine → additionally `tunnelStarted = true`
- **Relay activation is JWT-gated, not automatic** — `byClientJwt` must be present in DataStore; coordinator skips bridge start when JWT is null. Pre-walletAuth (before 2026-05-18) this meant relay never started for users who had never selected URnetwork as active engine. Post-walletAuth (commit `0ef16e3a`) every device auto-registers an Ed25519 keypair on first engine start, producing a non-guest JWT → relay becomes effectively automatic for all users
- No user toggle
- Each user earns independently — no API to aggregate relay earnings across multiple users to one wallet
- `URnetworkBridge.setupPayoutWallet(address)` auto-binds dev payout wallet to non-guest accounts on engine start (guest JWTs are blocked server-side, see [[concepts/urnetwork-guest-mode-relay-blocker]]). 3 contract tests in `URnetworkBridgeSetupPayoutWalletTest`

## Architecture

`UrnetworkRelayCoordinator` follows the same pattern as `TelegramProxyCoordinator`:
- Observes `TunnelController.state` via `combine(tunnelState, configStore.config())` AND `byClientJwt` flow
- When VPN starts (any engine) AND JWT is non-null: sets `device.providePaused = false`
- When VPN stops: sets `device.providePaused = true`
- When active engine changes to URnetwork: sets `tunnelStarted = true`
- When active engine changes away from URnetwork: sets `tunnelStarted = false`
- When JWT is null (no authentication): skips bridge start entirely — relay cannot activate without a JWT
- Initialized in `OzeroApp.onCreate` via Hilt inject + `runCatching`

## Monetization Constraints

- No public URnetwork SDK API for partner/referral aggregation of guest relay earnings
- `BRINGYOUR_BUNDLE_WALLET = "solana"` in PORTAL TOR APK = blockchain type string, not a wallet address
- One possible approach: embed single guest JWT so all devices share one "large node" → earnings to one wallet. Risk: ToS violation + account ban
- Decision (2026-05-14): relay-always without developer monetization; users earn independently

### Implementation Details (2026-05-17)

`UrnetworkRelayCoordinator` was implemented in commit `194d7701` with the following design:

- **`relayOwned: AtomicBoolean`** — tracks whether the coordinator started the bridge (vs engine owning it). Coordinator stops bridge only if `relayOwned.get() == true`, preventing double-stop with engine teardown.
- **`bridge.start()` idempotent** — returns `Success` if already running instead of `Failed("already running")`. Required because coordinator calls `start()` without knowing if engine already started it.
- **Ownership transfer**: when active engine changes to URnetwork, coordinator sets `relayOwned = false` (engine takes over); when changing away from URnetwork, coordinator starts bridge fresh (`relayOwned = true`).
- **JWT gating**: coordinator observes `byClientJwt` flow; skips bridge start when JWT is null (no authentication = no relay).
- SharedTrafficScreen simplified to single `unpaidBytes` field (commit d3c32b9f).

See [[concepts/relay-coordinator-ownership-transfer]] for the full ownership transfer pattern.

### JWT Bootstrap Requirement and Guest Mode Limitation (2026-05-18)

Critical operational discovery: `UrnetworkRelayCoordinator` requires `byClientJwt` in DataStore to start the bridge. JWT is saved only on first URnetwork engine connection (via `acquireGuestJwt()`). A new user who has never selected URnetwork as the active engine has no JWT → relay never starts → no bandwidth contribution.

Furthermore, guest JWTs are blocked from earning rewards server-side. `router/handler_utils.go` returns 401 for all wallet endpoints when `guestMode=true`. The relay shares bandwidth but receives no USDC payouts. See [[concepts/urnetwork-guest-mode-relay-blocker]] for full analysis.

This means relay-always is currently: (1) inactive for users who never tried URnetwork, (2) running idle (no earnings) for users who did. A non-guest JWT is required for actual monetization.

### walletAuth Resolution (2026-05-18, commit 0ef16e3a)

The guest mode monetization blocker was resolved by implementing per-device Ed25519 walletAuth auto-registration. Each device generates a keypair, calls `authLogin(walletAuth)` to obtain a non-guest `byJwt`, then `setupPayoutWallet(PRESET_WALLET)` succeeds because the server accepts wallet API calls from non-guest accounts. Existing guest users are migrated on first engine start — their guest `byJwt` is replaced atomically with a walletAuth-derived JWT. See [[concepts/urnetwork-walletauth-per-device-registration]] for full implementation details.

### Discovery: Relay Was Not Working (2026-05-17)

Critical finding during session: relay only worked when URnetwork was the active engine. When user selected ByeDPI or WARP, URnetwork SDK never started → relay didn't run → no traffic shared → no payouts. The user directly challenged: "ты уверен что это работает?" — agent honestly answered "НЕТ." The `UrnetworkRelayCoordinator` was the P0 fix to make relay truly engine-independent.

Additional constraint discovered: `EngineUrnetwork.start()` calls `sdkBridge.start()` without `isRunning()` check → `Failed("already running")` if bridge was already started by relay coordinator. Idempotent `start()` was the structural fix.

## Related Concepts

- [[concepts/engine-telegram-mtproxy]] — TelegramProxyCoordinator is the pattern this mirrors
- [[concepts/vpn-engine-pipeline]] — ManualEngineSource / StrategyEngine wiring that UrnetworkRelayCoordinator observes
- [[concepts/relay-coordinator-ownership-transfer]] — Detailed ownership transfer pattern with AtomicBoolean guard
- [[concepts/urnetwork-walletauth-per-device-registration]] — Per-device Ed25519 auth that resolved guest mode monetization blocker
- [[concepts/urnetwork-guest-mode-relay-blocker]] — The guest JWT monetization problem walletAuth solved

## Sources

- [[daily/2026-05-14.md]] — Session 20:30: architectural discussion of relay-always, SDK `tunnelStarted` vs `providePaused` decoupling, monetization constraints; Session 21:29: `setupPayoutWallet` implementation + contract tests
- [[daily/2026-05-17.md]] — Session 14:41+: relay NOT working for non-URnetwork engines discovered; P0 #111 UrnetworkRelayCoordinator implemented (commit 194d7701); relayOwned AtomicBoolean ownership; bridge.start() made idempotent; SharedTrafficScreen simplified
- [[daily/2026-05-18.md]] — Session 17:51: relay requires byClientJwt (saved on first URnetwork connect only); Session 18:25: guest JWT blocked from wallet API server-side → relay runs idle, no USDC payouts
