---
title: "Reading CI Gradle Errors in GitHub Actions"
aliases: [gh-log-failed, ci-log-access, gradle-ci-error-reading]
tags: [ci, github-actions, gradle, debugging]
sources:
  - "daily/2026-05-24.md"
created: 2026-05-24
updated: 2026-05-24
---

# Reading CI Gradle Errors in GitHub Actions

GitHub Actions does not expose Gradle build stdout through the checks API, annotations API, or job summary endpoints. The only reliable method to read Kotlin/Gradle compile errors from a failed CI run is `gh run view --log-failed`. All other API approaches (`/checks`, `/annotations`, job step `output`, artifact download) return empty or unauthorized responses for Gradle builds.

## Key Points

- `gh run view <run-id> --log-failed` streams the failed step output and is the only working method
- Checks API (`gh api repos/.../check-runs`) returns empty `output.text` for Gradle build jobs
- Annotations API returns 0 annotations for Gradle compile failures — GitHub doesn't parse Gradle output into annotations
- Artifact download for logs requires special auth (`gh api repos/.../actions/artifacts`) — often returns 401 or a zip that can't be read inline
- CI runner disk exhaustion (`No space left on device`) looks like a compile failure from the outside — only visible in `--log-failed` output; fix = rerun the job (`gh run rerun`)

## Details

### Working Command

```bash
gh run view <run-id> --log-failed
```

Pipe through `grep` to filter for Kotlin errors:

```bash
gh run view <run-id> --log-failed | grep "^e: "
```

Kotlin compiler errors are prefixed with `e: `. This filters out Gradle progress output and shows only compile errors with file:line:col coordinates.

### Why Other Methods Fail

During the `engine-singbox` implementation (2026-05-24), over 15 consecutive CI runs failed. Attempts to read errors through:
- `gh api .../check-runs` → `output.text: null`
- `gh api .../check-runs/.../annotations` → `[]` empty array
- `gh api .../actions/runs/.../jobs` + step `output` → no stdout field
- `gh api .../actions/artifacts` → 401 or zip requiring local extraction

All returned no useful error information. `gh run view --log-failed` was the first method that revealed actual Kotlin error lines.

### Disk Space False Failures

One CI run failed with `No space left on device` — a runner infrastructure error unrelated to code. This looks identical to a compile failure from the API perspective (job `conclusion: failure`). The `--log-failed` output contains the disk error string. Fix: `gh run rerun <run-id>` to get a fresh runner.

### Iteration Cost

Without `--log-failed`, the debugging cycle becomes: guess the error → push fix → wait for CI → repeat. Each round trip takes 3-5 minutes. With `--log-failed`, one command reveals all errors in the failed run, enabling batch fixing before the next push.

## Related Concepts

- [[concepts/ci-workflow-discipline]] - CI discipline rules including read ALL errors before fixing
- [[concepts/kotlin-expression-body-return-trap]] - Type of error found via this method
- [[concepts/gradle-continue-full-failures]] - `--continue` flag ensures all modules checked before `--log-failed` reading
- [[concepts/ci-job-dependency-masking]] - Job-level failure masking distinct from log-reading issue

## Sources

- [[daily/2026-05-24.md]] — Session 19:13: 15+ CI runs failed for engine-singbox; checks/annotations API returned nothing; `gh run view --log-failed | grep "e: "` revealed Kotlin compile errors with file:line; one run had disk exhaustion discovered via same method
