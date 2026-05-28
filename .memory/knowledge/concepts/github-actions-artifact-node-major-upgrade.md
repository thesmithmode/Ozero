---
title: GitHub Actions artifact Node major upgrade warning
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---
# GitHub Actions artifact Node major upgrade warning

## Key Points
- GitHub Actions can show deprecation warnings for `actions/upload-artifact` and `actions/download-artifact` even when the release workflow succeeds.
- The warning is operational debt: update the artifact actions on `dev` before the deprecated Node runtime becomes blocking.
- Workflow fixes after a release should be pushed to `dev` only unless the user explicitly permits touching `main`.
- The follow-up CI run must prove the newer action major is supported by the release workflow.

## Details

During the `v1.0.4` release cycle, the release workflow succeeded but GitHub Actions emitted a Node.js 20 deprecation warning for `upload-artifact/download-artifact@v4`. This did not invalidate the release, but it created a follow-up maintenance task because future GitHub-hosted runner policy can turn such warnings into failures.

The fix belongs in workflow maintenance on `dev`, not directly in `main`, when the user only asks to handle the warning after a release. The follow-up validation is not the release that already passed; it is the next push/CI cycle that proves the updated artifact actions are accepted by GitHub Actions.

## Related Concepts
- [[concepts/release-process]]
- [[concepts/ci-workflow-discipline]]
- [[connections/release-status-vs-asset-verification]]

## Sources
- [[daily/2026-05-28]] records that release `v1.0.4` succeeded while GitHub Actions warned about Node.js 20 deprecation for artifact actions.
- [[daily/2026-05-28]] records the decision to push the workflow fix only to `dev` after the user clarified that `main` must not be touched.
