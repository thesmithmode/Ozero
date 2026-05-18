---
title: "URnetwork Guest Mode Relay Monetization Blocker"
aliases: [guest-jwt-no-earnings, urnetwork-relay-idle, guest-mode-wallet-401]
tags: [urnetwork, monetization, architecture, gotcha]
sources:
  - "daily/2026-05-18.md"
created: 2026-05-18
updated: 2026-05-18
---

# URnetwork Guest Mode Relay Monetization Blocker

URnetwork SDK guest accounts (`guestMode=true`) are completely blocked from earning relay rewards. Server-side middleware in `router/handler_utils.go` returns HTTP 401 for all wallet-related endpoints (CreateAccountWallet, GetAccountWallets, RemoveWallet) when the JWT indicates guest mode. The relay operates — traffic is shared — but no USDC payouts occur. `setupPayoutWallet(address)` succeeds client-side but the server never processes the wallet binding.

## Key Points

- `guestMode=true` in JWT → server returns 401 on ALL wallet endpoints (CreateAccountWallet, GetAccountWallets, RemoveWallet)
- Server-side enforcement in `router/handler_utils.go` via `WrapRequireAuth` middleware — cannot be bypassed client-side
- Relay traffic IS shared (bandwidth contributed to network) but NOT compensated — runs "вхолостую" (idle from monetization perspective)
- UI evidence: `AccountViewModel.kt:73` shows `LoginMode.Guest → GuestModeOverlay` with "To start earning, create account"
- `UrnetworkJwtBootstrap` (guest JWT at app start) — correct for relay activation but useless for earning
- `PRESET_WALLET` (`27wATh...vHMM`) hardcoded in bridge — intentional design for developer earnings, not a bug

## Details

### The Server-Side Block

URnetwork's Go server uses middleware (`WrapRequireAuth`) that checks JWT claims before routing to wallet handlers. When `guestMode=true`:

```go
// router/handler_utils.go
func WrapRequireAuth(handler) {
    if claims.GuestMode {
        return 401  // Unauthorized for guest accounts
    }
    handler(claims)
}
```

This applies to the entire wallet API surface. A guest account can:
- Connect to the P2P mesh (relay traffic)
- Discover peers
- Route traffic through the network

A guest account CANNOT:
- Create a wallet binding
- View wallet balances
- Receive USDC payouts

### Impact on Ozero Architecture

`UrnetworkRelayCoordinator` starts the relay whenever any VPN engine is active. It calls `sdkBridge.start()` which acquires a guest JWT via `acquireGuestJwt()` if no authenticated JWT exists. The relay functions correctly — the device becomes a P2P node contributing bandwidth. However, the `setupPayoutWallet(PRESET_WALLET)` call that follows has no server-side effect because the guest JWT triggers 401 on the wallet binding endpoint.

Net result: Ozero users contribute bandwidth to URnetwork's mesh but receive nothing in return. The developer's hardcoded wallet is never registered server-side. All relay traffic from guest accounts is effectively free bandwidth for URnetwork.

### Discovery Path

The investigation traced through three code layers:

1. **Client**: `AccountViewModel.kt:73` — `LoginMode.Guest` shows `GuestModeOverlay` suggesting account creation
2. **Server**: `router/handler_utils.go` — `WrapRequireAuth` blocks guest JWT on wallet endpoints
3. **Server**: `api/handlers/account_wallet_handlers.go` — wallet handlers behind the auth middleware

### Resolution Options

Three paths forward, in order of feasibility:

1. **Master Ozero account** (recommended): Create one authenticated URnetwork account with full JWT. Embed credentials or use a server-side token exchange. All Ozero devices authenticate as the same "Ozero relay node." Earnings aggregate to one wallet.

2. **Solana wallet auth**: URnetwork may support wallet-based authentication that bypasses guest mode. Requires further SDK investigation.

3. **Per-user full account**: Each Ozero user creates a URnetwork account. Maximum decentralization but adds onboarding friction and account management complexity.

Decision pending from user (as of 2026-05-18).

### PRESET_WALLET Design Intent

The hardcoded wallet address `27wATh...vHMM` in `RealUrnetworkSdkBridge` is the developer's Solana address, set via `setupPayoutWallet()`. This is intentional design — all relay earnings from Ozero devices should go to the project developer, not individual users. Users don't see or configure a wallet. The blocker is that guest mode prevents this wallet from being registered server-side.

## Related Concepts

- [[concepts/urnetwork-relay-always]] - Relay-always architecture that this monetization blocker undermines; relay runs but earns nothing
- [[concepts/relay-coordinator-ownership-transfer]] - Coordinator manages relay lifecycle; JWT gating is the precondition for start
- [[concepts/urnetwork-sdk-integration]] - Parent integration article; guest JWT bootstrapping was added for relay activation

## Sources

- [[daily/2026-05-18.md]] - Session 18:25: investigation of relay payouts; server-side 401 for guestMode=true on all wallet endpoints; AccountViewModel.kt:73 GuestModeOverlay; handler_utils.go WrapRequireAuth; three resolution options proposed; decision pending
