---
title: sing-box chain DNS hijack parity
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---
# sing-box chain DNS hijack parity

## Summary
sing-box chain mode must use the same DNS pipeline as the main config path. Legacy `dns-out` outbound routing in chain config is a regression risk because modern sing-box expects DNS hijack route actions instead.

## Key Points
- The `v0.2.11` to `v1.0.3` audit found a P2 risk in the chain config DNS path.
- Main sing-box config had already moved away from deprecated DNS outbound behavior.
- Chain mode was aligned to the main path by using `hijack-dns` instead of legacy `dns-out`.
- A regression test was added so chain DNS does not drift from the main config path again.

## Details
During the release-regression audit, sing-box had several separate failure vectors: unsupported VLESS flow normalization, unsupported transports in auto-chain, and a legacy DNS outbound path in chain config. The DNS issue was treated as a risk rather than a confirmed crash, but it was still concrete because the chain path diverged from the already-corrected main config behavior.

The durable rule is that sing-box config paths must share the same DNS invariants. This article complements [[concepts/singbox-dns-outbound-deprecated]] and [[concepts/singbox-autochain-validator-parity]]: validators and DNS routing need to be common at the config builder layer, not scattered through UI-specific paths.

## Related Concepts
- [[concepts/singbox-dns-outbound-deprecated]]
- [[concepts/singbox-autochain-validator-parity]]
- [[concepts/singbox-crash-tombstone-diagnosis]]

## Sources
- [[daily/2026-05-28]]: Audit of `v0.2.11 -> v1.0.3` identified legacy `dns-out` in chain config as a P2 risk.
- [[daily/2026-05-28]]: Decision was to align chain DNS with the main mode through `hijack-dns`.
- [[daily/2026-05-28]]: A regression test was added for chain DNS behavior.
