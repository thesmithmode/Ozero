---
title: "GitHub Draft Release: Hidden from gh CLI and Assets Return 404"
aliases: [draft-release-404, gh-release-draft-hidden, draft-asset-unavailable]
tags: [ci, github, release, gotcha]
sources:
  - "daily/2026-05-14.md"
created: 2026-05-14
updated: 2026-05-14
---

# GitHub Draft Release: Hidden from gh CLI and Assets Return 404

GitHub Draft releases are not returned by `gh release list --json tagName` (without `--include-drafts`). Their asset download URLs return HTTP 404 for unauthenticated requests (including CI runners using public repository workflows without token-authenticated asset download). This causes CI steps that depend on release assets (e.g., downloading native binaries) to fail silently or with a 404 error, even though the release and assets exist in GitHub's UI.

## Key Points

- `gh release list --json tagName` omits Draft releases — the tag appears to not exist
- Asset URLs for Draft releases return 404 for unauthenticated requests; CI `curl`/`wget` without auth token fails
- Symptom: `Get <binary> tag` CI step reports "tag not found" or asset download fails with 404
- Fix: `gh release edit <tag> --draft=false` publishes the release; no binary rebuild needed — assets are preserved
- Always check `isDraft` field: `gh release view <tag> --json isDraft` before debugging asset issues

## Details

### The Mechanism

GitHub releases have three states: Draft, Pre-release, and Release. Draft releases are hidden from the public API and from `gh release list` by default. The intent is that draft releases are staging areas — not visible until explicitly published.

In Ozero's CI pipeline, `ci.yml` downloads native binaries from tagged GitHub Releases:
```
gh release download <tag> --repo <owner>/<repo> --pattern "*.so"
```

If the release for `<tag>` is in Draft state, this command reports "release not found" or fails with 404 on the asset URL.

### The 2026-05-14 Incident

After WARP engine H1/H3/H4 fixes were merged, CI run 25866630786 failed on:
- `Get URnetwork AAR tag`
- `Download native binaries`

Initial diagnosis suspected the binaries were deleted. Investigation with `gh release view <tag> --json isDraft` revealed three releases were in Draft state:

| Tag | Status |
|-----|--------|
| `byedpi-39af2ae9` | Draft |
| `urnetwork-sdk-b35b0ec5` | Draft |
| `urnetwork-sdk-0a32407e` | Draft |

These had 994+ downloads (the download count visible in GitHub UI despite Draft status for authenticated users), confirming the assets existed. They had been created as Drafts accidentally when the release workflow used `--draft` flag or was not explicitly published.

Fix applied without rebuild:
```bash
gh release edit byedpi-39af2ae9 --draft=false
gh release edit urnetwork-sdk-b35b0ec5 --draft=false
gh release edit urnetwork-sdk-0a32407e --draft=false
```

CI rerun succeeded immediately.

### Post-Tag Rotation Verification

After retagging (e.g., moving `v0.1.0` to a new commit), check Draft status before assuming the release is complete:

```bash
gh release view v0.1.0 --json isDraft,tagName,assets
```

A `release.yml` workflow that creates a release via `gh release create` defaults to published (not draft) unless `--draft` is passed, but manual releases or other workflows may default differently.

### Prevention

After any release creation, verify:
1. `gh release view <tag> --json isDraft` → must be `false`
2. Test an asset URL with `curl -I <asset-url>` → must return 200 (or 302 redirect), not 404
3. If CI uses a GitHub token, verify the token has `contents:read` scope for private repos

## Related Concepts

- [[concepts/release-process]] - Ozero release process: dev branch → vX.Y.Z tag → release.yml APK; prerelease detection via `contains(tag, '-')`
- [[concepts/ci-workflow-discipline]] - Tag only after CI green; verify release state as part of release checklist
- [[concepts/native-binary-auto-update-pipeline]] - Native binaries downloaded from GitHub Releases during CI; Draft status breaks this download

## Sources

- [[daily/2026-05-14.md]] — Session 18:00+: CI run 25866630786 failed on `Get URnetwork AAR tag` + `Download native binaries`; 3 releases in Draft state (byedpi-39af2ae9, urnetwork-sdk-b35b0ec5, urnetwork-sdk-0a32407e); assets intact (994+ downloads); fixed with `gh release edit --draft=false`; no rebuild needed
