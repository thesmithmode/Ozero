---
title: "Release Process"
aliases: [release-workflow, apk-release, version-tagging]
tags: [release, ci, workflow]
sources:
  - "daily/2026-04-29.md"
  - "daily/2026-05-20.md"
created: 2026-04-29
updated: 2026-05-20
---

# Release Process

Ozero releases are triggered by pushing a version tag (`v*.*.*`) to the `dev` branch. The `release.yml` GitHub Actions workflow detects the tag, builds a universal APK (`assembleRelease`), and publishes the release artifact. The process is intentionally simple: green CI on `dev` is the only prerequisite for tagging.

## Key Points

- Release tags follow semantic versioning: `v*.*.*` (e.g., `v1.0.5`)
- Tags are created on `dev`, not `main` — `main` is only updated by explicit user command
- `release.yml` in GitHub Actions builds the APK when a version tag is pushed
- The APK is a single universal build for **arm64-v8a only** — `abiFilters = ["arm64-v8a"]` in `app/build.gradle.kts`. Other ABIs (armeabi-v7a, x86_64) are intentionally excluded because the native libraries `libhev-socks5-tunnel`, `libam-go`, `libbyedpi`, `libmtg` are built only for arm64-v8a; widening ABIs without rebuilding native = runtime crash. Sentinel tests in `release.yml` are also arm64-v8a-only.
- R8 minification and shrinking are enabled in the release build (Log.* statements are NOT stripped — see `proguard-rules.pro`)

## Details

The release flow is linear: feature work completes on `dev`, CI goes green, and a version tag is applied. The `release.yml` workflow handles the rest — building the APK with `assembleRelease`, signing it, and creating a GitHub release with the artifact.

During the v1.0.5 release, the tag was created after the third CI run passed (the first two had lint and test failures). A release watcher agent was monitoring the build, but it was terminated before the APK build status could be confirmed. This left the release in an unverified state — the tag existed and the workflow was triggered, but the outcome was not observed.

The release process has a notable constraint: `main` is never touched without explicit user command. Release tags go on `dev`, and the merge from `dev` to `main` is a separate, user-initiated step. This means `main` may lag behind the latest release, and `dev` is the true release branch in practice.

## Related Concepts

- [[concepts/ci-workflow-discipline]] - CI must be green before a release tag is created
- [[concepts/vpn-engine-pipeline]] - The pipeline features shipped in v1.0.5

## Sources

- [[daily/2026-04-29.md]] - v1.0.5 tag created after third CI run; release watcher killed before APK build confirmed
- [[daily/2026-05-20.md]] - KB audit (18:43): arm64-v8a only constraint confirmed; "universal APK" in old summary was misleading — universal means single APK for all users, not multi-ABI; other ABIs excluded because libhev/libam-go/libbyedpi/libmtg are arm64-v8a only
