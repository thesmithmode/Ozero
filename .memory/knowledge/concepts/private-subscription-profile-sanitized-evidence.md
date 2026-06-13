---
title: Private subscription profiles need sanitized evidence
sources:
  - [[daily/2026-05-28]]
created: 2026-05-31
updated: 2026-05-31
---

# Private subscription profiles need sanitized evidence

## Summary

Real subscription import failures should be debugged from sanitized structural evidence, because private subscription URLs and user-specific profile payloads are sensitive even when they are essential to reproducing `Chain validation failed`.

## Key Points

- The May 28 trace included a personal cloud subscription and `profile_share.json` related to a recurring `Chain validation failed`.
- The subscription data was treated as sensitive and should not be copied into memory articles.
- Debugging should preserve structural facts, such as unsupported transports, chain validation stage, or parser path, without storing private URLs or payload values.
- This extends [[concepts/private-subscription-sanitized-debugging]] and [[concepts/singbox-private-subscription-chain-validation]].

## Details

The user provided real profile material while reporting that import of a personal cloud subscription still failed with `Chain validation failed`. That evidence was important because synthetic subscriptions might not reproduce the failure, but it also created a privacy boundary: wiki memory should record only generalized facts and the diagnostic shape, not raw URLs, user-specific hosts, or full profile contents.

For future work, acceptable evidence includes the failing stage, parser/config-builder component, unsupported feature class, and whether the profile was full Karing/sing-box JSON. Unsafe evidence includes the actual private subscription URL, unique server identifiers, credentials, or personal payload contents.

## Related Concepts

- [[concepts/private-subscription-sanitized-debugging]]
- [[concepts/singbox-private-subscription-chain-validation]]
- [[concepts/singbox-karing-json-import-parity]]
- [[concepts/prod-log-infra-redaction]]

## Sources

- [[daily/2026-05-28]]: Sessions 20:31, 20:37, and 20:39 recorded the personal cloud subscription failure and the instruction to avoid storing private subscription details.
- [[daily/2026-05-28]]: The same sessions tied the failure to `Chain validation failed` and sing-box subscription/import investigation.
