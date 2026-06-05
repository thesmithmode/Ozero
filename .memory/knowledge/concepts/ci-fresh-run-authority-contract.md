---
title: Fresh-run authority for CI verification
sources:
  - daily/2026-06-04.md
created: 2026-06-05
updated: 2026-06-05
---

## Summary
Dev CI can be trusted only by a fresh, terminal-terminated run on current push. Historical green runs are explicitly treated as stale evidence and must not guide completion.

## Key Points
- A red-or-green signal must be taken from the latest run tied to the current `dev` SHA.
- Terminal `success` is the acceptance boundary for whole-pipeline health, not per-job status snapshots.
- Each push requires re-baselining the failing job set from that run before declaring remediation progress.
- If a run toolchain (GH API/CLI/connectors) is blocked, investigators can still use artifact URLs and step summaries tied to the current run.
- A new green run can expose a different failure root because earlier blockers can be fixed and next-layer blockers become visible.

## Details
In the 2026-06-04 worklog, multiple attempts showed the same failure pattern: earlier successful runs were repeatedly revisited and treated as current proof before a new `dev` commit even after fixes. The process was corrected by grounding conclusions strictly on fresh run IDs and their explicit failing jobs. This reduced false conclusions and avoided stopping at non-authoritative signals.

The same log also shows that multiple job classes (`buildSrc`, `common-vpn`, `singbox`, `engine-warp`, shared modules) can fail in one cycle, and completion requires iterating by freshest evidence. “CI green” was therefore modeled as an explicit terminal state of the next run, not a partial hypothesis closure.

## Related Concepts
- [[concepts/dev-ci-root-cause-sequencing-loop]]
- [[concepts/ci-current-run-batch-failure-triage]]
- [[concepts/ci-terminal-success-fresh-run-contract]]
- [[connections/dev-ci-first-failure-sequencing-loop]]

## Sources
- Daily log evidence in [[daily/2026-06-04.md]] on stale status checks, run IDs, and terminal status requirement.
- 11:29, 12:28, 15:11, 15:26, 18:54, 20:39, 21:17 sessions describe multiple fresh-run analyses.
