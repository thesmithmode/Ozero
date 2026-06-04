---
title: sing-box splithttp unsupported tests must not build it as supported
sources:
  - daily/2026-06-04.md
created: 2026-06-04
updated: 2026-06-04
---
# sing-box splithttp unsupported tests must not build it as supported

## Key Points
- If the product contract marks `splithttp` as unsupported, tests must not later assert successful config construction for it.
- A contradictory test can look like a product regression while the bug is in the test expectation.
- Unsupported transport behavior should be verified at parser/filter boundaries and not reintroduced in builder happy paths.
- This contract protects the earlier crash fix around unsupported sing-box transports.

## Details

The 2026-06-04 CI triage found a `singbox-config` test that both expected `splithttp` to be unsupported and tried to build a config for it as if it were supported. The correct fix was to align the test with the unsupported-transport contract rather than changing production builder behavior.

This reinforces [[concepts/singbox-splithttp-unsupported]] and [[concepts/singbox-autochain-validator-parity]]. Unsupported transports should be filtered or rejected consistently before they reach config paths that assume support. Tests that contradict that boundary can create false product failures and waste CI cycles.

## Related Concepts
- [[concepts/singbox-splithttp-unsupported]]
- [[concepts/singbox-autochain-validator-parity]]
- [[concepts/singbox-subscription-nested-transport-mapping]]

## Sources
- [[daily/2026-06-04.md]] recorded that a `singbox-config` test incorrectly treated `splithttp` as both unsupported and supported.
- [[daily/2026-06-04.md]] recorded the decision to update the stale test contract rather than production builder behavior.
