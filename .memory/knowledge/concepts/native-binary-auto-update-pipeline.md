---
title: "Native Binary Auto-Update Pipeline via GitHub Actions"
aliases: [binaries-auto-update, native-binary-ci-commit, auto-merge-binaries]
tags: [ci, github-actions, native, architecture, automation]
sources:
  - "daily/2026-05-13.md"
created: 2026-05-13
updated: 2026-05-13
---

# Native Binary Auto-Update Pipeline via GitHub Actions

Ozero's native engines (ByeDPI, AmneziaWG, Hysteria2) are compiled by `binaries.yml` workflows. Rather than requiring manual commits of new SO/AAR artifacts, an auto-update pipeline (`native-auto-apply.yml`) is triggered on successful `binaries.yml` completion: it commits the new artifacts to a branch, runs CI, and squash-merges into `dev` automatically for patch/minor bumps. Major version bumps create a GitHub Issue instead.

## Key Points

- `binaries.yml` job `update-repo`: after build, commits artifacts to `bot/update-<engine>-<sha8>` branch
- `native-auto-apply.yml` triggers on `workflow_run` completion of `binaries.yml`: CI green → squash into `dev`; red → open Issue; major version → open Issue without merge
- Coverage ≥95% + contract tests are gates before automerge
- Limitation: auto-update cannot catch runtime SIGSEGV on vendor ROMs or handshake timeouts — only compile-time + contract-level regressions
- Contract tests that guard auto-update: `ByeDpiJniContractTest` (3 external fun + loadLibrary), AmneziaWgRuntimeBinaryTest (SHA256)

## Details

### Motivation

Native binaries (libbyedpi.so, libam-go.so, etc.) are rebuilt periodically from upstream sources. Previously, committing the new artifacts required a manual developer step: download artifact from CI, copy to jniLibs/, commit, push. This created drift: upstream bug fixes accumulated unmerged, and the commit was often delayed until a feature branch prompted the update.

The auto-update pipeline closes this gap. When `binaries.yml` produces a new artifact, the workflow commits it immediately and attempts to integrate it through the same CI gates as any other change.

### Risk Classification by Version Bump

The pipeline distinguishes bump magnitude:

| Bump type | Action |
|-----------|--------|
| Patch (x.y.Z) | Auto-merge to dev if CI green |
| Minor (x.Y.z) | Auto-merge to dev if CI green |
| Major (X.y.z) | Open Issue, no auto-merge |

Major bumps (e.g., libam-go v2.x → v3.x) may break JNI contracts, change API surface, or require Kotlin-side changes. These require human review before integration.

### What Tests Cannot Catch

Even with 95% coverage, auto-merge is inherently limited:

- **Runtime SIGSEGV on vendor ROMs** (Nubia, RedMagic): reproducible only on physical devices; CI uses emulators or runs unit tests only
- **Handshake timeouts** under TSPU filtering: network conditions not reproducible in CI
- **Go runtime GC conflicts**: manifest only after extended operation or during rapid engine switching

For these failure classes, the auto-merge is a "best-effort" gate. A post-deploy smoke test on physical device is still required before v* release tagging.

### Contract Tests as Gate

`ByeDpiJniContractTest` verifies:
1. `external fun jniStartProxy(...)` signature exists
2. `external fun jniStopProxy()` signature exists
3. `external fun jniGetProxyPort(): Int` signature exists
4. `System.loadLibrary("byedpi")` succeeds in test environment

If upstream ByeDPI adds or renames a JNI function, this test catches the contract drift before the artifact reaches `dev`.

## Related Concepts

- [[concepts/amneziawg-so-binary-integrity]] - SHA256 sentinel that guards against wrong binary being auto-merged
- [[concepts/ci-workflow-discipline]] - CI on dev is the gate for all merges including auto-merged binary updates
- [[concepts/byedpi-args-parsing]] - ByeDPI binary changes may affect arg parsing contracts caught by tests

## Sources

- [[daily/2026-05-13.md]] - Session 11:12: design decision — patch/minor bumps auto-merge via workflow_run trigger, major → Issue; ByeDpiJniContractTest written as gate; coverage ≥95% required before automerge
