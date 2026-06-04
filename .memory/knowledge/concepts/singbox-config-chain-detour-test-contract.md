---
title: Singbox Config Chain Detour Test Contract
sources:
  - [[daily/2026-06-04]]
created: 2026-06-04
updated: 2026-06-04
---
# Singbox Config Chain Detour Test Contract

## Key Points
- `singbox-config` tests must match the actual chain routing contract.
- `buildChainConfig(unknown)` can still include an inbound detour to `proxy`; tests should not assert detour absence when routing owns that behavior.
- `splithttp` remains unsupported under the current contract and should not be tested as a supported config path.
- Test fixes should update stale expectations rather than changing production builders that already match routing design.

## Details

On 2026-06-04, `singbox-config` failures included a stale assertion expecting no `detour` in a chain config for an unknown case. The current chain routing behavior adds inbound detour to `proxy`, so the test was out of date. The same CI cycle also exposed a contradictory test around `splithttp`: it treated the transport as unsupported while also trying to build it as supported.

The lesson is that config tests need to distinguish unsupported transport filtering from valid chain routing output. When the product contract says a transport is unsupported, config-builder tests should not try to exercise it as a supported outbound. When routing intentionally adds detours, assertions should reflect that ownership instead of forcing the old output shape.

## Related Concepts
- [[concepts/singbox-splithttp-unsupported-test-contract]]
- [[concepts/singbox-autochain-validator-parity]]
- [[concepts/singbox-chain-dns-hijack-parity]]
- [[concepts/ci-current-run-batch-failure-triage]]

## Sources
- [[daily/2026-06-04]]: sessions 20:39 and 20:50 record the `splithttp` unsupported/supported contradiction and the stale `buildChainConfig(unknown)` detour assertion.
