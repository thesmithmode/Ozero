---
title: GitHub Actions artifact actions need Node 24 migration
sources:
  - [[daily/2026-05-28]]
created: 2026-05-31
updated: 2026-05-31
---

# GitHub Actions artifact actions need Node 24 migration

## Summary

GitHub Actions deprecation warnings for artifact upload/download actions should be fixed on `dev` before they become release blockers, then validated by a later workflow run.

## Key Points

- The `v1.0.4` release showed a Node.js 20 deprecation warning for `actions/upload-artifact` and `actions/download-artifact`.
- The workflow update was pushed to `dev` only after the user clarified that `main` should not be touched.
- The follow-up validation requirement is a future CI/release run proving the newer action major works.
- This is a narrower instance of [[concepts/github-actions-artifact-node-major-upgrade]] and [[concepts/release-process]].

## Details

After `v1.0.4`, GitHub Actions warned that the artifact actions were still on a Node.js 20 runtime. The warning was not treated as a current release failure, but it was actionable technical debt because such runtime deprecations can later turn into hard workflow failures.

The fix belonged on `dev`, not `main`, because the user explicitly said to avoid touching `main` after the release. The remaining acceptance condition was not simply editing YAML; the next relevant push or release workflow had to confirm that the upgraded artifact actions were supported and did not break publishing.

## Related Concepts

- [[concepts/github-actions-artifact-node-major-upgrade]]
- [[concepts/release-process]]
- [[concepts/ci-workflow-discipline]]
- [[concepts/github-actions-run-id-monitoring]]

## Sources

- [[daily/2026-05-28]]: Sessions 20:02 and 20:10 recorded the Node.js 20 warning and the decision to update artifact actions on `dev` only.
- [[daily/2026-05-28]]: The action item required future validation that the workflow update does not break release behavior.
