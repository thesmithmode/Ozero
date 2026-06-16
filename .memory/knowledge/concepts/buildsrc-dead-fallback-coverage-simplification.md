---
title: buildSrc dead fallback coverage simplification
sources:
  - daily/2026-06-04.md
created: 2026-06-09
updated: 2026-06-09
---
# buildSrc dead fallback coverage simplification

## Summary
`buildSrc` coverage gaps should be closed by testing real parser/task branches or deleting unreachable fallback branches, not by manufacturing assertions around dead code.

## Key Points
- `LockFileParser` coverage required the real SnakeYAML `Date` branch, not another string-shaped test.
- `DownloadBinaryTask` wrapper failures needed cause details for malformed YAML, 404, and SHA256 mismatch.
- Null-message fallbacks such as `e.message ?: ...` can be dead branches when all relevant exceptions already carry messages.
- Dead fallback branches should be simplified away when they do not express a real contract.

## Details
The 2026-06-04 CI cycle exposed `buildSrc` as both a test/assertion problem and a coverage problem. `LockFileParser` did not merely need another happy-path lock-file test; the missing branch was the runtime path where SnakeYAML parses `generated_at` as a `Date`. That made [[concepts/buildsrc-lockfileparser-date-branch-coverage]] the owning concept for the parser side.

The same day later identified wrapper-message and fallback-message issues. `DownloadBinaryTask` tests expected details from the underlying cause, while the wrapper could collapse them into generic messages. At the coverage stage, dead fallback branches around `e.message ?: ...` were better removed than covered artificially. This keeps [[concepts/downloadbinarytask-wrapper-error-detail-contract]] intact while avoiding fake coverage on unreachable branches.

## Related Concepts
- [[concepts/buildsrc-lockfileparser-date-branch-coverage]]
- [[concepts/downloadbinarytask-wrapper-error-detail-contract]]
- [[concepts/coverage-gap-targeted-branch-remediation]]
- [[concepts/jacoco-historical-debt-per-module-baseline-boundary]]

## Sources
- [[daily/2026-06-04]]: sessions 11:29, 11:34 and 12:28 identify `generated_at` as a SnakeYAML timestamp/`Date` branch, not a simple string case.
- [[daily/2026-06-04]]: sessions 20:39 and 23:20 record `DownloadBinaryTask` message-detail assertions and dead `e.message ?: ...` fallback branches.
