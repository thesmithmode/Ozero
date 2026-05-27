---
title: "GitHub Workflow Failure Despite All Jobs Success"
aliases: [workflow-false-failure, gh-workflow-conclusion-mismatch]
tags: [github-actions, ci, release]
sources:
  - "daily/2026-05-27.md"
created: 2026-05-27
updated: 2026-05-27
---

# GitHub Workflow Failure Despite All Jobs Success

A GitHub Actions workflow run can report `conclusion: failure` even when every listed job in the run completed with `conclusion: success` and all expected artifacts were produced. This is not a logical contradiction — it occurs when a post-job infrastructure step, a hidden matrix entry, or a GitHub-internal cleanup step exits non-zero after the user-defined jobs complete. The failure does not prevent already-uploaded artifacts or created releases from being valid.

## Key Points

- `gh run list` conclusion and `gh run view` jobs list can show contradictory results: workflow = failure, all jobs = success
- Release artifacts (tags, uploaded assets) created during such a run are fully valid and persist
- The hidden failure source is typically: post-job cleanup, GitHub-internal step, or a skipped-but-required job
- Verifying actual release health: check `gh release view <tag>` and asset presence directly, not the workflow conclusion
- Do not re-trigger the workflow solely because conclusion = failure if release artifacts are present

## Details

During Ozero release pipeline debugging (run 8, v0.2.12), all eight jobs (`resolve-version`, `build-android-apk`, `build-linux`, `build-windows`, `build-macos`, `publish`, and auxiliary jobs) reported `conclusion: success` via the GitHub API. The `publish` job created the tag `v0.2.12` and uploaded four release artifacts: APK, `.deb`, `.exe`, `.dmg`. Yet `gh run list` and the GitHub Actions UI both reported the workflow as `failure`.

The root cause of the conclusion mismatch was not definitively identified after extensive investigation. Leading hypothesis: a post-job infrastructure step (runner cleanup, artifact expiry, or internal GitHub housekeeping) exited non-zero after the user-defined publish step completed successfully. This type of failure is reported in the workflow conclusion but is not visible in the jobs/steps API.

Practical implication: when debugging release pipeline failures, the correct success signal is the presence of all expected artifacts in the GitHub release (`gh release view <tag>`), not the workflow `conclusion` field. A release with all four artifacts is a successful release regardless of what the workflow conclusion shows.

## Related Concepts

- [[concepts/release-process]] - how to verify a release is complete
- [[concepts/github-release-asset-name-verification]] - the complementary check: verify assets by name
- [[concepts/gh-run-list-watcher-race]] - another GitHub Actions CI status reliability issue
- [[concepts/ci-workflow-discipline]] - monitoring and interpreting CI results

## Sources

- [[daily/2026-05-27.md]] - run 8 of release pipeline: all 8 jobs = success, v0.2.12 release created with APK+deb+exe+dmg, but `gh run list` reported failure; root cause not found after extensive log investigation
