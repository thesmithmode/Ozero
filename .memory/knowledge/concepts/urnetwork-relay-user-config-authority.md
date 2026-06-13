---
title: URnetwork relay must respect user config authority
sources:
  - daily/2026-05-29.md
created: 2026-05-31
updated: 2026-05-31
---

## Key Points
- `provideEnabled=false` is a user decision and must remain the source of truth for relay activation.
- Network callbacks may pause or resume temporary network state, but must not enable relay against user config.
- URnetwork payout wallet registration uses upstream chain token `SOL`, not `solana`.
- Relay architecture remains separate from URnetwork consumer engine ownership.

## Details
The relay investigation on 2026-05-29 confirmed that relay is a separate subsystem from the URnetwork consumer engine. It may run while another non-URnetwork engine is active, but it must not override explicit user configuration or steal ownership from the engine bridge.

Two concrete integration contracts were extracted. First, `RelayNetworkMonitor` must not register callbacks or clear `providePaused` when `provideEnabled=false`, because that lets a network callback reactivate relay after the user disabled it. Second, payout wallet registration must use the upstream token `SOL`; using `solana` can produce a registration acknowledgement without resolving the registry id needed for endpoint binding.

## Related Concepts
- [[concepts/urnetwork-relay-user-flag-wallet-chain-contract]]
- [[concepts/urnetwork-relay-provideenabled-sol-contract]]
- [[concepts/urnetwork-engine-relay-separation]]
- [[concepts/urnetwork-provide-tun-investigation]]

## Sources
- [[daily/2026-05-29]] records that `provideEnabled=false` must prevent relay reactivation from network callbacks.
- [[daily/2026-05-29]] records that upstream wallet registration uses chain token `SOL`.
- [[daily/2026-05-29]] records that relay must remain separate from the URnetwork consumer engine and use its own relay/provide lifecycle.
