---
title: "Native Binary Auto-Update Pipeline via GitHub Actions"
aliases: [binaries-auto-update, native-binary-ci-commit, auto-merge-binaries]
tags: [ci, github-actions, native, architecture, automation]
sources:
  - "daily/2026-05-13.md"
  - "daily/2026-05-20.md"
created: 2026-05-13
updated: 2026-05-20
---

# Native Binary Auto-Update Pipeline via GitHub Actions

Ozero's native engines (ByeDPI, AmneziaWG, Hysteria2) are compiled by `binaries.yml` workflows. Rather than requiring manual commits of new SO/AAR artifacts, an auto-update pipeline (`native-auto-apply.yml`) is triggered on successful `binaries.yml` completion: it commits the new artifacts to a branch, runs CI, and squash-merges into `dev` automatically for patch/minor bumps. Major version bumps create a GitHub Issue instead.

## Key Points

- `binaries.yml` job `update-repo`: after build, commits artifacts to `bot/update-<engine>-<sha8>` branch
- `native-auto-apply.yml` triggers on `workflow_run` completion of `binaries.yml`: CI green → squash into `dev`; red → open Issue; major version → open Issue without merge
- Coverage ≥95% + contract tests are gates before automerge
- Limitation: auto-update cannot catch runtime SIGSEGV on vendor ROMs or handshake timeouts — only compile-time + contract-level regressions
- Contract tests that guard auto-update: `ByeDpiJniContractTest` (3 external fun + loadLibrary), AmneziaWgRuntimeBinaryTest (SHA256)
- `binaries.lock.yaml` stores per-ABI SHA256 hashes; `regen_lock.py` requires downloaded `.so` + `manifest.yaml` to regenerate
- Prebuilt Go binaries (e.g., `libmtg.so`) follow the same lock pattern but are run via `ProcessBuilder`, not `System.loadLibrary`

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

### Manual Pin Upgrade: ByeDPI ba532298 (2026-05-20)

The auto-update pipeline handles patch/minor bumps automatically, but upstream commit pin upgrades require manual intervention when the library is pinned to a specific commit hash rather than a semantic version tag.

**Case study: ByeDPI YouTube fix (v0.1.9)**

The ByeDPI native library was pinned at `v0.17.3` tag (commit `7efde1b1`, 2025-09-19). ByeByeDPI 1.7.5 (the reference implementation Ozero follows for parity) had advanced to commit `ba532298` (2026-03-26) — 38 commits ahead. Those 38 commits included upstream fixes for YouTube QUIC/ECH/TLS-extension reordering, which are required to bypass Russian ISP filtering on video platforms. Ozero was blocking YouTube even though the same strategies worked in ByeByeDPI 1.7.5, because the native binary was 6 months stale.

**Fix procedure:**
1. Update `build_byedpi.sh` header comment with new commit hash (forces binaries.yml to regenerate)
2. Update `binaries.yml` to clone by commit hash `ba532298` instead of tag `v0.17.3`
3. Run `binaries.yml` workflow_dispatch manually → new artifact `byedpi-e4e9f53e`
4. Commit updated `binaries.lock.yaml` with new SHA256

**Lesson:** Tags are a lagging indicator of upstream progress. Commit-pinned libraries can fall significantly behind on bug fixes. The symptom (YouTube works in reference app but not in Ozero with same args) is the signal to bisect the upstream commit history and identify the divergence point.

## Sources

- [[daily/2026-05-13.md]] - Session 11:12: design decision — patch/minor bumps auto-merge via workflow_run trigger, major → Issue; ByeDpiJniContractTest written as gate; coverage ≥95% required before automerge
- [[daily/2026-05-20.md]] - v0.1.9 prep: ByeDPI pin v0.17.3 (7efde1b1, 2025-09-19) → ba532298 (2026-03-26, 38 commits, YouTube QUIC/ECH fix); build_byedpi.sh header comment bump + binaries.yml clone-by-commit; new artifact byedpi-e4e9f53e
