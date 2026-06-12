---
title: "Release Process"
aliases: [release-workflow, apk-release, version-tagging]
tags: [release, ci, workflow]
sources:
  - "daily/2026-04-29.md"
  - "daily/2026-05-04.md"
  - "daily/2026-05-20.md"
  - "daily/2026-05-23.md"
  - "daily/2026-05-27.md"
  - "daily/2026-05-18.md"
created: 2026-04-29
updated: 2026-06-12
---

# Release Process

Ozero releases are triggered by pushing a version tag (`v*.*.*`) to the `dev` branch. The `release.yml` GitHub Actions workflow detects the tag, builds artifacts for four platforms (Android APK, Linux `.deb`, Windows `.exe`, macOS `.dmg`), and publishes all artifacts to a GitHub Release. Green CI on `dev` is the only prerequisite for tagging.

## Key Points

- Release tags follow semantic versioning: `v*.*.*` (e.g., `v1.0.5`)
- Tags are created on `dev`, not `main` — `main` is only updated by explicit user command
- `release.yml` in GitHub Actions builds the APK when a version tag is pushed
- The APK is a single universal build for **arm64-v8a only** — `abiFilters = ["arm64-v8a"]` in `app/build.gradle.kts`. Other ABIs (armeabi-v7a, x86_64) are intentionally excluded because the native libraries `libhev-socks5-tunnel`, `libam-go`, `libbyedpi`, `libmtg` are built only for arm64-v8a; widening ABIs without rebuilding native = runtime crash. Sentinel tests in `release.yml` are also arm64-v8a-only.
- R8 minification and shrinking are enabled in the release build (Log.* statements are NOT stripped — see `proguard-rules.pro`)
- **`fetch-depth: 0` is REQUIRED in `actions/checkout`** — without it, shallow clone makes `git rev-list --count HEAD = 1`, producing `versionCode=1` → Android rejects install as downgrade (`INSTALL_FAILED_VERSION_DOWNGRADE`)

## Details

### Prerelease vs Full Release Tag Convention

`release.yml` determines whether a GitHub Release is a pre-release or full release by inspecting the tag string: `prerelease = contains(tag, '-')`. A tag without a hyphen (e.g., `v0.0.2`) produces a full release visible to all users. A tag with a hyphen (e.g., `v0.0.2-5`, `v1.0.0-rc1`) produces a pre-release. This means the tagging convention directly controls release visibility without any additional workflow flags.

Consequence: when a tag is created incorrectly (wrong prerelease/full designation), the tag must be deleted and recreated. During v0.0.2, the tag was recreated twice — once to fix the OkHttp hotfix and once to remove stub classes from the APK.

The release flow is linear: feature work completes on `dev`, CI goes green, and a version tag is applied. The `release.yml` workflow handles the rest — building the APK with `assembleRelease`, signing it, and creating a GitHub release with the artifact.

During the v1.0.5 release, the tag was created after the third CI run passed (the first two had lint and test failures). A release watcher agent was monitoring the build, but it was terminated before the APK build status could be confirmed. This left the release in an unverified state — the tag existed and the workflow was triggered, but the outcome was not observed.

The release process has a notable constraint: `main` is never touched without explicit user command. Release tags go on `dev`, and the merge from `dev` to `main` is a separate, user-initiated step. This means `main` may lag behind the latest release, and `dev` is the true release branch in practice.

### versionCode Shallow Clone Trap (v0.2.0 regression, 2026-05-23)

`versionCode` is computed as `git rev-list --count HEAD` — the total number of commits reachable from HEAD. GitHub Actions `actions/checkout` defaults to `fetch-depth: 1` (shallow clone). A shallow clone has exactly 1 commit → `git rev-list --count HEAD = 1` → `versionCode = 1`.

Any previously installed APK with `versionCode > 1` causes Android to reject the new install with `INSTALL_FAILED_VERSION_DOWNGRADE` — displayed to the user as "Не удалось получить информацию о пакете." The user sees a generic error and cannot install the update without uninstalling first, which loses their data.

Fix: `fetch-depth: 0` in the checkout step of `release.yml`:
```yaml
- uses: actions/checkout@v4
  with:
    fetch-depth: 0   # ← required; default 1 = shallow = versionCode=1
```

A sentinel assert `versionCode > 1` was added to the pipeline to catch regressions:
```yaml
- name: Assert versionCode > 1
  run: |
    vc=$(git rev-list --count HEAD)
    [ "$vc" -gt 1 ] || (echo "versionCode=$vc, need fetch-depth: 0" && exit 1)
```

The rule: **any CI job that uses `git rev-list --count` for version numbering must use `fetch-depth: 0`**. Debug CI typically does not compute versionCode, so this trap only manifests in `release.yml`.

### GitHub Force-Push on Existing Tag → HTTP 500

Force-pushing to an existing git tag on GitHub can return HTTP 500 (Internal Server Error). The safe procedure for re-releasing with a corrected tag:

1. Delete tag locally: `git tag -d v0.2.1`
2. Delete tag via GitHub API: `gh api -X DELETE repos/{owner}/{repo}/git/refs/tags/v0.2.1`
3. Create new tag pointing to the corrected commit: `git tag v0.2.1 <sha>`
4. Push new tag: `git push origin v0.2.1`

Never use `git push --force origin v0.2.1` — the GitHub API may respond with 500 and leave the tag in an indeterminate state. Came up during v0.2.3 re-release when versionCode was corrected.

### Multi-Platform Release Architecture (v0.2.12+)

`release.yml` builds four artifacts in parallel jobs:

| Job | Artifact | Notes |
|-----|----------|-------|
| `build-android-apk` | `Ozero-vX.Y.Z.apk` | arm64-v8a only; R8 minify enabled |
| `build-linux` | `ozero_X.Y.Z_amd64.deb` | Downloads upstream byedpi binary; packages with dpkg-deb |
| `build-windows` | `Ozero-X.Y.Z-setup.exe` | Gradle on Windows runner; requires `shell: bash` for `./gradlew` |
| `build-macos` | `Ozero-X.Y.Z.dmg` | Gradle on macOS runner |

Desktop jobs (Linux, Windows, macOS) require `GRADLE_OPTS="-Xmx4g --no-daemon"` — the default 2GB heap is insufficient for R8 dex compilation on GitHub-hosted runners.

The `publish` job runs after all four build jobs, creates a git tag, and uploads all four artifacts to a GitHub Release. The workflow `conclusion` field is unreliable — a release with all four artifacts present is a successful release regardless of what `gh run list` reports. Verify via `gh release view <tag>`.

### Upstream Binary Asset Names

Pipeline steps that download binaries from upstream GitHub releases must use verified exact asset filenames — do not guess from version numbers. Check via `gh release view <tag> --repo <owner>/<repo>` before writing the download step. Example mismatch: assumed `byedpi-17.3-x86_64.tar.gz` vs actual `byedpi-Linux.tar.gz`.

## Related Concepts

- [[concepts/ci-workflow-discipline]] - CI must be green before a release tag is created
- [[concepts/vpn-engine-pipeline]] - The pipeline features shipped in v1.0.5
- [[concepts/release-stub-gate]] - `release.yml` has independent validation gates (stub check) beyond CI
- [[concepts/versioncode-git-history-rewrite-regression]] - versionCode defense that led to re-release procedure
- [[concepts/ci-heredoc-single-quote-variable-trap]] - `<< 'HEREDOC'` blocks `$VERSION` expansion in deb packaging
- [[concepts/github-release-asset-name-verification]] - verifying upstream binary asset filenames
- [[concepts/github-workflow-failure-all-jobs-success]] - workflow conclusion vs actual artifact health
- [[concepts/gradle-r8-oom-github-runners]] - R8 OOM fix for desktop build jobs
- [[concepts/windows-runner-gradlew-shell]] - Windows runner requires `shell: bash` for `./gradlew`

## Sources

- [[daily/2026-04-29.md]] - v1.0.5 tag created after third CI run; release watcher killed before APK build confirmed
- [[daily/2026-05-04.md]] - Session 12:05: prerelease = contains(tag, '-') logic confirmed; v0.0.2 full release (no hyphen); tag recreated twice (OkHttp fix + stub removal)
- [[daily/2026-05-20.md]] - KB audit (18:43): arm64-v8a only constraint confirmed; "universal APK" in old summary was misleading — universal means single APK for all users, not multi-ABI; other ABIs excluded because libhev/libam-go/libbyedpi/libmtg are arm64-v8a only
- [[daily/2026-05-18.md]] - Session 10:41: v0.1.1 release failed because the `libmtg.so` sentinel still expected three ABIs; fix aligned it with arm64-v8a-only APK packaging and updated stale ABI instructions
- [[daily/2026-05-23.md]] - Session 20:44: v0.2.0 regression — `actions/checkout` without `fetch-depth: 0` → shallow clone → `git rev-list --count HEAD = 1` → `versionCode=1` → Android `INSTALL_FAILED_VERSION_DOWNGRADE`; fix: `fetch-depth: 0` + sentinel assert `versionCode > 1` in pipeline (commit `75e72b48`)
- [[daily/2026-05-25.md]] - GitHub force-push on existing tag → HTTP 500; safe path: delete via API + POST new tag; used during v0.2.3 re-release (singbox go.Seq fix)
- [[daily/2026-05-27.md]] - 8 failed release runs debugging: byedpi asset name mismatch, deb heredoc variable trap, Windows gradlew shell, R8 OOM on macOS/Windows runners; v0.2.12 successfully released with all 4 artifacts
