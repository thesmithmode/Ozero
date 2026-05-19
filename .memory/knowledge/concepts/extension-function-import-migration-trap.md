---
title: "Extension Function Import Migration Trap"
aliases: [interface-to-extension, extension-import-grep, top-level-extension-migration]
tags: [kotlin, gotcha, refactoring, compile-error]
sources:
  - "daily/2026-05-18.md"
created: 2026-05-18
updated: 2026-05-18
---

# Extension Function Import Migration Trap

When refactoring interface members into top-level extension functions (e.g., moving `UrnetworkConfigStore.writeOrRemove` from an interface method to a standalone extension), all consumer files lose their implicit access and require explicit imports. The Kotlin compiler reports `Unresolved reference` at each call site. Unlike renaming (which IDE refactoring tools handle), this kind of migration is invisible to `Rename` refactoring and requires a manual grep across the entire codebase.

## Key Points

- Interface member functions are resolved through the interface type — no import needed at call sites
- Top-level extension functions require explicit `import pkg.writeOrRemove` at every call site
- Compiler error is `Unresolved reference: writeOrRemove` — clear but appears in every consumer file simultaneously
- In Ozero: 3 files broke (`UrnetworkRelayCoordinator`, `MainViewModel`, `UrnetworkEngineSettingsViewModel`) when `writeOrRemove` moved from interface to top-level extension
- Compounding factor: `EngineId` enum expansion (6 new stub values) made `when(engineId)` non-exhaustive in `probingLabelRes` — two unrelated changes in the same push produced overlapping compile errors

## Details

### The Migration Pattern

Detekt's `TooManyFunctions` rule flags interfaces with many methods. A common fix is extracting utility methods as top-level extension functions, reducing the interface's method count. For example:

```kotlin
// BEFORE: interface method (implicit resolution)
interface UrnetworkConfigStore {
    suspend fun writeOrRemove(key: Preferences.Key<String>, value: String?)
    // ... 19 other methods
}

// AFTER: top-level extension (requires import)
suspend fun UrnetworkConfigStore.writeOrRemove(key: Preferences.Key<String>, value: String?) { ... }
```

Before the migration, any file that has a `UrnetworkConfigStore` reference can call `store.writeOrRemove(...)` without importing anything — the method is part of the interface contract. After migration, `writeOrRemove` is a standalone function in a different file, and every call site needs `import ru.ozero.engineurnetwork.config.writeOrRemove`.

### The Ozero Incident (2026-05-18)

Commit `832dc2c4` moved `writeOrRemove` from `UrnetworkConfigStore` interface to a top-level extension to bring the interface below detekt's 20-function threshold. Three consumer files were not updated with the new import:

1. `UrnetworkRelayCoordinator.kt` — called `configStore.writeOrRemove(...)` in relay start/stop
2. `MainViewModel.kt` — called it in toggle handlers for Fixed IP / Enhanced Anonymization
3. `UrnetworkEngineSettingsViewModel.kt` — called it in settings save paths

CI failed with 3 parallel `Unresolved reference` errors. The fix (commit `2dc2f4d1`) added the missing imports.

### Compounding with Enum Expansion

The same push also expanded `EngineId` enum with 6 new stub values (`XRAY`, `AMNEZIA`, `HYSTERIA2`, `NAIVE`, `TOR`, `FPTN` — all `isStub=true`). The `when(engineId)` expression in `MainScreen.probingLabelRes` became non-exhaustive, producing a second compile error alongside the import errors. Two unrelated changes — extension migration and enum expansion — produced overlapping errors in the same CI run, making diagnosis slower.

### Prevention Pattern

Before pushing any interface-to-extension migration:

```bash
# Find all files importing the interface
grep -rl "UrnetworkConfigStore" --include="*.kt" app/ engine-urnetwork/

# For each moved method, check call sites
grep -rn "writeOrRemove\|otherMovedMethod" --include="*.kt" app/ engine-urnetwork/
```

Add the extension import to every file that calls the moved method. Verify locally with `./gradlew compileDebugKotlin` before pushing.

### When to Use Extension Migration

Extension migration is appropriate when:
- The interface has too many methods (detekt TooManyFunctions)
- The method is a utility/convenience, not a core contract
- The method has a single implementation (not polymorphic behavior)

It is NOT appropriate when:
- The method is part of the interface's semantic contract (all implementations must provide it)
- The method is overridden differently in test fakes vs real implementations
- The method is called from dozens of files (migration cost exceeds benefit)

## Related Concepts

- [[concepts/suppress-annotation-decomposition]] - Same motivation (detekt compliance via decomposition not suppression); extension migration is a specific decomposition technique
- [[concepts/sentinel-refactor-batch-audit]] - Same class of problem: code move breaks multiple files; both require pre-push grep audit
- [[concepts/ci-job-dependency-masking]] - Compile errors from extension migration can be masked by unrelated CI job failures

## Sources

- [[daily/2026-05-18.md]] - Session after 17:51: `UrnetworkConfigStore` methods became top-level extensions; 3 files missing imports; compounded with `EngineId` enum expansion making `when` non-exhaustive; fix commit 2dc2f4d1
