---
title: Layered PR merge CI feedback loop
sources:
  - daily/2026-05-30.md
created: 2026-05-30
updated: 2026-05-30
---
# Layered PR merge CI feedback loop
## Summary
Layered conflict resolution and CI diagnosis are coupled: preserving each PR's unique contribution requires comparing against fresh `dev`, then validating with full target-branch CI because PR checks may skip or group the failing tests.
## Key Points
- Conflict resolution decides what code reaches `dev`; CI feedback decides whether that layered result is actually valid.
- A later PR can carry stale behavior even when it also contains a useful assertion or test.
- PR checks can be incomplete, so a green PR check set is not equivalent to green target-branch CI.
- Grouped jobs can make failures look like unstarted tests until their internal module results are inspected.
## Details
The MasterDNS PR sequence and PR #78/#79 CI investigation revealed one combined workflow rule. During merge, each PR must be reduced to its unique contribution relative to current `origin/dev`; otherwise a branch can accidentally overwrite already accepted behavior. This is how #74 kept typed `PortBusy` diagnostics without restoring older `amnezia-dns` auto-removal, and #75 reduced to a running-container contract assert.

The validation loop must then move to the target branch. In this session, PR jobs on `codex/*` branches could skip important unit tasks, and a perceived "tests did not start" problem was actually a failure inside a grouped extra-modules job. A green `dev` push run also did not prove the later `dev -> main` PR run would be green, because pull-request workflows may differ.

The connection is practical: conflict resolution without full target CI can preserve the wrong layer, while CI diagnosis without diff discipline can blame the wrong PR or module.
## Related Concepts
- [[concepts/overlapping-pr-merge-preserve-dev-contracts]]
- [[concepts/pr-ci-push-vs-pull-request-drift]]
- [[concepts/masterdns-amnezia-dns-running-udp-contract]]
- [[concepts/ci-grouped-job-failure-attribution]]
## Sources
- [[daily/2026-05-30]]: #73/#74/#75 had to be merged without losing previously accepted safety and diagnostics contracts.
- [[daily/2026-05-30]]: PR branch checks could skip useful unit tests, so the full `dev` CI was treated as the stronger signal.
- [[daily/2026-05-30]]: PR #78's "not started tests" symptom was reclassified as a failing grouped job after run/job inspection.
- [[daily/2026-05-30]]: PR #79 showed that green `dev` push CI does not guarantee green pull-request CI.
