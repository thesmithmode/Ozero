---
title: "Connection: Five Sentinel Trap Types Form a Completeness Family"
connects:
  - "concepts/sentinel-fqn-desync"
  - "concepts/sentinel-protecting-bug-trap"
  - "concepts/sentinel-anchor-substringafter-trap"
sources:
  - "daily/2026-05-16.md"
  - "daily/2026-05-21.md"
  - "daily/2026-05-23.md"
created: 2026-05-16
updated: 2026-05-23
---

# Connection: Five Sentinel Trap Types Form a Completeness Family

## The Connection

Five independently discovered sentinel test traps cover the complete space of sentinel failure modes. Each produces a different symptom via a different mechanism, but together they define what must be verified for a sentinel test to be trustworthy.

## Key Insight

Sentinel tests are meta-tests: they verify source code text patterns rather than runtime behavior. This makes them powerful (catch structural regressions that unit tests miss) but fragile (any change to the source text — naming, formatting, file location — can break or bypass the sentinel without changing behavior). The five trap types enumerate the failure modes of text-pattern verification:

| Trap | Mechanism | Symptom | When |
|------|-----------|---------|------|
| [[concepts/sentinel-fqn-desync]] | Short name vs FQN in source | Sentinel matches wrong string, passes | Import style differs between test and prod |
| [[concepts/sentinel-protecting-bug-trap]] | Sentinel asserts on buggy behavior | Sentinel blocks correct fix, CI red | Sentinel written against workaround, not invariant |
| [[concepts/sentinel-anchor-substringafter-trap]] | `substringAfter` anchor not found | Sentinel searches full file, passes incidentally | Function renamed or moved during refactoring |
| [[concepts/applytunderlying-split-contract]] | Literal assertion vs signature after default-param refactor | Sentinel passes vacuously after refactor converts inline literal to pass-through | Function parameter added as default; inline literal disappears from call-site |
| Comment-token collision | Sentinel comment in production file contains the token being searched | `indexOf()` finds the comment before the actual code call; ordering assertion fails | Sentinel one-liner comment added near the code it guards |

A robust sentinel must guard against all five: (1) use the same naming convention as production code, (2) assert the correct/desired behavior not the current behavior, (3) validate that the anchor string exists before using it for substring extraction, (4) do not assert on inline literals that disappear under default-param refactoring, (5) sentinel comments in production files must NOT contain the exact token the test searches for.

## Evidence

All five traps were discovered independently across different sessions:
- FQN desync: v0.0.11 (2026-05-11) — `excludeSelf` sentinel used short name, production had FQN
- Protecting bug: v0.0.13 (2026-05-13) — `ipProbeRoute` sentinel guarded null-guard workaround
- Anchor validation: v0.0.16 (2026-05-16) — 5 sentinels lacked anchor assertions, exposed during VpnService decomposition
- Literal vs signature: v0.1.10 (2026-05-21) — `OzeroVpnServiceLockdownKillswitchTest` asserting `"applyUnderlying = false"` literal; refactor converted inline literal to `applyUnderlying: Boolean = false` default param + `applyUnderlying = applyUnderlying` pass-through; literal vanished from call-site; sentinel passed vacuously
- Comment-token collision: v0.2.0 (2026-05-23, commit b1c6cba0) — `OzeroVpnService.kt:253` had a sentinel one-liner comment that literally contained `serviceScope.launch`; `OzeroVpnServiceLifecycleTest.indexOf("serviceScope.launch")` found the comment at line 253 before the actual call at line 258 → `launchIdx < preloadIdx` → assertion fail. Fix: remove the token from the comment text.

The temporal spread (12 days across 5 incidents) confirms these are fundamental failure modes, not project-specific incidents. Any project using source-pattern sentinels will encounter all five.

## Related Concepts

- [[concepts/sentinel-fqn-desync]] - Trap 1: naming mismatch → vacuous pass
- [[concepts/sentinel-protecting-bug-trap]] - Trap 2: guards wrong behavior → blocks correct fix
- [[concepts/sentinel-anchor-substringafter-trap]] - Trap 3: anchor miss → searches full file
- [[concepts/applytunderlying-split-contract]] - Trap 4: literal assertion breaks when default-param refactor replaces inline literal
- [[connections/ci-false-green-vectors]] - Sentinel traps 1, 3, 4, 5 are CI false-green vectors; trap 2 is CI false-red; trap 5 is CI false-red (ordering fails)
