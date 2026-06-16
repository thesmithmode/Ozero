---
title: Common-vpn split-start and shutdown branch coverage
sources:
  - daily/2026-06-04.md
created: 2026-06-04
updated: 2026-06-04
---
# Common-vpn split-start and shutdown branch coverage
## Key Points
- `common-vpn` showed a coverage deficit rather than a product crash in the 2026-06-04 log.
- The missing branches were concentrated in `StartSequenceCoordinator`, `ShutdownCoordinator`, `EngineWatchdogCoordinator`, and `NativeHevTunnelGateway`.
- The split-mode startup branches needed explicit `ALLOWLIST` and `BLOCKLIST` coverage.
- Shutdown behavior also needed a no-active-session path and a stop-before-start native gateway path.
## Details
The log shows a deliberate move toward runtime coverage, not synthetic sentinel coverage, for `common-vpn`. The interesting part is that the missing lines were not in a single feature path; they were spread across startup orchestration, shutdown bookkeeping, and native gateway behavior.

This concept is closely linked to [[coverage-gate-vs-test-harness-validity-loop]] because a coverage hole can look like a product bug until the actual branch map is inspected. It also connects to [[runtime-engine-fix-ci-proof-loop]], where the owning layer must be tested directly instead of letting a higher-level harness hide the missing branch.
## Related Concepts
- [[coverage-gate-vs-test-harness-validity-loop]]
- [[runtime-engine-fix-ci-proof-loop]]
- [[buildsrc-lockfileparser-date-branch-coverage]]
## Sources
- [[daily/2026-06-04.md]]
