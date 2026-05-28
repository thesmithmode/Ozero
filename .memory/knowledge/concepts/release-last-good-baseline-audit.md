---
title: Release last-good baseline audit
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---

# Release last-good baseline audit

## Key Points
- A user-confirmed working release can be used as the behavioral baseline for regression audit.
- The audit must verify actual tags or SHAs before comparing releases.
- Read-only audit findings must be separated from follow-up fixes.
- Large release diffs require P0/P1 prioritization with concrete file and behavior evidence.

## Details

On 2026-05-28, release `v0.2.11` was treated as the last-good baseline because the user confirmed that ByeDPI, FPTN, and URnetwork worked better there. Later audit work compared `v0.2.11` to the latest release target and reported a large diff, then separated confirmed P1 regressions from lower-confidence risks.

This pattern extends [[concepts/release-audit-tag-sha-grounding]]. The audit must first establish which tag or SHA represents the latest release, especially when a local tag is missing. Only after that grounding should findings feed [[concepts/release-regression-evidence-checklist]] or code changes. The same distinction matters for [[connections/release-status-vs-asset-verification]], where workflow success and published artifacts can diverge.

## Related Concepts
- [[concepts/release-audit-tag-sha-grounding]]
- [[concepts/release-regression-evidence-checklist]]
- [[concepts/release-regression-self-review-gate]]
- [[connections/release-status-vs-asset-verification]]

## Sources
- [[daily/2026-05-28]]: пользователь предложил сравнить ByeDPI с релизом `0.2.11`, где он стабильно работал.
- [[daily/2026-05-28]]: аудит `v0.2.11 -> v1.0.3` выполнялся без правок и с явным указанием регрессионных рисков.
- [[daily/2026-05-28]]: при отсутствии локального тега `v1.0.3` было зафиксировано требование подтянуть теги и уточнить фактический release SHA.
