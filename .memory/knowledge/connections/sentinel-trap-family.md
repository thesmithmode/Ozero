---
title: "Connection: Three Sentinel Trap Types Form a Completeness Family"
connects:
  - "concepts/sentinel-fqn-desync"
  - "concepts/sentinel-protecting-bug-trap"
  - "concepts/sentinel-anchor-substringafter-trap"
sources:
  - "daily/2026-05-16.md"
created: 2026-05-16
updated: 2026-05-16
---

# Connection: Three Sentinel Trap Types Form a Completeness Family

## The Connection

Three independently discovered sentinel test traps cover the complete space of sentinel failure modes. Each produces a different symptom (false green, false red, false green) via a different mechanism (naming mismatch, behavior mismatch, anchor miss), but together they define what must be verified for a sentinel test to be trustworthy.

## Key Insight

Sentinel tests are meta-tests: they verify source code text patterns rather than runtime behavior. This makes them powerful (catch structural regressions that unit tests miss) but fragile (any change to the source text — naming, formatting, file location — can break or bypass the sentinel without changing behavior). The three trap types enumerate the failure modes of text-pattern verification:

| Trap | Mechanism | Symptom | When |
|------|-----------|---------|------|
| [[concepts/sentinel-fqn-desync]] | Short name vs FQN in source | Sentinel matches wrong string, passes | Import style differs between test and prod |
| [[concepts/sentinel-protecting-bug-trap]] | Sentinel asserts on buggy behavior | Sentinel blocks correct fix, CI red | Sentinel written against workaround, not invariant |
| [[concepts/sentinel-anchor-substringafter-trap]] | `substringAfter` anchor not found | Sentinel searches full file, passes incidentally | Function renamed or moved during refactoring |

A robust sentinel must guard against all three: (1) use the same naming convention as production code, (2) assert the correct/desired behavior not the current behavior, (3) validate that the anchor string exists before using it for substring extraction.

## Evidence

All three traps were discovered independently across different sessions:
- FQN desync: v0.0.11 (2026-05-11) — `excludeSelf` sentinel used short name, production had FQN
- Protecting bug: v0.0.13 (2026-05-13) — `ipProbeRoute` sentinel guarded null-guard workaround
- Anchor validation: v0.0.16 (2026-05-16) — 5 sentinels lacked anchor assertions, exposed during VpnService decomposition

The temporal separation (5 days apart) suggests these are fundamental failure modes, not project-specific incidents. Any project using source-pattern sentinels will encounter all three.

## Related Concepts

- [[concepts/sentinel-fqn-desync]] - Trap 1: naming mismatch → vacuous pass
- [[concepts/sentinel-protecting-bug-trap]] - Trap 2: guards wrong behavior → blocks correct fix
- [[concepts/sentinel-anchor-substringafter-trap]] - Trap 3: anchor miss → searches full file
- [[connections/ci-false-green-vectors]] - Sentinel traps 1 and 3 are CI false-green vectors; trap 2 is CI false-red
