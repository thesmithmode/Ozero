---
title: sing-box Karing JSON import parity
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---
# sing-box Karing JSON import parity

## Key Points
- sing-box subscription import must accept full Karing/sing-box JSON shapes when they are otherwise valid.
- Unsupported transports and unsupported VLESS flow values must be filtered or normalized before libbox config validation.
- Auto-select and auto-chain must share the same validators so a relay path cannot reintroduce invalid config.
- Private subscription debugging must use sanitized structural evidence, not raw profile URLs or user-specific payloads.

## Details
The 2026-05-28 regression work showed that `Chain validation failed` can survive even after fixing one visible config-builder path. The durable rule is that sing-box import parity is not just a parser concern: parser, validators, auto-select config, auto-chain config, DNS hijack behavior, and UI chain creation all need to agree on the same supported subset before handing config to libbox.

This connects [[concepts/singbox-private-subscription-chain-validation]] with [[concepts/singbox-autochain-validator-parity]]. A private cloud subscription may include Karing-style or sing-box-style JSON, unsupported transports such as `splithttp`, unsupported VLESS flows, or DNS structures that are valid upstream but not valid for the embedded runtime. The project should sanitize those profiles for debugging, then prove the normalized config path with regression tests.

## Related Concepts
- [[concepts/singbox-private-subscription-chain-validation]]
- [[concepts/singbox-autochain-validator-parity]]
- [[concepts/singbox-chain-dns-hijack-parity]]
- [[concepts/private-subscription-sanitized-debugging]]

## Sources
- [[daily/2026-05-28]]: the user reported that a personal cloud subscription still failed with `Chain validation failed`.
- [[daily/2026-05-28]]: auto-chain was found to miss unsupported-transport filtering that standalone auto-select already applied.
- [[daily/2026-05-28]]: the release-sentinel checklist included accepting full Karing/sing-box JSON without storing private subscription data.
