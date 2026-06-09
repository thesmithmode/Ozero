---
title: Subagent integration write-scope discipline
sources:
  - daily/2026-05-30.md
created: 2026-06-09
updated: 2026-06-09
---
# Subagent integration write-scope discipline
## Summary
Parallel subagents are useful for independent engine investigations only when their write scopes are separated, CI validation waits for integrated code, and the orchestrator personally verifies every merged diff.
## Key Points
- Subagents should be assigned non-overlapping write scopes when runtime regressions split across engines.
- A CI/testing agent is premature before fixes are integrated because it validates the wrong state.
- Read-only investigation can run beside write-scoped implementation when shared contracts might be affected.
- The orchestrator must integrate results one at a time and re-check diffs, staging, and cross-module contracts.
- This discipline complements [[concepts/subagent-code-review-false-positives]] and [[connections/runtime-engine-fix-ci-proof-loop]].
## Details
On 2026-05-30 the work split runtime regressions across FPTN, MasterDNS, sing-box, ByeDPI, and WARP/orchestration. The user challenged an early CI/testing subagent as premature, because no integrated state existed yet. The testing agent was stopped, and the remaining subagents were kept on separated domains.

The durable practice is to use parallelism for independent investigation, not for uncoordinated application. Even when files do not overlap directly, compile contracts and shared runtime behavior can still conflict. The orchestrator owns final integration: receive findings, verify them against code and logs, apply one coherent change at a time, and then run the real gate.

This is especially important in Ozero because engine fixes often touch shared concepts such as lifecycle, exit-node proof, and CI coverage. A subagent result is evidence, not an automatically trusted patch.
## Related Concepts
- [[concepts/subagent-code-review-false-positives]]
- [[connections/runtime-engine-fix-ci-proof-loop]]
- [[concepts/code-quality-review-proof-standard]]
- [[concepts/real-path-grounding-before-fix-plan]]
## Sources
- [[daily/2026-05-30]]: Subagents were split across FPTN, MasterDNS, sing-box, ByeDPI, and WARP/orchestration.
- [[daily/2026-05-30]]: The CI/testing agent was stopped because validation before integration would test the wrong state.
- [[daily/2026-05-30]]: The integration rule was to merge results one by one with personal diff and contract verification.
