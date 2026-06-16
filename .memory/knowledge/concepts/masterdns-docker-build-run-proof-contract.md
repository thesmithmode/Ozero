---
title: MasterDNS Docker build and run proof contract
sources:
  - daily/2026-05-30.md
  - daily/2026-05-31.md
created: 2026-05-31
updated: 2026-06-09
---
# MasterDNS Docker build and run proof contract
## Summary
MasterDNS deploy success must prove the server binary exists, preserve structured Docker run evidence, and check the deploy-relevant UDP/53 conflict path.

## Key Points
- MasterDNS Docker build success must prove that `/usr/local/bin/masterdnsvpn-server` exists and is executable.
- The upstream installer must not be hidden behind a broad `|| true` path that can report `BUILD_OK` without a runnable binary.
- `docker run` failures need structured `ERR_RUN` evidence, while stale-container cleanup must preserve the `masterdns-key` volume.
- Port preflight must treat UDP/53 as the deploy-critical conflict because the container publishes `53/udp`.
- Upstream binary name drift should be handled through a pinned release asset or structured fallback, with `bin_missing` reported as a build artifact failure.

## Details

The 2026-05-30 runtime diagnosis found that MasterDNS could finish image build, generate an encrypted key, open firewall rules, and still fail at container start with `run_failed`. The later subagent root cause was that the Dockerfile could suppress upstream installer failure with `|| true`, report `BUILD_OK`, and produce an image that lacked the expected `masterdnsvpn-server` entrypoint.

The durable rule is that build success is not a log marker alone. The build path must verify the binary path and executable bit before returning success, and must report `ERR_BUILD|reason=bin_missing` or equivalent structured failure when the artifact is absent. Runtime failure must preserve enough evidence to distinguish port conflict, missing binary, container state, Docker daemon failure, and application crash without reading sensitive key material.

This also narrows cleanup behavior. Removing stale containers is valid recovery, but deleting the persistent key volume would destroy deploy state and can turn a runnable repair into data loss. That separation belongs with the existing MasterDNS deploy hardening and amnezia-dns cleanup contracts.

The PR review loop added a narrower port-conflict rule. TCP/53 and UDP/53 can coexist, while the MasterDNS container path publishes UDP/53. Therefore `checkAmneziaDns53` and related preflight diagnostics should not treat TCP/53 as equivalent to UDP/53 when deciding whether an `amnezia-dns` conflict blocks deploy.

The 2026-05-31 integration added another concrete failure mode: the Dockerfile expected the old `masterdnsvpn-server` name while upstream produced a `MasterDnsVPN_Server_Linux*_v*` binary. Deploy code should treat that as upstream binary name drift, prefer a pinned known release artifact, and keep fallback installer behavior diagnostic rather than silently successful.

## Related Concepts
- [[concepts/masterdns-deploy-hardening]]
- [[concepts/masterdns-amnezia-dns-removal-success-contract]]
- [[concepts/masterdns-amnezia-dns-running-udp-contract]]
- [[concepts/masterdns-port53-bind-preflight-contract]]
- [[concepts/code-quality-review-proof-standard]]

## Sources
- [[daily/2026-05-30]]: MasterDNS reached Docker image build and container launch, then failed at `docker run` with `run_failed`.
- [[daily/2026-05-30]]: A subagent found that the Dockerfile suppressed installer failure and could emit `BUILD_OK` without `/usr/local/bin/masterdnsvpn-server`.
- [[daily/2026-05-30]]: The accepted fix contract required binary existence/executable checks, structured `ERR_BUILD`/`ERR_RUN` evidence, stale-container cleanup, and preserving the `masterdns-key` volume.
- [[daily/2026-05-30]]: PR review confirmed the Docker port preflight should conflict only on UDP/53 because TCP/53 can coexist and the container publishes `53/udp`.
- [[daily/2026-05-31]]: Sessions 11:39, 12:13, and 12:27 record `bin_missing`, pinned upstream release handling, safe cleanup, key read retry, and localized deploy diagnostics.
