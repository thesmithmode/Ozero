---
title: "New Engine Module: CI Registration Checklist"
aliases: [engine-ci-checklist, new-module-ci, engine-registration]
tags: [ci, android, engines, gotcha]
sources:
  - "daily/2026-05-14 (1).md"
created: 2026-05-27
updated: 2026-05-27
---

# New Engine Module: CI Registration Checklist

Adding a new `engine-*` Gradle module requires explicit registration in two CI artifacts: the binary lock file (`binaries.lock.yaml`) and the test jobs in `ci.yml`. Omitting either causes silent failures — missing binaries cause assembleDebug/test failure at build time, missing CI test job means tests never run without any error.

## Key Points

- New engine modules are NOT auto-discovered by CI; they must be explicitly added to test jobs in `ci.yml`
- If the engine requires native binaries, a lock file entry must be added via `regen_lock.py` before first CI run
- Missing test job registration → tests silently never run (0 tests = no failure reported)
- Missing lock file entry → `assembleDebug` or test run fails with missing .so files
- `regen_lock.py` requires: downloaded `.so` files + `manifest.yaml` in the correct directory to compute file sizes

## Details

### The engine-telegram Incident (2026-05-14)

`engine-telegram` was implemented in a feature branch and squash-merged to `dev`. Two CI failures resulted from missing registrations:

**1. Missing binaries.lock.yaml entry**

`mtg` binary was not in `binaries.lock.yaml`. The CI workflow downloads native binaries based on lock file contents. Without the entry, `assembleDebug` and test compilation would fail at runtime when code tried to locate `libmtg.so`. Fix: run `regen_lock.py` with `mtg-3ad1935b` tag (v2.1.7, 3 ABI: arm64-v8a, armeabi-v7a, x86_64) to add the correct entry with SHA256 hashes and sizes.

**2. Missing CI test job registration**

`engine-telegram` tests were not added to any job in `ci.yml`. The existing job `Tests — engine-urnetwork + engine-byedpi` runs Gradle test tasks for those modules only. Tests for `engine-telegram` simply never ran — no error, no skip, no warning. Fix: explicitly add `engine-telegram` module tasks to the existing combined test job.

### Registration Procedure for New Engines

When creating a new `engine-*` module:

1. **Native binaries** (if applicable):
   - Identify the binary release tag
   - Download `.so` files for all target ABIs (currently: arm64-v8a only for release; arm64-v8a + armeabi-v7a + x86_64 for dev testing)
   - Place alongside `manifest.yaml`
   - Run `regen_lock.py` to compute hashes and update lock file
   - Commit updated `binaries.lock.yaml`

2. **CI test registration**:
   - Add module Gradle task to the appropriate test job in `ci.yml`
   - Verify the module name matches the Gradle module name exactly
   - Push and verify test output shows non-zero test count for the new module

3. **Per-engine UI** (separate requirement):
   - Create settings screen in `ui/settings/engines/` per the per-engine UI contract

### Why Silent Failure Happens

CI test jobs in `ci.yml` use explicit Gradle task lists, not module auto-discovery. This is intentional (allows parallelism and selective testing), but means new modules are invisible until manually added. The absence of test output does not trigger any CI failure — Gradle simply runs the listed tasks and exits 0.

## Related Concepts

- [[concepts/ci-workflow-discipline]] - CI workflow discipline: test jobs use explicit module lists; `--continue` for full failure visibility
- [[concepts/per-engine-ui]] - Per-engine UI contract: settings screen required in `ui/settings/engines/` for every engine
- [[concepts/native-binary-auto-update-pipeline]] - Binary lock file maintenance and auto-update pipeline via CI

## Sources

- [[daily/2026-05-14 (1).md]] - Session 13:56: `engine-telegram` had no `binaries.lock.yaml` entry for mtg → build fail; not in CI test jobs → tests silently never ran; fix: `regen_lock.py` with `mtg-3ad1935b` + add to existing test job
