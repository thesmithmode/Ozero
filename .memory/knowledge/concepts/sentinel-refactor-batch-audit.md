---
title: "Sentinel Batch Audit Before Push After Code Extraction"
aliases: [sentinel-batch-audit, refactor-sentinel-cascade, extract-refactor-sentinel]
tags: [testing, sentinel, refactoring, process, gotcha]
sources:
  - "daily/2026-05-17.md"
created: 2026-05-17
updated: 2026-05-17
---

# Sentinel Batch Audit Before Push After Code Extraction

Extract-refactor (moving methods from one class to another) breaks ALL sentinel tests that read production source files by path and use `substringAfter("functionName")` anchors. Each sentinel that referenced the old file now either passes vacuously (anchor not found, searches full file — see [[concepts/sentinel-anchor-substringafter-trap]]) or fails with wrong assertions. The fix is a batch audit: before pushing any extract-refactor, grep ALL sentinel tests for moved function names and update them in one pass.

## Key Points

- OzeroVpnService → coordinator extraction (5 classes) caused cascading CI failures across multiple sentinel test files
- Root cause: each sentinel test hardcodes a source file path + function anchor; extraction moves both
- Symptom-fixing loop: fix one sentinel → push → next sentinel fails → fix → push → repeat for N sentinels
- Correct process: `grep -r "OzeroVpnService\|startVpn\|stopVpn\|performShutdown" test/` → update ALL matches in one commit BEFORE push
- CI went green on commit b4815e26 only after batch dedup fix — multiple individual fixes prior were wasted CI cycles

## Details

### The Cascade Mechanism

Ozero uses source-pattern sentinel tests extensively to guard structural invariants (e.g., "loadOnce() must precede serviceScope.launch in startVpn"). These sentinels read `.kt` source files from disk and assert text patterns exist in specific functions. When code extraction moves `startVpn()` from `OzeroVpnService.kt` to `StartSequenceCoordinator.kt`:

1. `OzeroVpnServiceLifecycleTest` still reads `OzeroVpnService.kt`
2. `substringAfter("fun startVpn(")` doesn't find the anchor
3. Returns full file content (see [[concepts/sentinel-anchor-substringafter-trap]])
4. Pattern may match elsewhere incidentally → false green, or fail → CI red
5. Developer fixes THIS sentinel, pushes
6. `PeerWatchdogTest` has a different anchor in the same file → also broken
7. Repeat for each sentinel file

The user explicitly called this out as "фиксить симптомы, а не корень" — the root cause is pushing without auditing all sentinels, not any individual sentinel being wrong.

### The Batch Audit Pattern

Before pushing any extract-refactor:

```bash
# Find all sentinel tests referencing the refactored file
grep -rl "OzeroVpnService\\.kt" app/src/test/ --include="*.kt"

# For each moved function, find sentinels with that anchor
grep -rn "startVpn\|stopVpn\|performShutdown\|handleEngineFailure" app/src/test/ --include="*.kt"

# Update ALL matches in one commit
```

This produces a single "FIX: обновить sentinel-якоря после extract-refactor" commit that updates all broken sentinels at once. One CI run validates all fixes. No cascade.

### The v0.0.16 Incident (2026-05-17)

The OzeroVpnService decomposition (1010 → ~270 lines, 5 extracted coordinators from [[concepts/vpnservice-god-object-decomposition]]) was pushed without batch sentinel audit. The result:

- CI run 1: sentinel A fails (lifecycle test anchor)
- Fix → push → CI run 2: sentinel B fails (peer watchdog anchor)
- Fix → push → CI run 3: sentinel dedup issue
- Fix (b4815e26) → CI run 4: green

Four CI cycles where one would have sufficed. Each cycle costs ~10 minutes of CI time plus context switching. The user's frustration was justified: this is a process bug, not a code bug.

### Prevention Rule

Added to development checklist: "After ANY file-level code move (extract class, move function, rename file), grep all test files for the old file name and all moved function names. Update ALL matches before push."

This rule complements [[concepts/sentinel-anchor-substringafter-trap]] (which adds anchor validation inside each sentinel) with a process-level prevention (audit all sentinels as a batch before CI ever sees them).

## Related Concepts

- [[concepts/sentinel-anchor-substringafter-trap]] - Individual sentinel hardening via anchor assertion; this article is about batch process
- [[concepts/vpnservice-god-object-decomposition]] - The refactoring that triggered the cascade
- [[connections/sentinel-trap-family]] - Fourth sentinel failure mode: mass breakage from file-level moves (distinct from the three per-sentinel traps)
- [[concepts/ci-workflow-discipline]] - Wasted CI cycles from incremental sentinel fixes

## Sources

- [[daily/2026-05-17.md]] - Session 10:59: sentinel cascade after OzeroVpnService decomposition; 4 CI cycles wasted; root cause = no batch audit before push; CI green on b4815e26
