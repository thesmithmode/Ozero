---
title: "CI Job Dependency Masking"
aliases: [github-actions-needs-masking, ci-needs-chain, detekt-masking-compile]
tags: [ci, github-actions, gotcha, process]
sources:
  - "daily/2026-05-02.md"
created: 2026-05-02
updated: 2026-05-02
---

# CI Job Dependency Masking

In GitHub Actions, when a CI job declares `needs: [other-job]` and the dependency job fails, the dependent job is skipped entirely. This means errors in the dependent job — even compilation errors — remain invisible until the dependency is fixed. In Ozero's `ci.yml`, a detekt style failure in the `kotlin-style` job caused `assemble-debug` to be skipped, hiding a compile error (`Os.close(Int)` doesn't exist) that only surfaced later in the `release.yml` workflow.

## Key Points

- GitHub Actions `needs:` creates hard dependency — if predecessor fails, successor is skipped (not failed, not run at all)
- Detekt failure in `kotlin-style` → `assemble-debug` skipped → compile error invisible in CI
- The compile error only appeared in `release.yml` (which runs `assembleRelease` independently) — a different workflow with different job graph
- This is distinct from Gradle's fail-fast (task-level) — this is GitHub Actions job-level dependency masking
- Mitigation: either remove `needs:` between independent checks, or use `if: always()` on jobs that should run regardless

## Details

### The v0.0.2-5 Incident

During the v0.0.2-5 release cycle, a P1 fix for fd leak introduced `Os.close(rawFd)` — a call to `android.system.Os.close(Int)` that does not exist in Android's POSIX API. The correct pattern is `ParcelFileDescriptor.adoptFd(rawFd).close()`.

This compile error should have been caught by the `assemble-debug` job in `ci.yml`. However, the `assemble-debug` job was configured with `needs: kotlin-style`, and the `kotlin-style` job failed due to a detekt threshold violation (also introduced by the P1 fixes — `thresholdInInterfaces` was 10 but the project rule was 20). Because `kotlin-style` failed, `assemble-debug` was skipped, and the compile error went undetected.

The compile error was only discovered when `release.yml` triggered (on tag push) and ran `assembleRelease` — a job with no `needs:` dependency on style checks. By this point, a pre-release tag had already been created and had to be deleted and recreated after the fix.

### The Masking Chain

```
ci.yml job graph:
  kotlin-style (detekt) ← FAILED (threshold config mismatch)
       ↓ needs
  assemble-debug ← SKIPPED (predecessor failed)
       (compile error: Os.close(Int) not found — INVISIBLE)

release.yml job graph:
  assemble-release ← FAILED (compile error discovered here)
```

The information loss: CI reported "kotlin-style: failed" and the developer fixed the detekt threshold. But the compile error in the same commit was invisible, requiring a second fix-push-wait cycle.

### Broader Pattern

This masking behavior applies to any `needs:` chain in GitHub Actions. Common cases in Android projects:

- Lint job fails → test job skipped → test regressions invisible
- Style job fails → build job skipped → missing imports/compilation errors invisible
- Build job fails → instrumented test job skipped → device-specific failures invisible

The `--continue` flag (Gradle-level, see [[concepts/gradle-continue-full-failures]]) cannot help here because the Gradle process never starts for skipped jobs.

## Related Concepts

- [[concepts/gradle-continue-full-failures]] - Task-level fail-fast mitigation (complements this job-level issue)
- [[concepts/ci-workflow-discipline]] - CI discipline rules that this finding extends
- [[connections/ci-false-green-vectors]] - Another vector for CI reporting incomplete results

## Sources

- [[daily/2026-05-02.md]] - Session 13:20: detekt failure masked compile error (`Os.close(Int)`) in assemble-debug job; discovered only in release.yml; required pre-release tag deletion and recreation
