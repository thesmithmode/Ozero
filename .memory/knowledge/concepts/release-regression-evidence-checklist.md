---
title: Release regression evidence checklist
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---

# Release regression evidence checklist

## Key Points
- Release-regression work needs an explicit checklist mapped to the original user prompt before CI is treated as meaningful.
- Each engine claim should have evidence from code, logs, tests, or reference-history comparison.
- Green CI is not enough when fixes include timeouts or other changes that can mask a wrong readiness criterion.
- Reports should separate confirmed fixes, runtime risks, and unproven assumptions.

## Details

The 2026-05-28 release-regression cycle showed that a first CI run can be premature even when the code compiles. The user asked whether all original points were closed before CI, and the answer was no: URnetwork and sing-box still needed stronger evidence. The corrected practice is to build a checklist from the original prompt and attach evidence to each item before promoting the change.

The checklist is especially important for engine regressions because symptoms can cross module boundaries. ByeDPI stop behavior affected WARP startup, URnetwork looked like a no-peer situation while hiding readiness detection gaps, and sing-box had multiple paths that could generate invalid configs. This concept complements [[concepts/ci-workflow-discipline]]: CI remains required, but it is a secondary signal unless the checklist proves the tested code paths match the user-visible regressions.

## Related Concepts
- [[concepts/ci-workflow-discipline]]
- [[concepts/code-review-before-ci-monitor]]
- [[concepts/urnetwork-readiness-connectionstatus]]
- [[concepts/singbox-autochain-validator-parity]]
- [[connections/release-regression-ci-vs-runtime-proof]]

## Sources
- [[daily/2026-05-28.md]] records that the first CI was acknowledged as premature because not every original prompt item had evidence.
- [[daily/2026-05-28.md]] records the decision to report only problems with code-line or contract-level proof during broad review.
- [[daily/2026-05-28.md]] records the release completion criterion as successful release build, not only green dev CI.
