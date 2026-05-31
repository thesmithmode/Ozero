---
title: Runtime security and CI proof loop
sources:
  - daily/2026-05-31.md
created: 2026-05-31
updated: 2026-05-31
---
# Runtime security and CI proof loop

## Key Points
- Fail-closed runtime behavior, public asset hygiene, and CI coverage fidelity form one release-risk loop.
- A green CI run is not enough if coverage gates read stale execution data or miss modules.
- Runtime leak fixes need both scenario tests and trustworthy CI inclusion.
- Intentional backup static-key behavior is outside this fix loop unless the product backup contract changes.

## Details

The 2026-05-31 review linked three concerns that can look separate: WARP/engine fail-closed behavior, public asset hygiene, and false-green CI coverage. If runtime failure paths can bypass the watchdog, if IPv6 can leak outside the tunnel, or if bundled assets ship live proxy credentials, then release readiness cannot be proven by build success alone.

The CI side completes the loop. Even when tests exist, stale JaCoCo paths or omitted modules can make coverage gates untrustworthy. Therefore runtime security fixes need explicit scenario coverage, N>0 confirmation, correct coverage execution data, and module inclusion. Otherwise CI can be green while the exact security-critical path remains untested.

## Related Concepts
- [[concepts/engine-runtime-failclosed-watchdog-path]]
- [[concepts/warp-ipv6-fail-closed-routing]]
- [[concepts/android-jacoco-executiondata-false-green]]
- [[concepts/backup-one-click-restore-contract]]

## Sources
- [[daily/2026-05-31]]: Session 20:48 ties fail-closed routing, watchdog path, live credentials, and CI coverage fidelity into one review priority list; later clarification marks backup static key as intentional.
- [[daily/2026-05-31]]: Session 20:48 records that N>0 tests do not prove all needed modules are covered by the CI gate.
- [[daily/2026-05-31]]: Session 20:48 records that Android JaCoCo may be false-green due to stale execution data paths.
