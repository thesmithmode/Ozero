---
title: "Sentinel FQN Desync: Short vs Fully-Qualified Class Names"
aliases: [fqn-sentinel-mismatch, sentinel-class-name-desync, short-fqn-test-trap]
tags: [testing, gotcha, sentinel, android]
sources:
  - "daily/2026-05-11.md"
created: 2026-05-11
updated: 2026-05-11
---

# Sentinel FQN Desync: Short vs Fully-Qualified Class Names

Sentinel tests that assert on class name strings can desync from production code when one uses short names (`EngineId.WARP`) and the other uses fully-qualified names (`ru.ozero.enginescore.EngineId.WARP`). The sentinel pattern-matches against source text — if the naming style differs, the sentinel may pass even when the production code's behavior has changed or broken. The rule: sentinel tests must use the same naming convention as the production code they guard.

## Key Points

- Sentinel tested for `EngineId.WARP` (short FQN); production code used `ru.ozero.enginescore.EngineId.WARP` (full FQN) → sentinel matched nothing, passed vacuously
- The desync allowed a regression to ship: `excludeSelf` for WARP was broken but sentinel didn't catch it because the string pattern didn't match
- Rule: in source-pattern sentinel tests, use the exact string that appears in production code — including import style, FQN, or alias
- Alternative: add an import-check sentinel that verifies the import statement exists, making short-name usage safe
- `MainScreenChartTest` had a related fragility: 4-space prefix matching on formatted output broke on whitespace changes — removed

## Details

### The Desync Mechanism

Source-pattern sentinel tests work by reading a production source file as a string and asserting that specific patterns exist. For example, a sentinel for `excludeSelf` might assert:

```kotlin
// SENTINEL: assert excludeSelf is true for WARP
val source = File("OzeroVpnService.kt").readText()
assertTrue(source.contains("EngineId.WARP"))
assertTrue(source.contains("excludeSelf = true"))
```

If the production code uses the full path:

```kotlin
// Production code
if (engineId != ru.ozero.enginescore.EngineId.WARP) {
    excludeSelf = true
}
```

The sentinel's `source.contains("EngineId.WARP")` matches (it's a substring of the FQN), but the semantic meaning differs. If someone changes `ru.ozero.enginescore.EngineId.WARP` to `ru.ozero.enginescore.EngineId.BYEDPI`, the sentinel for `EngineId.WARP` might still match if the short string appears elsewhere in the file (in imports, comments, or other logic). The sentinel becomes a false-green gate.

### The v0.0.11 Incident

In the v0.0.11 stabilization cycle, a sentinel test for `excludeSelf` was checking for `EngineId.WARP` (short form). The production code in `OzeroVpnService` used the full FQN `ru.ozero.enginescore.EngineId.WARP` because no import statement existed for `EngineId` in that file. The sentinel passed but the actual conditional logic was incorrect — `excludeSelf` was not being applied correctly for WARP engines.

The fix required aligning both: the sentinel now checks for the exact FQN string used in production, and the production code's import/FQN usage is verified by the sentinel.

### Prevention Rules

1. **Same style**: Sentinel string must match exactly what appears in production source (including package prefix)
2. **Import guard**: If using short names in sentinel, add a secondary assertion that the relevant import exists in the file
3. **Avoid fragile prefix matching**: The `MainScreenChartTest` used 4-space indentation prefix matching on formatted chart output — any whitespace change broke it. Removed in favor of content-based assertions.

## Related Concepts

- [[concepts/tun-self-exclusion-sdk-engines]] - The `excludeSelf` invariant that this sentinel was supposed to protect
- [[concepts/junit-platform-silent-skip]] - Another test infrastructure trap where tests pass vacuously without verifying correctness
- [[connections/ci-false-green-vectors]] - Sentinel FQN desync is another vector for CI reporting false green

## Sources

- [[daily/2026-05-11.md]] - Session 12:45: sentinel checked `EngineId.WARP` (short FQN), production code used `ru.ozero.enginescore.EngineId.WARP` (full FQN) → sentinel desynchronized; `MainScreenChartTest` 4-space prefix fragility removed
