---
title: URnetwork engine and relay separation
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---
# URnetwork engine and relay separation

## Summary
URnetwork consumer engine readiness and URnetwork relay/provide behavior are separate systems and must be diagnosed and fixed independently.

## Key Points
- URnetwork engine startup is about `attachTun`, connect issuance, readiness, peers, and runtime watchdog behavior.
- URnetwork relay is a separate provide/share path that can run with non-URnetwork active engines.
- Relay must respect the user-controlled `provideEnabled` flag and must not be re-enabled by network callbacks.
- Relay wallet registration uses the upstream chain token `SOL`; consumer-engine fixes must not be mixed with payout or relay changes.

## Details
The 2026-05-29 work separated a consumer-mode URnetwork startup bug from relay behavior. The engine-side symptom was `CONNECTING peers=0` with a long startup wait, which was traced to readiness/lifecycle placement. The fix direction was short startup readiness and runtime peer grace after `onEngineStarted()`.

Relay had different contracts. It is allowed to operate independently from the URnetwork engine and must preserve user intent. A network monitor can pause or resume relay for connectivity, but it cannot override `provideEnabled=false`. Similarly, wallet chain registration belongs to the relay/payout API contract and uses `SOL`; that concern should not be blended into engine client-mode diagnosis.

## Related Concepts
- [[concepts/urnetwork-startup-readiness-runtime-peer-grace]]
- [[concepts/urnetwork-relay-provideenabled-sol-contract]]
- [[concepts/relay-coordinator-ownership-transfer]]
- [[concepts/urnetwork-provide-tun-investigation]]

## Sources
- [[daily/2026-05-29]]: user clarified that URnetwork and URnetwork relay are different modules and must not be mixed.
- [[daily/2026-05-29]]: URnetwork readiness was fixed by moving the 5-minute `peers=0` grace out of startup and into runtime watchdog.
- [[daily/2026-05-29]]: relay analysis confirmed `SOL` as upstream wallet chain token and `provideEnabled` as the source of truth for user-controlled relay state.
