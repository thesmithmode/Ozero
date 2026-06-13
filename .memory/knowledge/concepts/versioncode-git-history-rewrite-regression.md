---
title: "versionCode Regression After Git History Rewrite"
aliases: [versioncode-regression, history-rewrite-versioncode]
tags: [android, release, git, versioncode]
sources:
  - "daily/2026-05-25.md"
  - "daily/2026-05-23.md"
created: 2026-05-25
updated: 2026-06-12
---

# versionCode Regression After Git History Rewrite

When `versionCode` is computed from `git rev-list --count HEAD`, any operation that rewrites git history (rebase, amend, filter-branch, force-push for author cleanup) can reduce the total commit count. If the new count is lower than the versionCode of a previously installed APK, Android rejects the install with "package invalid" or `INSTALL_FAILED_VERSION_DOWNGRADE`.

## Key Points

- `git rev-list --count HEAD` returns the number of commits reachable from HEAD — git history rewrite can shrink this
- v0.2.0 had versionCode=1155; after AI authorship removal rebase, dev branch had only ~618 commits → versionCode=618 < 1155 → install blocked
- `git describe --tags` picks up the nearest reachable tag — after rewrite, `v0.2.0` tag became unreachable; `v0.1.11` was used instead → versionName regressed too
- Fix: `versionCode = max(commitCount + OFFSET, FLOOR)` where FLOOR > last known versionCode
- CI assertion must use hard threshold (`>= 2000`), not relative (`> 1`) — relative passes even when regressed

## Details

### Root Cause Mechanism

The Ozero v0.2.1 APK produced "пакет недействителен" on install. Initial investigation ruled out V1 signing (red herring — CERT.RSA present but V1=false in `apksigner verify`), R8 stripping, and INDEX.LIST artifacts. The real cause: versionCode 618 < 1155 from v0.2.0.

The regression was introduced by a prior session that rewrote git history to remove AI authorship (`rebase+amend+force-push`). This reduced `git rev-list --count HEAD` from ~1155 to ~618. The tag `v0.2.0` pointed to a commit no longer in the linear history after force-push, making `git describe` fall back to `v0.1.11` — so both versionCode and versionName regressed simultaneously.

The same trap can happen without rewriting history if release CI uses a shallow checkout. In v0.2.0 release work, `actions/checkout` without `fetch-depth: 0` made the release job see only one commit, so `git rev-list --count HEAD` returned `1` and the APK could not upgrade over the previous installed build. The fix belongs in `release.yml`, not as user advice to uninstall the old APK: updates must be monotonic and install over the prior version.

### Defense Pattern

```kotlin
// app/build.gradle.kts
val versionCodeOffset = 1500
val versionCodeFloor = 2000
val gitCommitCount: Int = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
}.standardOutput.asText.get().trim().toIntOrNull() ?: 1
val gitVersionCode: Int = maxOf(gitCommitCount + versionCodeOffset, versionCodeFloor)
```

The `FLOOR` (2000) must be set above the last known-good versionCode from any released APK. The `OFFSET` (1500) ensures that even if commit count is reset to a small number, the floor prevents downgrade.

CI assertion (in `release.yml`):
```yaml
- name: Assert versionCode >= 2000
  run: |
    vc=$(./gradlew -q :app:printVersionCode)
    [ "$vc" -ge 2000 ] || (echo "versionCode=$vc too low" && exit 1)
```

The ktlint fix was also required: multi-line expression with `providers.exec { ... }` inside `maxOf()` violated brace rules; fix was to extract to a separate `val` first.

### Related Context

After any git history rewrite operation, verify that versionCode remains monotonically increasing before creating a release tag. The `apksigner verify` V1=false output in this incident was a misleading red herring — V1 signing presence does not affect the "package invalid" error from a versionCode downgrade.

Release workflows that compute versionCode from git history must use `actions/checkout` with `fetch-depth: 0`; a shallow clone can cause the same downgrade class even when history was not rewritten.

## Related Concepts

- [[concepts/release-process]] - Release workflow and other versionCode traps (shallow clone)
- [[concepts/git-contributor-rewrite]] - The git history rewrite that caused this regression
- [[connections/release-checks-beyond-ci]] - CI and release assertions that guard release quality

## Sources

- [[daily/2026-05-25.md]] — v0.2.1 install failure root cause: versionCode 618 < 1155 from v0.2.0; history rewrite (AI authorship removal) reduced commit count; fix: max(commitCount+1500, 2000) + CI assertion >= 2000; versionName regression via git describe picking unreachable tag
