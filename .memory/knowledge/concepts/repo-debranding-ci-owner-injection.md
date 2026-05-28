---
title: Repository debranding uses CI owner and repo injection
sources:
  - daily/2026-05-27.md
created: 2026-05-28
updated: 2026-05-28
---
# Repo Debranding CI Owner Injection

## Key Points
- User-specific owner strings must not be hardcoded in public repo code or docs.
- UPDATE_GITHUB_OWNER and UPDATE_GITHUB_REPO belong in CI environment injection, not build.gradle.kts constants.
- GitHub Actions can source owner/repo from github.repository_owner and related repository context.
- Debranding must include Docker labels, scripts, test fixtures, and release metadata.

## Details

During the 2026-05-27 cleanup, references to thesmithmode were removed from Ozero source-controlled metadata. UPDATE_GITHUB_OWNER and UPDATE_GITHUB_REPO were removed from build.gradle.kts and injected by release.yml from GitHub context instead.

The cleanup also rotated the release keystore organization to O=Ozero and updated three GitHub Secrets. This makes public artifacts and update metadata project-branded while keeping repository-specific values supplied by CI.

## Related Concepts
- [[concepts/prod-log-infra-redaction]]
- [[concepts/release-process]]
- [[concepts/github-release-asset-name-verification]]

## Sources
- [[daily/2026-05-27.md]]: Session 16:50 recorded removal of thesmithmode from build.gradle.kts, release.yml owner/repo injection, keystore rotation, and metadata cleanup.
