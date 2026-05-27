---
title: "Feature Deletion Leaves Orphaned Consumers"
aliases: [orphaned-consumers, feature-deletion-cleanup, deletion-consumer-audit]
tags: [android, refactoring, kotlin, ci, detekt]
sources:
  - "daily/2026-05-25.md"
created: 2026-05-25
updated: 2026-05-25
---

# Feature Deletion Leaves Orphaned Consumers

Deleting a large feature (e.g., removing a parser class or endpoint prober) leaves orphaned references scattered across ViewModels, DI modules, and test fakes. These don't cause compile errors if the deleted class was injected as a constructor parameter — the ViewModel still compiles (the parameter just goes unused). The failure surfaces later in CI as detekt `UnusedPrivateMember` or ktlint unused import errors.

## Key Points

- Deleting a class used in constructor injection leaves unused `import` and `field` in every consuming ViewModel
- Unused constructor param + unused import = detekt/ktlint CI failure, not compile failure
- The DI module (`@Provides` / `@Binds`) may still correctly provide the deleted class to other real consumers — do NOT blindly remove DI bindings
- After any feature deletion, grep all consumer layers: ViewModel files, test fakes, DI modules, and `FakeXxx` implementations
- Fix: remove field from ViewModel constructor + remove matching test fake injection + verify DI binding still needed by other real consumers

## Details

### The Incident

After removing the `ClashYamlParser` feature (commit `e5a5db11`), `WarpEngineSettingsViewModel` retained:
- `import ru.ozero.engine.warp.WarpIniBuilder` — unused after deletion
- `import ru.ozero.engine.warp.WarpEndpointProber` — unused in VM (prober lives in `EngineWarp`)
- `val endpointProber: WarpEndpointProber` — constructor param now unused
- Missing `import kotlinx.coroutines.flow.update` — was accidentally removed in the same cleanup

CI failed on `detekt` (`UnusedPrivateMember`) and `ktlint` (unused import).

### The Non-Obvious Trap

The DI module `WarpModule.kt` still had `@Provides fun provideWarpEndpointProber()`. This was NOT orphaned — `EngineWarp` still consumes it. Removing the DI binding would have broken `EngineWarp`. The correct fix was to remove the prober only from the ViewModel constructor, leaving the DI binding intact.

### Systematic Deletion Checklist

When deleting a feature class `FooClass`:

```bash
# Find all consumers
grep -r "FooClass\|FakeFoo\|import.*FooClass" --include="*.kt" .
```

Check each hit:
1. ViewModel: remove constructor param + import
2. Test fake: remove `FakeFooClass` if only used for the ViewModel
3. DI module: verify if any other real consumer still needs it; only remove if zero consumers

## Related Concepts

- [[concepts/cyclomatic-complexity-extract-helper]] - When refactoring triggers similar multi-layer consumer updates
- [[concepts/extension-function-import-migration-trap]] - Similar: migration leaves stale imports
- [[concepts/ci-workflow-discipline]] - detekt/ktlint CI gates catch orphaned references

## Sources

- [[daily/2026-05-25.md]] — `ClashYamlParser` deletion left `WarpEndpointProber` as orphaned import+field in `WarpEngineSettingsViewModel`; DI binding kept (EngineWarp still needs it); CI failed detekt+ktlint; fix: remove field from VM constructor + remove unused imports; lesson: after feature deletion, grep all consumer layers in one pass before push
