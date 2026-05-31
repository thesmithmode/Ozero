---
title: URnetwork explicit bestAvailable location
sources:
  - daily/2026-05-31.md
created: 2026-05-31
updated: 2026-05-31
---
# URnetwork explicit bestAvailable location

## Summary
URnetwork default location must be represented as an explicit SDK `bestAvailable` location rather than an implicit `null`, so settings UI and engine startup share the same state.

## Key Points
- `null` default location can leave SDK `connectLocation` or `defaultLocation` empty until a manual UI selection.
- "Лучший доступный" should be stored as an explicit SDK best-available token.
- Settings UI should use fast fallback from store/cache when SDK selected location is not immediately available.
- `bestAvailable` is not a concrete country and must not be exposed as country evidence.
- This builds on [[concepts/urnetwork-location-token-best-available]] and [[concepts/urnetwork-runtime-grace-startup-gate]].

## Details
The 2026-05-31 URnetwork investigation found a mismatch between settings UI and engine startup: the UI could show no selected country, and connection could fail until the user manually selected "Лучший доступный". The recorded root cause was an implicit `null` default instead of an explicit SDK best-available location.

The stable contract is that default location state must be materialized into the SDK and persisted configuration. The UI may use cached/store fallback for immediate display, but exit-node metadata must not treat best-available as a specific country because it represents a selection mode rather than a proven geographic endpoint.

## Related Concepts
- [[concepts/urnetwork-location-token-best-available]]
- [[concepts/urnetwork-runtime-grace-startup-gate]]
- [[concepts/exit-node-providerlabel-known-ip-contract]]
- [[concepts/urnetwork-engine-relay-separation]]

## Sources
- [[daily/2026-05-31]]: session 19:02 records that URnetwork did not show the selected country and did not connect until manual best-available selection.
- [[daily/2026-05-31]]: session 19:17 records the decision to replace implicit `null` with explicit SDK `bestAvailable` location.
- [[daily/2026-05-31]]: session 19:29 records that `bestAvailable` must not be treated as a concrete country in exit-node strategy.
