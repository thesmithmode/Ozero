---
title: "Sentinel Protecting Bug Trap: When Tests Guard Wrong Behavior"
aliases: [sentinel-guards-bug, sentinel-obsolete-behavior, sentinel-blocks-correct-fix]
tags: [testing, gotcha, sentinel, ci]
sources:
  - "daily/2026-05-13.md"
created: 2026-05-13
updated: 2026-05-14
---

# Sentinel Protecting Bug Trap: When Tests Guard Wrong Behavior

Sentinel tests are designed to protect invariants by failing when production code changes. But if the sentinel was written against buggy behavior (a workaround, a null-guard that shouldn't exist, a wrong return type), the sentinel becomes a gate that blocks the correct fix. CI goes red not because the fix is wrong, but because the sentinel enshrines the bug as an invariant.

## Key Points

- Sentinel tests lock in whatever behavior existed when written — if that behavior was a bug or workaround, the sentinel protects the bug
- Symptom: you fix the root cause correctly, CI goes red on a sentinel test, and the "fix" is to revert the correct change
- The trap is most dangerous when the sentinel was added during a rush and nobody verified the asserted behavior was actually correct
- Rule: when fixing a function, immediately grep all sentinels that test it and verify each asserts the CORRECT contract, not the historical one
- Distinct from [[concepts/sentinel-fqn-desync]] (sentinel passes vacuously due to naming mismatch) — here the sentinel actively fails on correct code

## Details

### The EngineUrnetworkContractTest Incident (2026-05-13)

`EngineUrnetworkPlugin.ipProbeRoute()` had a null-guard: when `selectedLocation()` returned `country=null, name=null` but `countryCode="US"`, the function returned `IpProbeRoute.Unavailable` instead of `IpProbeRoute.StaticLocation(null, "US")`. A sentinel test `ipProbeRoute возвращает Unavailable когда country и name пустые` was written to guard this behavior.

The null-guard was incorrect — `StaticLocation(null, "US")` is the valid contract when countryCode exists. `IpInfoCard` handles null country via `?: stringResource(R.string.unknown)`. When the null-guard was removed as part of a correct fix, CI went red because the sentinel expected `Unavailable`.

The sentinel was renamed and rewritten to assert `StaticLocation(null, countryCode)` — the correct behavior. Two CI runs were wasted discovering and fixing the stale sentinel.

### Why This Happens

1. **Rush-written sentinels**: during rapid bug fixing, sentinel is added to "lock" behavior without verifying it's the right behavior
2. **Sentinel outlives its context**: the workaround it guards was always temporary, but the test stays
3. **No review of sentinel correctness**: code review checks that sentinels exist, not that they assert correct behavior
4. **Compound with sequential fixes**: session 15:06 had 4 sequential FIX commits where each violated existing rules — rushed sentinel additions in such sessions are high-risk

### Prevention

1. When fixing a function, `grep -r "functionName\|ClassName" test/` and verify every sentinel asserts the CORRECT contract
2. Sentinel PR review question: "is this the behavior we WANT, or the behavior we currently HAVE?"
3. If sentinel was added alongside a workaround, add a comment: `// guards workaround, update when root cause fixed`
4. After removing a workaround, search for sentinels that referenced it

## Related Concepts

- [[concepts/sentinel-fqn-desync]] — sentinel passes vacuously due to short vs FQN naming mismatch (opposite failure mode: silent pass vs active block)
- [[concepts/ip-probe-route-architecture]] — the IpProbeRoute contract where this incident occurred; StaticLocation null country section documents the correct behavior
- [[concepts/test-tautology-always-green]] — another test correctness trap: tautology assertions that always pass regardless of implementation
- [[connections/ci-false-green-vectors]] — sentinel-protecting-bug is the inverse: CI false RED instead of false green

## Sources

- [[daily/2026-05-13.md]] — Session 20:53: sentinel `ipProbeRoute возвращает Unavailable когда country и name пустые` blocked correct fix; Session 21:13: sentinel renamed/rewritten, confirmed pattern "старый тест охранял баг, не инвариант"
