---
title: URnetwork relay user flag and wallet chain contract
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---
# URnetwork relay user flag and wallet chain contract

## Key Points
- URnetwork relay is separate from the URnetwork consumer engine and must not be diagnosed as the same lifecycle.
- `provideEnabled=false` is user intent and must prevent network callbacks from re-enabling relay.
- Relay payout wallet registration must use upstream chain token `SOL`, not `solana`.
- Relay network monitoring may manage temporary network pause, but not override persistent config.

## Details
The 2026-05-29 relay analysis confirmed two independent relay integration breaks. First, the wallet registration path used the wrong chain token. Upstream uses `SOL` for `addExternalWallet`, while the local code used `solana`; this could produce a registration acknowledgement without resolving the expected registry id.

Second, relay network callbacks could clear pause state and effectively re-enable provide mode even when the user had disabled relay. The corrected ownership rule is that `provideEnabled` is the source of truth. Network callbacks may respond to connectivity only when relay is enabled by config.

## Related Concepts
- [[concepts/urnetwork-relay-provideenabled-sol-contract]]
- [[concepts/urnetwork-relay-provideenabled-boundary]]
- [[concepts/urnetwork-provide-tun-investigation]]
- [[concepts/urnetwork-walletauth-per-device-registration]]

## Sources
- [[daily/2026-05-29]] records the upstream `SOL` wallet chain token and the local `solana` mismatch.
- [[daily/2026-05-29]] records that `RelayNetworkMonitor` must not remove pause or register callbacks when `provideEnabled=false`.
