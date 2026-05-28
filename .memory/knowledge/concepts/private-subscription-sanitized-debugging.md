---
title: Private subscription sanitized debugging
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---

# Private subscription sanitized debugging

## Key Points
- Private subscription URLs, profile payloads, hostnames, and user-specific values must not be stored in memory articles.
- Reusable memory should describe the failure class, parser path, and sanitized evidence shape.
- `Chain validation failed` on a personal cloud subscription is a durable bug signal, but the raw profile is sensitive input.
- Debugging should preserve enough structural evidence to reproduce parser and config-builder behavior without leaking private data.

## Details

The 2026-05-28 sessions included a user-provided `profile_share.json` and a personal cloud subscription that still failed import with `Chain validation failed`. The durable knowledge is not the private URL or profile contents; it is the repeated failure mode and the need to trace it through subscription parsing, chain validation, unsupported transport handling, DNS config generation, and runtime config checks.

This pattern should be handled by sanitizing values before they enter daily or knowledge articles. Articles may record that a private cloud subscription failed, which validators were involved, and what class of generated config was rejected. They should not persist user-specific endpoints, account identifiers, private subscription links, profile names that identify the user, or any raw payload that could reveal the subscription provider or account.

## Related Concepts
- [[concepts/singbox-private-subscription-chain-validation]]
- [[concepts/singbox-autochain-validator-parity]]
- [[concepts/prod-log-infra-redaction]]
- [[concepts/singbox-chain-dns-hijack-parity]]

## Sources
- [[daily/2026-05-28]] records that the personal cloud subscription continued to fail with `Chain validation failed`.
- [[daily/2026-05-28]] records the decision not to save the private URL or user-specific subscription data in memory, using only a generalized description.
- [[daily/2026-05-28]] records the action item to analyze `ozero_trace.log` and `profile_share.json` after sanitizing private values.
