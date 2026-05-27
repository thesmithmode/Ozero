---
title: "GitHub Release Asset Name Verification"
aliases: [release-asset-names, byedpi-asset-names, gh-release-asset-check]
tags: [github-actions, release, ci, byedpi]
sources:
  - "daily/2026-05-27.md"
created: 2026-05-27
updated: 2026-05-27
---

# GitHub Release Asset Name Verification

GitHub release asset filenames are set by the uploading workflow or tool and do not follow a predictable naming convention. A downstream workflow that downloads an asset by a guessed filename will fail silently or with a 404/curl error when the actual filename differs. The only reliable way to determine an asset's exact name is to inspect the release directly: `gh release view <tag> --json assets` or `gh release view <tag>`.

## Key Points

- Asset names are set by the publisher's workflow; they do not follow semantic versioning patterns by default
- A common pattern mismatch: `byedpi-17.3-x86_64-linux.tar.gz` (guessed from version number) vs `byedpi-Linux.tar.gz` (actual) for ByeDPI v0.17.3
- Guessing based on version number (`<name>-<version>-<arch>-<platform>.<ext>`) is unreliable
- Verification command: `gh release view <tag> --repo <owner>/<repo>` — lists all assets with exact filenames
- Failure mode: `curl` or `wget` step exits with HTTP 404 or empty download, silently producing a zero-byte binary that causes build failures later

## Details

In the Ozero release pipeline, the `build-linux` job downloads a pre-built `byedpi` binary from its upstream GitHub release, then packages it into a `.deb`. The pipeline was written with the assumed filename `byedpi-17.3-x86_64.tar.gz`, following the convention `<name>-<version>-<arch>.<ext>`. The actual filename in the ByeDPI v0.17.3 release was `byedpi-Linux.tar.gz` — platform only, no version or arch in the name.

This mismatch caused the first code-level failure in a series of 8 failed release runs. The fix was straightforward once the root cause was identified: inspect the actual release assets and hardcode the correct name. Future-proofing would involve parsing the asset list dynamically via `gh release view --json assets | jq -r '.assets[].name'` and matching by pattern.

The broader principle: before writing any pipeline step that downloads a release artifact from a third-party repository, verify the exact asset name by browsing an actual release. Version numbers in asset filenames are inconsistently applied across projects — some include them, some do not, some use different separators (`-`, `_`, `.`), and some embed architecture descriptors that do not match the local variable format.

## Related Concepts

- [[concepts/release-process]] - Ozero's multi-platform release pipeline downloads upstream binaries
- [[concepts/new-engine-module-ci-checklist]] - checklist for CI integration of new engine binaries
- [[concepts/github-draft-release-invisible]] - related GitHub release visibility gotcha
- [[concepts/byedpi-args-parsing]] - ByeDPI binary integration details

## Sources

- [[daily/2026-05-27.md]] - run 3 of release pipeline failed because `byedpi-17.3-x86_64.tar.gz` 404'd; actual asset name was `byedpi-Linux.tar.gz`; discovered via `gh release view v0.17.3`
