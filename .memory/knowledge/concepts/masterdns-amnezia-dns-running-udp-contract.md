---
title: MasterDNS amnezia-dns running UDP conflict contract
sources:
  - daily/2026-05-30.md
created: 2026-05-30
updated: 2026-05-30
---
# MasterDNS amnezia-dns running UDP conflict contract
## Summary
MasterDNS conflict detection for `amnezia-dns` must only block on a running container that owns UDP port 53. Stopped containers and TCP-only matches must not trigger the user conflict dialog or auto-removal flow.
## Key Points
- Check `docker ps` before `docker inspect` so stopped `amnezia-dns` containers do not look like active conflicts.
- Treat `53/udp` as the relevant blocker for DNS; `53/tcp` is not enough to report the `amnezia-dns` conflict.
- Keep the behavior user-confirmed: do not restore older auto-remove behavior for `amnezia-dns`.
- Emit conflict markers as a single static shell string such as `AMNEZIA_DNS_CONFLICT|proto=...` so contract tests can detect the behavior.
## Details
The 2026-05-30 merge sequence established that PR #73 added a safer user-confirmed `amnezia-dns` conflict flow, while PR #74 still contained older auto-remove behavior. The accepted resolution preserved the safe flow and kept only the useful structured diagnostics from PR #74, including typed `PortBusy(protocol,address,owner)` handling.

PR #75 then reduced to a contract assertion: `amnezia-dns` must be discovered among running containers through `docker ps` before any `inspect` details are trusted. A later review comment narrowed the port check further: `checkAmneziaDns53` should only consider UDP 53, because the MasterDNS conflict is about DNS service availability, not any incidental TCP binding.

The CI loop also showed that shell formatting matters for this contract. Splitting `AMNEZIA_DNS_CONFLICT|proto=` across adjacent quoted shell fragments kept runtime behavior close but made the marker invisible to static tests. The fix was to print the marker as one double-quoted shell string with runtime variables.
## Related Concepts
- [[concepts/masterdns-startup-hardening]]
- [[concepts/masterdns-deploy-hardening]]
- [[concepts/overlapping-pr-merge-preserve-dev-contracts]]
- [[concepts/ci-grouped-job-failure-attribution]]
## Sources
- [[daily/2026-05-30]]: PR #73 introduced the safe user-confirmed `amnezia-dns` flow, and PR #74 was resolved without restoring auto-remove behavior.
- [[daily/2026-05-30]]: PR #75 was reduced to checking only running containers via `docker ps` before `inspect`.
- [[daily/2026-05-30]]: The review comment for PR #78 required `MasterDnsDockerScripts.checkAmneziaDns53` to consider UDP 53 only.
- [[daily/2026-05-30]]: The remaining MasterDNS CI failure was fixed by emitting `AMNEZIA_DNS_CONFLICT|proto=` as one static marker string.
