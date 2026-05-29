---
title: URnetwork relay provideEnabled and SOL contract
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---

# URnetwork relay provideEnabled and SOL contract

## Key Points
- URnetwork relay is separate from the URnetwork engine and must not be diagnosed as the same path.
- `provideEnabled=false` is the user's source of truth and network callbacks must not re-enable relay.
- Upstream wallet registration uses chain token `SOL`, not `solana`.
- Relay can use the dummy IoLoop/offline TUN pattern without owning the main engine bridge.
- This connects [[concepts/urnetwork-provide-data-flow]] with [[concepts/relay-coordinator-ownership-transfer]].

## Details

After the URnetwork readiness fix, the daily log moved to relay analysis. The important boundary was architectural: URnetwork relay/provide is not the same as the URnetwork consumer engine. Relay starts when another non-URnetwork engine is active, uses its own provide flow, and must not be mixed into diagnosis of consumer `attachTun -> awaitReady`.

Two concrete relay defects were recorded. First, the payout wallet path used `"solana"` where upstream uses `"SOL"` for `addExternalWallet`. The log notes this can produce a registration acknowledgement without resolving the expected registry id. Second, `RelayNetworkMonitor` could remove `providePaused` or re-enable behavior after network callbacks even when `provideEnabled=false`, violating the user's explicit relay setting.

Commit `54faf668 FIX: Исправление реле URnetwork` corrected these boundaries: chain token `SOL`, network monitor respect for `provideEnabled`, and relay coordinator passing that flag into monitoring. This keeps network callbacks responsible for transient connectivity pauses only, not for overriding configuration.

## Related Concepts
- [[concepts/urnetwork-provide-data-flow]]
- [[concepts/relay-coordinator-ownership-transfer]]
- [[concepts/urnetwork-provide-tun-investigation]]
- [[concepts/urnetwork-walletauth-per-device-registration]]

## Sources
- [[daily/2026-05-29]]: Relay design was confirmed as separate from URnetwork engine and based on dummy IoLoop/offline TUN pattern.
- [[daily/2026-05-29]]: Upstream `WalletViewModel` uses `addExternalWallet(..., "SOL")`.
- [[daily/2026-05-29]]: Commit `54faf668` fixed chain token and prevented network callback from overriding `provideEnabled=false`.
