---
title: Sing-box subscription transport tests must follow parser contract
sources:
  - daily/2026-06-04.md
created: 2026-06-05
updated: 2026-06-05
---

## Summary
`singbox-subscription` tests must not assert unsupported transport behavior in one branch while constructing configs as if the transport were supported in another branch.

## Key Points
- Unsupported transports (for example `splithttp`) should be consistently treated as unsupported across parser + config tests.
- Nested transport mapping failures often appear as compile-time breakage when expected parser properties do not exist.
- A `ClashYamlParser` contract regression in nested mapping can surface as invalid `type` handling before runtime behavior is reached.
- Test code should represent the same product contract as production parser behavior (`detour`, `type`, fallback branches).
- When parser contracts evolve, tests are updated first and production config builders are changed only after failing tests clarify real behavior change.

## Details
The daily sessions repeatedly highlighted a recurring class of CI breakage in `singbox-subscription`: references to fields or transport branches that were not part of the current contract, such as `pluginOpts` and conflicting `splithttp` handling. This created compile/test instability despite no obvious business-logic bug in startup or connection paths.

The practical correction was to align test scenarios with the active contract: unsupported transports stay unsupported in assertions, and expected successful branches test supported mappings only. This reduced false failures from invalid branch combinations and moved coverage work to actual runtime/formatting helpers.

## Related Concepts
- [[concepts/singbox-splithttp-unsupported-test-contract]]
- [[concepts/singbox-subscription-nested-transport-mapping]]
- [[concepts/singbox-config-chain-detour-test-contract]]
- [[connections/ci-current-run-batch-failure-triage]]

## Sources
- [[daily/2026-06-04.md]] session notes around 16:36, 17:53, 20:39 describe plugin field mismatch and remaining singbox transport contract failures.
- Earlier sessions log the `splithttp` contract conflict and subsequent test contract cleanup.
