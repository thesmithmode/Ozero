---
title: "URnetwork Provider Identity: provideSecretKeys Persistence and JWT Refresh Listener"
aliases: [provide-secret-keys, urnetwork-relay-identity, provide-keys-listener]
tags: [urnetwork, relay, provider, identity, sdk, architecture]
sources:
  - "daily/2026-05-23.md"
created: 2026-05-23
updated: 2026-05-23
---

# URnetwork Provider Identity: provideSecretKeys Persistence and JWT Refresh Listener

URnetwork SDK uses two separate identity mechanisms for relay providers. JWT (`byClientJwt`) is the billing identity — who you are for USDC payouts. `provideSecretKeys` are the mesh identity — your node's cryptographic identifier in the P2P relay network. If `provideSecretKeys` change between restarts, the URnetwork mesh treats each restart as a new provider node: no traffic is routed to a node the mesh has never seen before. This was the root cause of 0 relay bytes despite successful VPN connections.

## Key Points

- `addProvideSecretKeysListener` **must be registered BEFORE** calling `initProvideSecretKeys()` — otherwise the callback fires inside `initProvideSecretKeys()` and the result is lost
- Upstream `DeviceManager.kt:121-130` registers the listener to save generated keys into `localState.provideSecretKeys`; Ozero's `RealUrnetworkSdkBridge` was missing this call entirely
- Without the listener: `initProvideSecretKeys()` generates a fresh keypair each restart → different provider identity every time → mesh routes 0 bytes to node
- `addJwtRefreshListener` — second listener added in the same fix; updates `localState.byClientJwt` when SDK refreshes the JWT automatically (token expiry). Without this, `byClientJwt` in DataStore becomes stale → coordinator stops seeing it as valid → relay pauses
- `provideSecretKeys` live in SDK's `space` object (persisted by the SDK itself); they are NOT Ozero's DataStore — we only need to pass them back via `localState` to maintain symmetry

## Details

### Two Identity Components

| Component | What it is | When needed | Saved where |
|-----------|-----------|-------------|-------------|
| `byClientJwt` | Billing identity (non-guest JWT from walletAuth) | Relay coordinator start, wallet API calls | Ozero DataStore |
| `provideSecretKeys` | Mesh identity (P2P relay node keypair) | `device.initProvideSecretKeys()` | SDK `space` + Ozero `localState` via listener |

JWT and `provideSecretKeys` must BOTH be stable across restarts for relay to function:
- Unstable JWT → relay coordinator skips bridge start (JWT-gated check)
- Unstable provideSecretKeys → mesh routes 0 bytes (unknown node identity)

Pre-fix Ozero had: stable JWT (after 2026-05-18 walletAuth fix) but unstable provideSecretKeys (no listener). Result: relay coordinator started the bridge, SDK appeared "running", but mesh received 0 traffic.

### Fix (commit cc9e3c67)

```kotlin
// In RealUrnetworkSdkBridge.runStartOnMain (and ensureDeviceOnMain):

// BEFORE — missing listener:
device.initProvideSecretKeys()

// AFTER — register listener FIRST:
device.addProvideSecretKeysListener { keys ->
    localState = localState.copy(provideSecretKeys = keys)
    // saves to SDK space on listener callback; stable across restarts
}
device.initProvideSecretKeys()

// Also added JWT refresh listener:
device.addJwtRefreshListener { jwt ->
    localState = localState.copy(byClientJwt = jwt)
}
```

### Listener Registration Order is Critical

`initProvideSecretKeys()` is synchronous — if the keys are already saved in SDK space, the listener fires immediately during the `initProvideSecretKeys()` call. If the listener is registered AFTER the call, the callback has already fired and is gone. This is a classic "register before subscribe" trap identical to Android `LiveData.observe()` or RxJava `subscribe()` ordering.

Sentinel tests in `RealUrnetworkSdkBridgeContractTest`:
- `should call addProvideSecretKeysListener when localState.provideSecretKeys is null`
- `should persist provideSecretKeys across bridge restarts`
- `should call addJwtRefreshListener to track JWT refreshes`
- Two additional behavioral sentinels for registration order

### Runtime Behavior

After the fix, the relay behavior for first-time users is:
1. First `start()` call: `initProvideSecretKeys()` generates new keys → listener fires → keys saved to `localState.provideSecretKeys` (SDK-persisted)
2. Subsequent `start()` calls: `initProvideSecretKeys()` loads existing keys from SDK space → listener fires with SAME keys → `localState.provideSecretKeys` unchanged
3. Mesh identity = stable → traffic routed to this provider node

## Related Concepts

- [[concepts/urnetwork-relay-always]] — Relay coordinator architecture; JWT-gating of bridge start; this fix ensures provideSecretKeys stability alongside JWT stability
- [[concepts/urnetwork-walletauth-per-device-registration]] — Per-device Ed25519 walletAuth that resolved JWT guest-mode blocker; provideSecretKeys is separate from walletAuth
- [[concepts/urnetwork-guest-mode-relay-blocker]] — Server-side JWT blocking that was the prior root cause; provideSecretKeys instability was the remaining cause
- [[concepts/relay-coordinator-ownership-transfer]] — Coordinator ownership pattern; provideSecretKeys are bridge state, coordinator just ensures stability

## Sources

- [[daily/2026-05-23.md]] — Session "URnetwork provider 0 bytes — root cause + fix": 5-subagent deep diagnostic; root cause: `addProvideSecretKeysListener` missing → keypair regenerated every restart → new mesh node identity → 0 bytes; also `addJwtRefreshListener` missing → stale JWT; fix in commit `cc9e3c67`; sentinel tests in `RealUrnetworkSdkBridgeContractTest`; architectural clarification: JWT = billing identity, provideSecretKeys = mesh identity
