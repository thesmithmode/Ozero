---
title: "URnetwork relay provideEnabled and SOL contract"
sources:
  - "daily/2026-05-29.md"
created: 2026-05-29
updated: 2026-06-13
---
# URnetwork relay provideEnabled and SOL contract
## Key Points
- URnetwork relay is separate from the URnetwork client engine and must not be diagnosed as the same module path.
- `provideEnabled=false` is a user-owned off switch; `RelayNetworkMonitor` must not re-enable relay by clearing pause from a network callback.
- `addExternalWallet` must use chain token `"SOL"`, matching upstream `WalletViewModel`, not `"solana"`.
- The relay fix was committed as `54faf668 FIX: Исправление реле URnetwork`.
## Details
The relay investigation found two independent contract breaks. First, network callbacks could reactivate provide mode even when the user had disabled relay. This violates feature ownership: a network monitor can manage temporary network pause, but it cannot override `provideEnabled=false`.

Second, payout wallet registration used the wrong chain token. Upstream calls `addExternalWallet(..., "SOL")`; using `"solana"` can produce an acknowledgment-like path without resolving the registry id and binding the endpoint. The fix therefore uses the upstream token and preserves the relay architecture.

Relay remains architecturally distinct from the URnetwork client engine. It may start when another non-URnetwork engine is active, uses the dummy IoLoop/offline TUN pattern described in [[concepts/urnetwork-provide-tun-investigation]], and must not stop the shared bridge if the main engine owns it.

The 2026-05-29 relay pass followed the readiness fix but stayed in a separate layer. It corrected payout binding and user-owned enablement without changing the URnetwork consumer engine's startup semantics.
## Related Concepts
- [[concepts/urnetwork-provide-tun-investigation]] - Details the dummy IoLoop/offline TUN pattern for provide mode.
- [[concepts/urnetwork-provide-secret-keys-identity]] - Covers identity and listener ordering around provide keys.
- [[concepts/urnetwork-startup-readiness-runtime-peer-grace]] - Separates client-engine readiness from relay behavior.
- [[concepts/relay-coordinator-ownership-transfer]] - Prior relay ownership rule and provide pause gotcha.
- [[connections/multi-engine-lifecycle-exitnode-regression-loop]] - Places relay after URnetwork client readiness in the same staged regression repair loop.
## Sources
- [[daily/2026-05-29]]: records that URnetwork engine and relay must not be mixed during diagnosis.
- [[daily/2026-05-29]]: records the upstream `"SOL"` chain token comparison and the `"solana"` bug.
- [[daily/2026-05-29]]: records the `provideEnabled=false` network callback bug and commit `54faf668`.
- [[daily/2026-05-29]]: records that relay analysis was performed after client-engine readiness and was not mixed into that fix.
