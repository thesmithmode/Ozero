---
title: "Gradle R8 OOM on GitHub Runners"
aliases: [r8-oom, gradle-oom-runner, jvm-heap-release-build]
tags: [gradle, r8, github-actions, oom, release, android]
sources:
  - "daily/2026-05-27.md"
created: 2026-05-27
updated: 2026-05-27
---

# Gradle R8 OOM on GitHub Runners

The default JVM heap size in `gradle.properties` (`org.gradle.jvmargs=-Xmx2048m`) is insufficient for R8/D8 dex compilation during release builds on GitHub-hosted macOS and Windows runners. These runners typically have ~7GB RAM total, and with OS overhead, the 2GB heap for the Gradle daemon runs out during the full R8 minification + shrinking pass. The fix is to override `GRADLE_OPTS` in the CI step with `-Xmx4g` and add `--no-daemon` to prevent heap fragmentation across daemon invocations.

## Key Points

- Default `gradle.properties`: `org.gradle.jvmargs=-Xmx2048m` — insufficient for R8 release builds on hosted runners
- GitHub macOS runner: ~7GB RAM, Windows runner: ~7GB RAM — both need explicit heap override
- GitHub Linux runner: ~7GB RAM — Linux typically succeeds with 2GB because of lower OS overhead
- Fix: set `GRADLE_OPTS: "-Xmx4g --no-daemon"` as a step or job env var in release CI
- `--no-daemon` prevents Gradle daemons from accumulating heap across parallel task invocations
- Symptom: `java.lang.OutOfMemoryError: Java heap space` in the R8 compilation step, not during task graph resolution

## Details

R8 (Android's replacement for ProGuard) performs whole-program optimization including full dex compilation, inlining, class merging, and dead code elimination. For a non-trivial Android project with multiple engine modules and their dependencies, the peak memory usage during R8 optimization can exceed 3GB of heap. The 2GB default is adequate for debug builds (`assembleDebug`) and unit test compilation, but not for `assembleRelease` with `minifyEnabled=true` and `shrinkResources=true`.

The failure pattern on macOS/Windows runners is consistent: the Gradle task graph resolves successfully, all compilation steps complete, and then the process crashes during the R8 dex compilation phase with `java.lang.OutOfMemoryError: Java heap space`. The error appears in the Gradle output but not in task-level logs visible via `gh run view --log-failed` until the full log is retrieved.

Linux runners succeed with the 2GB default in practice, likely because the Linux kernel's memory management and lower base OS overhead leave more space for the JVM. However, the margin is thin and any future dependency growth could cause Linux to fail as well. The robust fix is to set the override for all three platforms.

The `GRADLE_OPTS` environment variable overrides `org.gradle.jvmargs` for the current invocation without modifying the project file. This is preferable for CI because it avoids conflating the developer default (which should remain 2GB for laptops) with the CI requirement (where 4GB is available and needed).

```yaml
- name: Build release APK
  env:
    GRADLE_OPTS: "-Xmx4g --no-daemon"
  run: ./gradlew assembleRelease
```

## Related Concepts

- [[concepts/release-process]] - R8 OOM occurs specifically in the release build pipeline
- [[concepts/windows-runner-gradlew-shell]] - companion Windows runner issue in the same pipeline
- [[concepts/ci-workflow-discipline]] - CI configuration for resource-constrained environments
- [[concepts/proguard-release-drift]] - R8 configuration issues that can interact with memory usage

## Sources

- [[daily/2026-05-27.md]] - runs 4-8 of release pipeline: macOS and Windows jobs failed with `java.lang.OutOfMemoryError: Java heap space` during R8 dex compilation; fix: `GRADLE_OPTS="-Xmx4g --no-daemon"` added to all desktop build jobs
