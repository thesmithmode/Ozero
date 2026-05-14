---
title: "GitHub Draft Release Visibility: CLI Omission + 404 Assets"
aliases: [draft-release-invisible, gh-release-list-drafts, draft-404]
tags: [github, ci, gotcha, release]
sources:
  - "daily/2026-05-14.md"
created: 2026-05-14
updated: 2026-05-14
---

# GitHub Draft Release Visibility: CLI Omission + 404 Assets

`gh release list --json tagName` omits draft releases from output. Asset download URLs for draft releases return 404 for unauthenticated requests. CI workflows that download release assets fail silently when a release is marked as draft — the tag exists but the binary is invisible.

## Key Points

- `gh release list --json tagName` does NOT include draft releases — they are filtered out by default
- Asset URLs for draft releases return 404 for unauthenticated HTTP requests (even though assets exist)
- Fix: `gh release edit <tag> --draft=false` publishes the release without rebuilding
- After tag rotation or release recreation, always verify `--draft` status with `gh release view <tag>`
- Symptom in CI: "Get URnetwork AAR tag" or "Download native binaries" step fails — tag not found or download 404

## Details

### The Incident (2026-05-14)

Three binary releases used by CI (`byedpi-39af2ae9`, `urnetwork-sdk-b35b0ec5`, `urnetwork-sdk-0a32407e`) were marked as Draft. CI run 25866630786 failed because:

1. `gh release list --json tagName` did not return these tags
2. Direct asset download URLs returned 404 (unauthenticated)
3. The releases had 994+ downloads previously — assets were intact, just hidden

Fix was `gh release edit <tag> --draft=false` for all three — no rebuild needed, download counts preserved.

### Draft vs Deleted

A draft release is NOT a deleted release. The tag, assets, and metadata all exist. But GitHub treats draft releases as private — visible only to repo collaborators via API with authentication. The `gh release list` command filters them out by default, creating the illusion that the release doesn't exist.

## Related Concepts

- [[concepts/release-process]] - Ozero release workflow: tag v*.*.* on dev → release.yml builds APK; draft status can block this
- [[concepts/codeql-aar-dependency-gap]] - Related CI infra failure: missing AAR download step; both are CI dependency resolution failures

## Sources

- [[daily/2026-05-14.md]] - Session 18:00+: 3 draft releases caused CI failure; `gh release list` omits drafts; fix = `gh release edit --draft=false`; assets intact with 994+ downloads
