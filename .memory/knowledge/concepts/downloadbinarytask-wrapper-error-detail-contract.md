---
title: DownloadBinaryTask wrapper must preserve actionable error details
sources:
  - daily/2026-06-04.md
created: 2026-06-04
updated: 2026-06-04
---
# DownloadBinaryTask wrapper must preserve actionable error details

## Key Points
- `DownloadBinaryTask` wrapper errors should preserve cause details that tests and diagnostics rely on.
- Generic messages such as `Download failed` or `Integrity check failed` are insufficient when the cause contains `404`, malformed YAML, or SHA256 evidence.
- Test failures in this area are assertion failures, not flaky infrastructure, when expected detail text disappears.
- Supply-chain checks need diagnostic messages that identify parse, download, and integrity failure classes.

## Details

The 2026-06-04 CI investigation found `buildSrc` failing in `DownloadBinaryTaskTest`. The production wrapper had shifted toward generic top-level messages, while tests expected details from the cause such as malformed YAML, HTTP `404`, or SHA256 mismatch. The root cause was loss of actionable diagnostic detail, not a network or coverage problem.

For supply-chain tasks, wrapper messages are part of the operational contract. They allow CI logs to distinguish lock-file parse failures, download failures, and integrity failures without guessing. This relates to [[concepts/ci-artifact-report-driven-debugging]] and [[concepts/ci-coverage-gate-artifact-trust-contract]] because reliable CI diagnosis depends on precise artifacts and messages.

## Related Concepts
- [[concepts/ci-artifact-report-driven-debugging]]
- [[concepts/ci-coverage-gate-artifact-trust-contract]]
- [[concepts/buildsrc-lockfileparser-date-branch-coverage]]

## Sources
- [[daily/2026-06-04.md]] recorded that `DownloadBinaryTaskTest` failed because wrapper messages hid cause details such as malformed YAML, `404`, and SHA256 mismatch.
- [[daily/2026-06-04.md]] recorded the decision to restore expected detail messages instead of treating the failure as flaky CI.
