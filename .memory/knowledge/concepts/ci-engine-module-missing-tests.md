---
title: "New Engine Module Not Added to CI Test Job: Silent Skip"
aliases: [ci-missing-engine-tests, engine-module-ci-gap, ci-test-job-registration]
tags: [ci, android, architecture, gotcha]
sources:
  - "daily/2026-05-14.md"
created: 2026-05-14
updated: 2026-05-14
---

# New Engine Module Not Added to CI Test Job: Silent Skip

When a new `engine-*` Gradle module is added to the project, its tests do NOT automatically run in CI. CI workflows define test jobs with explicit module lists (e.g., `./gradlew :engine-urnetwork:test :engine-byedpi:test`). An omitted module means all its tests are silently skipped — no error, no warning, just zero test output. The fix: always add the new module to the relevant `Tests —` job in `ci.yml` immediately when creating the module.

## Key Points

- CI test jobs in Ozero list modules explicitly; new modules are not auto-discovered
- Silently skipped tests look like "no tests to run" or produce no output at all — not a CI failure
- Rule: creating a new `engine-*` module → immediately add it to the CI test job in the same commit
- `binaries.lock.yaml` must also be updated with the new module's native binaries; missing entries cause `assembleDebug` failures
- `regen_lock.py` requires downloaded `.so` files adjacent to `manifest.yaml` to compute binary sizes

## Details

### The Mechanism

Ozero's `ci.yml` contains test jobs like:

```yaml
Tests — engine-urnetwork + engine-byedpi:
  runs-on: ubuntu-latest
  steps:
    - run: ./gradlew :engine-urnetwork:test :engine-byedpi:test --continue
```

When `engine-telegram` was added, its tests were not added to any CI job. The module compiled (compilation is caught by the build job), but `TelegramProxyCoordinatorTest`, `DataStoreTelegramConfigStoreTest`, and `MtgWrapperArgsTest` never executed until after squash-merge to `dev`.

### The feat/mtg Incident (2026-05-14)

`engine-telegram` was developed on `feat/mtg`. During development, tests were run locally. After squash-merge to `dev`, CI ran the full test suite for the first time — and discovered that:

1. `TelegramProxyCoordinatorTest` × 6 → `UncompletedCoroutinesError` (testScope passed to coordinator)
2. `UpdateInstallResultReceiverTest` × 9 → NPE from Hilt + Robolectric eager init
3. `MtgWrapperArgsTest` → assertion always failed (shell mock missing `$0`)

All three failures were pre-existing bugs in the test code that had never been caught because CI never ran them. Multiple CI fix cycles were required after the merge.

### The Lock File Dependency

`binaries.lock.yaml` is the manifest of native binaries downloaded from GitHub Releases during CI. For `engine-telegram`, the `mtg` binary (`libmtg.so`) was not in the lock file when initially merged. This would have caused `assembleDebug` and test compilation to fail at native library resolution.

The fix required running `regen_lock.py` with the tag `mtg-3ad1935b` (v2.1.7) and the downloaded `.so` files present locally to compute SHA256 and file sizes. Then committing the updated lock file.

### Checklist for New Engine Module

When creating a new `engine-*` module:
1. Add `./gradlew :engine-<name>:test` to the CI test job in `ci.yml`
2. Update `binaries.lock.yaml` with all native binaries via `regen_lock.py`
3. Add engine to the per-engine UI requirement (`ui/settings/engines/`) per [[concepts/per-engine-ui]]
4. Register engine in `@IntoSet` Hilt module or equivalent DI binding

## Related Concepts

- [[concepts/ci-workflow-discipline]] - CI discipline: `--continue` to see all failures; add module to CI immediately; tag only after CI green
- [[concepts/per-engine-ui]] - Each engine requires a settings screen; same "don't forget to register" checklist
- [[concepts/native-binary-auto-update-pipeline]] - Lock file pattern for native binaries; regen_lock.py is part of the engine creation workflow
- [[concepts/runtest-uncompleted-coroutines-trap]] - The bugs discovered when CI finally ran engine-telegram tests were testScope coroutine traps

## Sources

- [[daily/2026-05-14.md]] — Session 13:56: `engine-telegram` not in CI test job; discovered after squash-merge; added to existing job `Tests — engine-urnetwork + engine-byedpi`; `binaries.lock.yaml` updated with mtg-3ad1935b tag via `regen_lock.py`
