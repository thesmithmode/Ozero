---
title: "Sentinel Protecting Bug Trap: When Tests Guard Wrong Behavior"
aliases: [sentinel-guards-bug, sentinel-obsolete-behavior, sentinel-blocks-correct-fix]
tags: [testing, gotcha, sentinel, ci]
sources:
  - "daily/2026-05-13.md"
  - "daily/2026-05-15.md"
  - "daily/2026-05-22.md"
created: 2026-05-13
updated: 2026-05-22
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

### The excludeSelf Sentinel Incident (2026-05-15)

Commit `5a8089dd` changed `excludeSelf` from unconditional `true` to `excludeSelf = (engineId != EngineId.WARP)`. Two sentinel tests were written to protect this new conditional behavior. When the regression was discovered (all engines broken because WARP without self-exclusion killed traffic), the fix required reverting to unconditional `true` — but the sentinels blocked the revert because they asserted the broken conditional.

Both sentinels had to be deleted and replaced with a new sentinel that forbids `EngineId.WARP` from appearing in `common-vpn` source at all — enforcing the modular boundary rather than a specific conditional value. This confirms the pattern: sentinels written during the same session as a bug can enshrine the bug as an invariant, particularly when the developer believes the change is an improvement rather than a regression.

### The Switching Desync Sentinel Incident (2026-05-22)

`TunnelController` incorrectly cleared `switching` state on any `Connected(X)` event, regardless of whether `switching.to` matched `X`. A sentinel `switchingDoesNotClearOnConnectedOfDifferentEngine` was written with `assertNull(tunnelController.switching.value)` after emitting `Connected(WARP)` when `switching.to = URNETWORK`. The assertion was wrong — switching should survive when the connected engine doesn't match the pending target. When the bug was fixed (clear only if `sw.to == null || sw.to == X`), the sentinel went red. The sentinel was rewritten to assert that `switching` is NOT cleared when `Connected(engineA)` arrives while `switching.to = engineB`. See [[concepts/engine-chip-race-observer]] for the full context.

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
- [[daily/2026-05-15.md]] — Session 12:27: two sentinels guarding `excludeSelf=(engineId != WARP)` blocked revert to correct unconditional `true`; both deleted, replaced with modular boundary sentinel
- [[daily/2026-05-22.md]] — Session 11:59: `switchingDoesNotClearOnConnectedOfDifferentEngine` asserted `assertNull` on switching after Connected(WARP) while switching.to=URNETWORK; fixed bug made sentinel red; sentinel rewritten to assert switching survives wrong-engine Connected events
