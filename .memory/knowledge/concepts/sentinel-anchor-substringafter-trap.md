---
title: "Sentinel Anchor substringAfter Trap: Silent Pass on Renamed Functions"
aliases: [sentinel-anchor-trap, substringafter-sentinel, sentinel-anchor-validation]
tags: [testing, gotcha, sentinel, ci]
sources:
  - "daily/2026-05-16.md"
created: 2026-05-16
updated: 2026-05-16
---

# Sentinel Anchor substringAfter Trap: Silent Pass on Renamed Functions

Sentinel tests that use `substringAfter("functionName")` to locate a code region in production source fail silently when the function is renamed or moved to another file. `substringAfter` returns the entire source string when the delimiter is not found — the sentinel then searches the full file for its target pattern, which may match incidentally elsewhere, producing a false green. The fix: always assert that the anchor string exists in the source before using `substringAfter`.

## Key Points

- `substringAfter("funcName")` returns the full string unchanged when `funcName` is not found — no exception, no warning
- Sentinel test passes because the pattern it seeks might exist somewhere in the full source (not in the intended function)
- Particularly dangerous during refactoring: function renamed/moved → anchor misses → sentinel passes vacuously → regression undetected
- Fix: add `assertTrue(source.contains("funcName"), "anchor funcName not found in source")` before `substringAfter`
- In Ozero: 5 sentinel files lacked anchor assertions; added during session 13:37 task #76

## Details

### The Mechanism

A typical sentinel test structure:

```kotlin
@Test fun `startVpn calls loadOnce before serviceScope launch`() {
    val source = File("OzeroVpnService.kt").readText()
    val startVpnBlock = source.substringAfter("fun startVpn(")
                              .substringBefore("fun stopVpn(")
    assertTrue(startVpnBlock.contains("loadOnce()"))
    val loadOnceIdx = startVpnBlock.indexOf("loadOnce()")
    val launchIdx = startVpnBlock.indexOf("serviceScope.launch")
    assertTrue(loadOnceIdx < launchIdx, "loadOnce must precede serviceScope.launch")
}
```

When `startVpn` is extracted to `StartSequenceCoordinator`, `source.substringAfter("fun startVpn(")` doesn't find the delimiter in the new file. It returns the entire `OzeroVpnService.kt` content. The test then searches the full file for `loadOnce()` and `serviceScope.launch` — which may exist in other methods — and the ordering check may pass accidentally.

### The RealUrnetworkSdkBridgeContractTest Incident

A concrete example from session 11:45: the test searched for `"override suspend fun stop():"` with a trailing colon. The actual code had `override suspend fun stop()` without a colon. `substringAfter` returned the entire source file. The test's subsequent `indexOf` calls found the target patterns at indices from unrelated parts of the file, producing inverted index ordering that happened to pass the assertion.

### The Fix Pattern

```kotlin
@Test fun `startVpn calls loadOnce before serviceScope launch`() {
    val source = File("StartSequenceCoordinator.kt").readText()
    
    // ANCHOR VALIDATION — fails fast if function was renamed/moved
    val anchor = "fun runStartSequence("
    assertTrue(source.contains(anchor), "anchor '$anchor' not found — function renamed?")
    
    val block = source.substringAfter(anchor)
                      .substringBefore("}")  // or next function
    assertTrue(block.contains("loadOnce()"))
}
```

The anchor assertion fails immediately if the function is not in the expected file, forcing the developer to update the sentinel rather than letting it pass vacuously.

### Relationship to Other Sentinel Traps

This is the third sentinel trap type in Ozero's knowledge base, each with a distinct failure mode:

| Trap | Failure Mode | Result |
|------|-------------|--------|
| [[concepts/sentinel-fqn-desync]] | Short name vs FQN mismatch | Sentinel matches wrong location, passes |
| [[concepts/sentinel-protecting-bug-trap]] | Sentinel guards buggy behavior | Sentinel blocks correct fix, CI red |
| This article | Anchor string not found after rename | substringAfter returns full source, sentinel passes |

All three share a common theme: sentinel tests that verify source text patterns are fragile to code changes that don't affect behavior. The anchor validation fix addresses this specific variant.

## Related Concepts

- [[concepts/sentinel-fqn-desync]] - Complementary sentinel trap: short vs FQN naming mismatch causes vacuous pass
- [[concepts/sentinel-protecting-bug-trap]] - Complementary sentinel trap: sentinel guards wrong behavior, blocks correct fix
- [[concepts/vpnservice-god-object-decomposition]] - The refactoring that exposed this trap: functions moved between files during decomposition

## Sources

- [[daily/2026-05-16.md]] - Session 13:37 task #76: 5 sentinel files lacked anchor assertions, added `assertTrue(source.contains(anchor))` check; Session 11:45: `substringAfter("override suspend fun stop():")` with trailing colon didn't match source without colon → returned full source
