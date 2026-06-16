---
title: MasterDNS port 53 bind preflight contract
sources:
  - daily/2026-05-31.md
created: 2026-05-31
updated: 2026-05-31
---
# MasterDNS port 53 bind preflight contract

## Summary
MasterDNS deploy must prove UDP/TCP port 53 is actually bindable after Docker setup, clean stale containers safely, and avoid reporting an ownerless bind failure as `PORT_FREE`.

## Key Points
- Docker install/run success does not prove MasterDNS can bind port 53.
- Stale containers may need cleanup, but persistent key volume must be preserved.
- Ownerless bind-probe failure is not equivalent to a free port.
- Diagnostics should distinguish bind conflict from missing binary and Docker setup failures.
- This refines [[concepts/masterdns-pinned-release-binary-name-drift]] and [[concepts/masterdns-amnezia-dns-running-udp-contract]].

## Details
The 2026-05-31 MasterDNS work followed earlier deploy hardening and added a sharper port-53 contract. A successful Docker preflight can still fail later if the service cannot bind DNS ports. Therefore the deploy path needs a post-install or post-run bind proof rather than assuming Docker readiness means DNS readiness.

The same work preserved the existing safety rule around cleanup: stale containers may be removed, but key material volume should not be deleted as part of routine recovery. Diagnostics also need to avoid false `PORT_FREE` labels when a bind probe fails but cannot identify an owner.

## Related Concepts
- [[concepts/masterdns-pinned-release-binary-name-drift]]
- [[concepts/masterdns-amnezia-dns-running-udp-contract]]
- [[concepts/masterdns-docker-build-run-proof-contract]]
- [[concepts/masterdns-deploy-hardening]]

## Sources
- [[daily/2026-05-31]]: session 19:02 records a new MasterDNS error from logs/screenshots as a separate workstream.
- [[daily/2026-05-31]]: session 19:17 records strengthened port 53 preflight after Docker install/run gates.
- [[daily/2026-05-31]]: session 19:17 records that stale containers are cleaned while a bind-probe failure without owner is no longer reported as `PORT_FREE`.
