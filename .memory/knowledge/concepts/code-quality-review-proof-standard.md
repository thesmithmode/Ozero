---
title: Code quality review proof standard
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---

# Code quality review proof standard

## Key Points
- Broad code-quality review should report only issues that are fully evidenced by code, logs, artifacts, or explicit project contracts.
- Speculative risks can guide investigation, but they should not be reported as findings without proof.
- Findings need concrete anchors such as file paths, line evidence, run IDs, CI artifacts, or contract invariants.
- This standard is especially important after large release diffs and engine-regression fixes, where plausible hypotheses can be numerous.

## Details

On 2026-05-28 the user asked for a wide code-quality review after release-regression fixes, but required that only 100% provable problems be reported. The daily log ties this to the larger `v0.2.11` to `v1.0.3` audit: the assistant was allowed to investigate broadly, while the output had to separate confirmed regressions from unproven risks [[daily/2026-05-28.md]].

The same standard was applied to the engine work. URnetwork readiness and sing-box auto-chain validation were reported because they had code-level evidence and violated known invariants. In contrast, remaining runtime uncertainty for live URnetwork peers, ByeDPI drain behavior, and sing-box traffic was described as residual risk rather than proof of a still-broken fix [[daily/2026-05-28.md]].

This proof standard complements CI validation. A green CI run can prove that specific configured jobs passed, but it does not prove live-device engine behavior unless the relevant scenario is actually represented in the gate [[daily/2026-05-28.md]].

## Related Concepts
- [[concepts/release-regression-evidence-checklist]]
- [[concepts/release-runtime-scenario-checklist]]
- [[connections/release-regression-ci-vs-runtime-proof]]
- [[concepts/test-retention-evidence-standard]]

## Sources
- [[daily/2026-05-28.md]]: records the instruction to perform broad review but report only fully proven problems.
- [[daily/2026-05-28.md]]: records the distinction between confirmed URnetwork/sing-box regressions and residual runtime uncertainty after green CI.
