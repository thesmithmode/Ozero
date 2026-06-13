---
title: MasterDNS amnezia-dns removal success contract
sources:
  - daily/2026-05-30.md
created: 2026-05-30
updated: 2026-05-30
---
# MasterDNS amnezia-dns removal success contract

## Summary
MasterDNS must report `amnezia-dns` removal success only after Docker stop/remove actually succeeds, and must avoid blocking valid deploys on TCP/53 when only UDP/53 is published.

## Key Points
- `docker stop` and `docker rm` failures must not be ignored in the user-confirmed cleanup flow.
- The script must not print `AMNEZIA_DNS_REMOVED` unless the container was actually stopped and removed.
- `amnezia-dns` conflict detection should target UDP/53, because the MasterDNS container publishes UDP/53.
- TCP/53 and UDP/53 can coexist; treating TCP/53 as a blocker creates false positives.

## Details
MasterDNS conflict handling on 2026-05-30 had two related contracts. First, the preflight must detect only a running `amnezia-dns` container that conflicts with the UDP/53 binding used by MasterDNS. This extends [[concepts/masterdns-amnezia-dns-running-udp-contract]] by making the UDP-only part explicit: TCP/53 is not enough to block the deploy path.

Second, the user-confirmed cleanup path must be truthful. If Docker cleanup fails, the UI must not receive a success marker. This keeps deploy state consistent with [[concepts/masterdns-deploy-hardening]] and prevents a false continuation after a failed container removal.

The flow also interacts with pending deploy state. `PortBusy` should clear pending deploy when the deployment cannot continue, while `AmneziaDnsConflict` preserves the pending deploy because the user may confirm cleanup and continue. Tests should encode both paths so future changes do not silently restore unsafe auto-remove behavior.

## Related Concepts
- [[concepts/masterdns-amnezia-dns-running-udp-contract]]
- [[concepts/masterdns-deploy-hardening]]
- [[concepts/masterdns-fake-ssh-specificity]]
- [[concepts/ci-grouped-job-failure-attribution]]

## Sources
- [[daily/2026-05-30]]: Review findings accepted that Docker stop/remove failures must not be ignored and `AMNEZIA_DNS_REMOVED` must mean real cleanup success.
- [[daily/2026-05-30]]: CI and review analysis fixed `checkAmneziaDns53` to consider UDP/53 rather than TCP/53.
