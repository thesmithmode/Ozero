---
title: CI Snapshot Artifact Failure Grounding
sources:
  - daily/2026-05-31.md
created: 2026-06-01
updated: 2026-06-01
---
# CI Snapshot Artifact Failure Grounding

## Key Points
- When GitHub API or `gh` logs are unavailable, CI diagnosis should pivot to available snapshot artifacts instead of looping on broad hypotheses.
- Snapshot artifacts can identify the failing module and test class more reliably than memory-based risk lists.
- Merge commits without code diff should not be blamed before checking parent content and failing artifacts.
- Confirmed failure sources should drive minimal fixes; suspected zones remain review items until evidence appears.

## Details

During the 2026-05-31 CI recovery work, access to exact GitHub logs was blocked or unreliable. The investigation initially circled through known risk zones such as sing-box extra modules, `shared-warp-settings`, `engine-byedpi`, and app test compile. The useful turn came from snapshot artifacts, which localized a concrete failure to `engine-masterdns`, with seven failures in `MasterDnsDeployerTest`.

This establishes a practical hierarchy for CI-only validation: artifacts and workflow logs first, then module contracts, then code changes. It complements [[concepts/ci-artifact-report-driven-debugging]] and [[concepts/ci-grouped-job-failure-attribution]] by adding a guard against repeated low-value searches when direct CI APIs are unavailable.

## Related Concepts
- [[concepts/ci-artifact-report-driven-debugging]]
- [[concepts/ci-grouped-job-failure-attribution]]
- [[concepts/github-actions-run-level-polling]]
- [[concepts/masterdns-docker-build-run-proof-contract]]

## Sources
- [[daily/2026-05-31]]: The agent noted an overlong diagnostic loop caused by unavailable GitHub run detail and repeated broad searches.
- [[daily/2026-05-31]]: Snapshot artifacts localized the actual failure to `engine-masterdns` and `MasterDnsDeployerTest`, overriding earlier broad suspicions.
