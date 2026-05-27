---
title: "GitHub Draft Release: Invisible to API and 404 Assets"
aliases: [draft-release-invisible, gh-release-draft-404]
tags: [ci, github, release, gotcha]
sources:
  - "daily/2026-05-14 (1).md"
created: 2026-05-27
updated: 2026-05-27
---

# GitHub Draft Release: Invisible to API and 404 Assets

GitHub draft releases are excluded from `gh release list` output and all public asset download URLs return HTTP 404 for unauthenticated requests. CI workflows that fetch release assets via download URLs will fail silently as if the release doesn't exist — not as a draft-visibility error.

## Key Points

- `gh release list --json tagName` omits draft releases; they are invisible unless `--include-drafts` is explicitly passed
- Asset download URLs for draft releases return 404 for unauthenticated HTTP requests (CI runners, scripts without `GITHUB_TOKEN`)
- `gh release view <tag>` shows `isDraft: true` — use this to diagnose unexplained 404s
- Fix: `gh release edit <tag> --draft=false` publishes the release and restores asset URLs without rebuilding
- APK built from draft tag should be considered untested until CI validates it on a non-draft run

## Details

### The Ozero Incident (2026-05-14)

Three binary releases — `byedpi-39af2ae9`, `urnetwork-sdk-b35b0ec5`, `urnetwork-sdk-0a32407e` — were accidentally left as Draft after creation. CI workflow steps `Get URnetwork AAR tag` and `Download native binaries` both failed with 404/not-found errors.

The original assumption was that the releases had been deleted. Asset download counters (994+) proved they were intact — just hidden as drafts. Running `gh release edit <tag> --draft=false` on each restored full access without any rebuild.

### Why This Happens

GitHub treats draft releases as pre-publication staging. The intent is that draft assets are not publicly accessible until explicitly published. The side effect: any automation that does not pass an authenticated token with `repo` scope will see 404 for draft asset URLs, and the list API simply excludes drafts by default.

### CI Tag Rotation Pitfall

When a release tag is deleted and recreated (e.g., `v0.1.0` moved to a new commit), the new release is often created as a draft first. If CI triggers immediately on the tag push before `--draft=false` is applied, the workflow fails. Procedure: after any tag rotation, verify `gh release view <tag> --json isDraft` before assuming CI will pass.

### Detection Command

```bash
gh release view <tag> --json isDraft,tagName,url
# isDraft: true → run: gh release edit <tag> --draft=false
```

## Related Concepts

- [[concepts/release-process]] - Ozero release procedure: tag → release.yml → APK; draft status is a deployment blocker
- [[concepts/gh-run-list-watcher-race]] - Related API freshness issue: `gh run list` can return stale completed run; both are API visibility traps in CI tooling

## Sources

- [[daily/2026-05-14 (1).md]] - Session 18:00+: `Get URnetwork AAR tag` + `Download native binaries` failed; 3 releases were Draft (byedpi-39af2ae9, urnetwork-sdk-b35b0ec5, urnetwork-sdk-0a32407e); asset URLs 404 unauth; fix = `gh release edit --draft=false`; assets intact (downloadCount 994+)
