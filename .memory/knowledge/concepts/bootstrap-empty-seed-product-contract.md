---
title: Empty bootstrap seed is a product contract question
sources:
  - daily/2026-06-02.md
created: 2026-06-02
updated: 2026-06-02
---

# Empty bootstrap seed is a product contract question

## Summary

An empty bundled `bootstrap-servers.json` can be correct public-repo hygiene or a product regression depending on whether Ozero promises first-run bundled bootstrap servers.

## Key Points

- Removing live bundled servers can be a security and privacy cleanup for a public repository.
- The same change can regress first-run behavior if no safe replacement bootstrap path exists.
- `FirstRunBootstrap` being a no-op in both `v1.0.13` and `dev` means the no-op itself is not a new `dev` regression.
- The actionable question is whether the product requires a non-empty safe seed or intentionally starts empty.

## Details

The 2026-06-02 audit separated the code behavior from the product contract. `FirstRunBootstrap` was already a no-op in the `v1.0.13` baseline, so the no-op was not introduced by current `dev`. The distinct change was that bundled bootstrap server data had been cleaned to an empty seed.

For a public repository, removing live server coordinates can be the correct security posture. However, if users expect first-run connectivity through bundled bootstrap entries, an empty asset becomes a product regression unless another remote or user-driven bootstrap mechanism exists. Future decisions should document which contract is intended before treating the empty seed as either bug or fix.

## Related Concepts

- [[concepts/bootstrap-signature-real-trust-gate]]
- [[concepts/public-repo-secret-and-insecure-asset-boundary]]
- [[concepts/release-last-good-baseline-audit]]
- [[connections/fail-closed-security-ci-trust-boundary]]

## Sources

- [[daily/2026-06-02]]: audit found `bootstrap-servers.json` cleared to an empty seed on `dev`.
- [[daily/2026-06-02]]: decision recorded that this is a risk only if bundled bootstrap servers are an expected capability.
- [[daily/2026-06-02]]: lesson recorded that `FirstRunBootstrap` was already no-op in `v1.0.13`, so the asset contract is the relevant issue.
