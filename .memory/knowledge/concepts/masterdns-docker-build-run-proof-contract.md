---
title: MasterDNS Docker build and run proof contract
sources:
  - daily/2026-05-30.md
created: 2026-05-31
updated: 2026-05-31
---
# MasterDNS Docker build and run proof contract

## Key Points
- MasterDNS Docker build success must prove that `/usr/local/bin/masterdnsvpn-server` exists and is executable.
- The upstream installer must not be hidden behind a broad `|| true` path that can report `BUILD_OK` without a runnable binary.
- `docker run` failures need structured `ERR_RUN` evidence with phase, exit/state, error text, stderr, and log tail.
- Stale containers may be removed during recovery, but the `masterdns-key` volume must be preserved.
- This contract complements [[concepts/masterdns-amnezia-dns-removal-success-contract]] and [[concepts/masterdns-deploy-hardening]].

## Details

The 2026-05-30 runtime diagnosis found that MasterDNS could finish image build, generate an encrypted key, open firewall rules, and still fail at container start with `run_failed`. The later subagent root cause was that the Dockerfile could suppress upstream installer failure with `|| true`, report `BUILD_OK`, and produce an image that lacked the expected `masterdnsvpn-server` entrypoint.

The durable rule is that build success is not a log marker alone. The build path must verify the binary path and executable bit before returning success, and must report `ERR_BUILD|reason=bin_missing` or equivalent structured failure when the artifact is absent. Runtime failure must preserve enough evidence to distinguish port conflict, missing binary, container state, Docker daemon failure, and application crash without reading sensitive key material.

This also narrows cleanup behavior. Removing stale containers is valid recovery, but deleting the persistent key volume would destroy deploy state and can turn a runnable repair into data loss. That separation belongs with the existing MasterDNS deploy hardening and amnezia-dns cleanup contracts.

## Related Concepts
- [[concepts/masterdns-deploy-hardening]]
- [[concepts/masterdns-amnezia-dns-removal-success-contract]]
- [[concepts/masterdns-amnezia-dns-running-udp-contract]]
- [[concepts/code-quality-review-proof-standard]]

## Sources
- [[daily/2026-05-30]]: MasterDNS reached Docker image build and container launch, then failed at `docker run` with `run_failed`.
- [[daily/2026-05-30]]: A subagent found that the Dockerfile suppressed installer failure and could emit `BUILD_OK` without `/usr/local/bin/masterdnsvpn-server`.
- [[daily/2026-05-30]]: The accepted fix contract required binary existence/executable checks, structured `ERR_BUILD`/`ERR_RUN` evidence, stale-container cleanup, and preserving the `masterdns-key` volume.
