---
title: URnetwork relay provideEnabled boundary
sources:
  - [[daily/2026-05-29]]
created: 2026-05-29
updated: 2026-05-29
---
# URnetwork relay provideEnabled boundary

## Key Points
- URnetwork relay is separate from the main URnetwork engine and must not be conflated with engine readiness.
- `provideEnabled=false` is user intent and must prevent relay reactivation by network callbacks.
- Relay network monitoring may clear temporary network pause, but it must not override the configured provide switch.
- Wallet registration uses upstream-sensitive chain tokens; `"SOL"` and `"solana"` are not equivalent.
- Relay fixes should preserve bridge ownership and dummy IoLoop/offline TUN behavior.

## Details

After URnetwork engine readiness was fixed, the 2026-05-29 investigation moved to relay. The intended design was confirmed: relay lives separately from the URnetwork engine, can run while another engine is active, uses a dummy IoLoop/offline TUN pattern, and must not stop a bridge owned by the main engine.

Two relay integration defects were identified. First, `RelayNetworkMonitor` could reactivate provide by clearing `providePaused` even when `provideEnabled=false`. This violated user intent because a connectivity callback could restart relay after the user explicitly disabled sharing. Second, payout wallet registration used `"solana"` where upstream uses `"SOL"`, causing an API contract mismatch.

The resulting fix made `provideEnabled` an explicit boundary passed into the relay network monitor and changed wallet setup to use the upstream token. This keeps network callbacks limited to temporary connectivity recovery and prevents them from becoming an implicit relay enable switch.

## Related Concepts
- [[concepts/urnetwork-provide-tun-investigation]]
- [[concepts/relay-coordinator-ownership-transfer]]
- [[concepts/urnetwork-walletauth-per-device-registration]]
- [[concepts/urnetwork-provide-secret-keys-identity]]

## Sources
- [[daily/2026-05-29]]: relay was confirmed as separate from the URnetwork engine and not part of the main attach/readiness fix.
- [[daily/2026-05-29]]: `RelayNetworkMonitor` could reactivate relay despite `provideEnabled=false`.
- [[daily/2026-05-29]]: `UrnetworkPayoutWalletSetup` changed token chain from `"solana"` to upstream `"SOL"`.
- [[daily/2026-05-29]]: commit `54faf668 FIX: Исправление реле URnetwork` implemented the relay boundary fixes.
