---
title: Bootstrap signature real trust gate
sources:
  - daily/2026-06-01.md
created: 2026-06-01
updated: 2026-06-01
---
# Bootstrap signature real trust gate

## Summary
Bootstrap server asset sanitization must preserve a real Ed25519 signature trust gate. Tests that only verify sanitized seed shape are insufficient because they do not prove tamper resistance.

## Key Points
- Replacing live bootstrap assets with sanitized JSON can invalidate the existing `.sig` and `update-pubkey.pem` pairing.
- A real JDK Ed25519 verification returning `false` is a trust-gate failure, not a cosmetic test issue.
- Bootstrap tests must include tamper/regression cases, not just sanitized-content checks.
- The private signing key must not be committed while regenerating a valid public key/signature pair for sanitized assets.
- CI green without real signature verification does not prove bootstrap trust.

## Details
The comparison with release `v1.0.13` identified a regression risk: bootstrap signature tests had been weakened to check a sanitized seed instead of proving that the asset snapshot was signed by the expected Ed25519 key. After sanitization, the old signature and public key could no longer match the current JSON. The correct response was not to restore live credentials, but to regenerate the public verification material and signature for the sanitized JSON while keeping private key material out of the repository.

The durable lesson is that public-repo sanitization and cryptographic trust gates are separate contracts. Sanitization removes sensitive data; signature verification proves integrity. A bootstrap asset can be safe to publish but still invalid if its signature pair is stale. Tests must catch that stale pair and any tampering.

This concept extends [[concepts/public-repo-secret-and-insecure-asset-boundary]] and [[concepts/release-regression-self-review-gate]]. It also relates to [[concepts/ci-coverage-gate-artifact-trust-contract]] because release trust depends on executable proof, not only green delivery status.

## Related Concepts
- [[concepts/public-repo-secret-and-insecure-asset-boundary]]
- [[concepts/release-regression-self-review-gate]]
- [[concepts/ci-coverage-gate-artifact-trust-contract]]
- [[connections/fail-closed-security-ci-trust-boundary]]

## Sources
- [[daily/2026-06-01]]: The log records that sanitized `bootstrap-servers.json` could make the old `.sig` and `update-pubkey.pem` mismatch and that JDK Ed25519 verification returned false.
- [[daily/2026-06-01]]: The accepted fix direction was to restore real Ed25519 verification and tamper tests without committing private signing keys or live credentials.
