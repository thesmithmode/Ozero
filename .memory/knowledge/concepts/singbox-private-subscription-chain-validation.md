---
title: sing-box private subscription chain validation
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---

# sing-box private subscription chain validation

## Key Points
- `Chain validation failed` on a real subscription must be debugged with sanitized profile data.
- Private subscription URLs, user-specific profile values, and hostnames must not be persisted in memory.
- Chain validation failures should be traced through parser output, auto-chain validators, DNS pipeline, and unsupported transport filtering.
- If `checkConfig` passes but runtime still crashes, the next evidence is a native tombstone or debug log, not another config guess.

## Details

The 2026-05-28 sessions recorded a recurring sing-box problem: a personal cloud subscription still failed import with `Chain validation failed`, and sing-box/auto mode could not reliably reach the network. The user attached `ozero_trace.log` and `profile_share.json`, but the durable memory rule is to store only sanitized descriptions such as "personal cloud subscription".

The likely investigation surface links several existing invariants. [[concepts/singbox-autochain-validator-parity]] covers parity between auto-select and auto-chain validation. [[concepts/singbox-chain-dns-hijack-parity]] covers DNS pipeline parity. [[concepts/singbox-splithttp-unsupported]] and [[concepts/singbox-dns-outbound-deprecated]] explain common config-generation failures from subscriptions. Privacy handling belongs with [[concepts/prod-log-infra-redaction]].

## Related Concepts
- [[concepts/singbox-autochain-validator-parity]]
- [[concepts/singbox-chain-dns-hijack-parity]]
- [[concepts/singbox-splithttp-unsupported]]
- [[concepts/prod-log-infra-redaction]]

## Sources
- [[daily/2026-05-28]]: пользователь сообщил, что импорт личной cloud subscription падает с `Chain validation failed`.
- [[daily/2026-05-28]]: принято решение не сохранять приватный URL подписки и user-specific данные в память.
- [[daily/2026-05-28]]: action items потребовали разобрать `ozero_trace.log` и `profile_share.json` с предварительной санитизацией приватных значений.
