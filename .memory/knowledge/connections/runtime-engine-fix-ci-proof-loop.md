---
title: Runtime engine fixes and CI proof loop
sources:
  - daily/2026-05-30.md
created: 2026-05-31
updated: 2026-05-31
---
# Runtime engine fixes and CI proof loop

## Key Points
- Runtime engine symptoms must be diagnosed by separate engine signatures before integration.
- Green CI is necessary but insufficient unless changed modules have nonzero, relevant tests.
- Subagent results require orchestrator review, staged integration, and contract checks.
- Release progression should wait for dev CI, self-review, and then main/release CI.
- This links [[concepts/dev-ci-workflow-dispatch-nonzero-tests-contract]] and [[concepts/release-runtime-scenario-checklist]].

## Details

The late 2026-05-30 work split several runtime regressions into independent tracks: FPTN auth/health-preselect, MasterDNS Docker run failure, sing-box lifecycle/traffic failure, ByeDPI YouTube media path, and WARP/orchestration read-only analysis. That split prevented one generic fix from being applied to unrelated signatures.

The integration rule was also explicit. Subagents could work in non-overlapping scopes, but their findings had to be pulled back one by one, reviewed personally, staged, and validated against the owning contract. A CI agent before integration was rejected as premature because it would test the wrong state.

The final proof loop requires more than a green badge. After an initial green CI, artifact audit found a zero-test module, which forced a sentinel and another CI run. This connects runtime proof, module-level test evidence, and release discipline: dev CI green, code-review/self-review, then main merge and release CI only after the relevant behaviors are actually covered.

## Related Concepts
- [[concepts/dev-ci-workflow-dispatch-nonzero-tests-contract]]
- [[concepts/release-runtime-scenario-checklist]]
- [[connections/release-ci-green-vs-runtime-engine-proof]]
- [[connections/cascade-lifecycle-regressions-cross-engine-proof]]

## Sources
- [[daily/2026-05-30]]: Runtime issues were separated into FPTN, MasterDNS, sing-box, ByeDPI, and WARP/orchestration tracks.
- [[daily/2026-05-30]]: The CI/testing subagent was stopped as premature until fixes were integrated.
- [[daily/2026-05-30]]: Subagent outputs were integrated one by one with personal diff and contract review.
- [[daily/2026-05-30]]: A first green dev CI was followed by artifact audit, zero-test detection, sentinel creation, and a required rerun.
