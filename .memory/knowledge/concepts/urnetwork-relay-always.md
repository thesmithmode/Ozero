---
title: "URnetwork Relay-Always Architecture (UrnetworkRelayCoordinator)"
aliases: [relay-always, urnetwork-relay-coordinator, urnetwork-provide]
tags: [urnetwork, relay, architecture, coordinator-pattern]
sources:
  - "daily/2026-05-14.md"
created: 2026-05-14
updated: 2026-05-14
---

# URnetwork Relay-Always Architecture

URnetwork SDK decouples TUN-routing (`tunnelStarted`) from relay contribution (`providePaused`). Relay works without an active TUN interface — meaning Ozero can contribute relay capacity in parallel with any VPN engine (ByeDPI, WARP, URnetwork, etc.).

## Key Points

- `tunnelStarted = true` required ONLY when URnetwork is the active routing engine (traffic goes through URnetwork TUN)
- `providePaused = false` (relay ON) works regardless of which engine is active — relay is IO-only, no TUN needed
- When any VPN engine is running → `device.providePaused = false`
- When URnetwork is the active engine → additionally `tunnelStarted = true`
- Relay activates automatically whenever VPN is running; no user toggle
- Each user earns independently — no API to aggregate relay earnings across multiple users to one wallet
- `PRESET_WALLET` constant in `UrnetworkDefaults` is dead code — hardcoded value is passed to `start()` but `addExternalWallet`/`updatePayoutWallet` SDK calls are not invoked in `runStartOnMain()`

## Architecture

`UrnetworkRelayCoordinator` follows the same pattern as `TelegramProxyCoordinator`:
- Observes `TunnelController.state` via `combine(tunnelState, configStore.config())`
- When VPN starts (any engine): sets `device.providePaused = false`
- When VPN stops: sets `device.providePaused = true`
- When active engine changes to URnetwork: sets `tunnelStarted = true`
- When active engine changes away from URnetwork: sets `tunnelStarted = false`
- Initialized in `OzeroApp.onCreate` via Hilt inject + `runCatching`

## Monetization Constraints

- No public URnetwork SDK API for partner/referral aggregation of guest relay earnings
- `BRINGYOUR_BUNDLE_WALLET = "solana"` in PORTAL TOR APK = blockchain type string, not a wallet address
- One possible approach: embed single guest JWT so all devices share one "large node" → earnings to one wallet. Risk: ToS violation + account ban
- Decision (2026-05-14): relay-always without developer monetization; users earn independently

## Related Concepts

- [[concepts/engine-telegram-mtproxy]] — TelegramProxyCoordinator is the pattern this mirrors
- [[concepts/vpn-engine-pipeline]] — ManualEngineSource / StrategyEngine wiring that UrnetworkRelayCoordinator observes

## Sources

- [[daily/2026-05-14.md]] — Session 20:30: architectural discussion of relay-always, SDK `tunnelStarted` vs `providePaused` decoupling, PRESET_WALLET dead code, monetization constraints
